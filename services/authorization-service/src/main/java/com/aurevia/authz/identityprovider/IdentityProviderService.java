package com.aurevia.authz.identityprovider;

import static com.aurevia.authz.identityprovider.IdentityProviderModels.*;
import com.aurevia.authz.observability.AuditTrail;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class IdentityProviderService {
  private static final Pattern CODE=Pattern.compile("^[a-z][a-z0-9-]{2,79}$");
  private static final Pattern CLAIM=Pattern.compile("^[A-Za-z][A-Za-z0-9_.:-]{0,159}$");
  private static final Pattern SECRET=Pattern.compile("^secret://[A-Za-z0-9._/-]+$");
  private static final Set<String> TYPES=Set.of("OIDC","KEYCLOAK","AZURE_AD","OKTA","AUTH0","GOOGLE_WORKSPACE");
  private final IdentityProviderRepository providers;
  private final IdentityProviderUriPolicy uris;
  private final IdentityProviderHealthProbe health;
  private final AuditTrail audit;
  public IdentityProviderService(IdentityProviderRepository providers,IdentityProviderUriPolicy uris,
      IdentityProviderHealthProbe health,AuditTrail audit){
    this.providers=providers;this.uris=uris;this.health=health;this.audit=audit;}

  public List<ProviderView> list(){return providers.findAll();}
  public List<ProviderSummary> available(String tenant,String domain){return providers.findEnabled()
      .stream().filter(value->matches(value,tenant,domain)).map(IdentityProviderService::summary).toList();}
  public ProviderSummary route(String code,String tenant,String domain){
    List<ProviderView> matches=providers.findEnabled().stream().filter(value->
        (blank(code)||value.code().equals(normalizeCode(code)))&&matches(value,tenant,domain)).toList();
    if(matches.isEmpty())throw new ResponseStatusException(HttpStatus.NOT_FOUND,"No matching identity provider");
    if(matches.size()>1)throw new ResponseStatusException(HttpStatus.CONFLICT,"Identity provider selection required");
    return summary(matches.getFirst());
  }
  public RuntimeProvider runtime(String code){ProviderView value=providers.findEnabledByCode(normalizeCode(code))
      .orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Identity provider unavailable"));
    return new RuntimeProvider(value.code(),value.name(),value.issuerUrl(),value.authorizationEndpoint(),
        value.tokenEndpoint(),value.jwksUri(),value.userInfoEndpoint(),value.clientId(),
        value.clientSecretReference(),value.scopes(),value.audiences(),value.subjectClaim(),
        value.usernameClaim(),value.groupsClaim());}

  @Transactional public MutationResult create(ProviderCommand raw,String actor){
    ProviderCommand command=validate(raw);UUID id=UUID.randomUUID();String safeActor=actor(actor);
    providers.insert(id,command,safeActor);event(safeActor,"identity_provider.created",id,command.code(),
        "CREATE",null,Map.of("issuer",command.issuerUrl(),"tenant",safe(command.tenantId())));
    return new MutationResult(id,0);
  }
  @Transactional public MutationResult update(UUID id,long version,ProviderCommand raw,String actor){
    ProviderSnapshot before=providers.snapshot(id).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND));
    ProviderCommand command=validate(raw);
    if(!before.code().equals(command.code()))throw new IllegalArgumentException("Identity provider code is immutable");
    if(providers.update(id,version,command,actor(actor))!=1)throw new OptimisticLockingFailureException("VERSION_CONFLICT");
    Map<String,Object> prior=Map.of("issuer",before.issuerUrl(),"clientId",before.clientId(),
        "tenant",safe(before.tenantId()),"enabled",before.enabled());
    Map<String,Object> after=Map.of("issuer",command.issuerUrl(),"clientId",command.clientId(),
        "tenant",safe(command.tenantId()),"enabled",command.enabled());
    event(actor,"identity_provider.updated",id,command.code(),"UPDATE",prior,after);
    if(!before.issuerUrl().equals(command.issuerUrl()))event(actor,"identity_provider.issuer_changed",id,command.code(),"UPDATE",prior,after);
    if(!before.clientId().equals(command.clientId()))event(actor,"identity_provider.client_changed",id,command.code(),"UPDATE",prior,after);
    if(!java.util.Objects.equals(before.tenantId(),command.tenantId()))event(actor,"identity_provider.tenant_mapping_changed",id,command.code(),"UPDATE",prior,after);
    return new MutationResult(id,version+1);
  }
  @Transactional public MutationResult enabled(UUID id,long version,boolean enabled,String actor){
    ProviderSnapshot value=providers.snapshot(id).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND));
    if(providers.updateEnabled(id,version,enabled,actor(actor))!=1)throw new OptimisticLockingFailureException("VERSION_CONFLICT");
    event(actor,enabled?"identity_provider.enabled":"identity_provider.disabled",id,value.code(),
        enabled?"ENABLE":"DISABLE",Map.of("enabled",value.enabled()),Map.of("enabled",enabled));
    return new MutationResult(id,version+1);
  }
  @Transactional public HealthResult health(UUID id,String actor){
    ProviderView value=providers.findAll().stream().filter(item->item.id().equals(id)).findFirst()
        .orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND));
    Instant checked=Instant.now();String status="ACTIVE";String error=null;
    try{health.verify(value.jwksUri());}catch(RuntimeException failure){status="UNREACHABLE";error="JWKS health check failed";}
    providers.updateHealth(id,status,checked,error);
    event(actor,"identity_provider.health_checked",id,value.code(),"HEALTH_CHECK",null,Map.of("status",status));
    return new HealthResult(status,checked);
  }

  private ProviderCommand validate(ProviderCommand raw){
    String code=normalizeCode(raw.code());String type=upper(raw.type());
    if(!CODE.matcher(code).matches())throw new IllegalArgumentException("Invalid identity provider code");
    if(!TYPES.contains(type))throw new IllegalArgumentException("Unsupported identity provider type");
    if(blank(raw.name())||blank(raw.clientId())||!SECRET.matcher(text(raw.clientSecretReference())).matches())
      throw new IllegalArgumentException("Name, clientId and a secret:// reference are required");
    String subject=claim(raw.subjectClaim(),"sub"),username=claim(raw.usernameClaim(),"preferred_username"),
        groups=claim(raw.groupsClaim(),"groups");
    List<String> scopes=normalize(raw.scopes());if(scopes.isEmpty())scopes=List.of("openid","profile","email");
    if(!scopes.contains("openid"))throw new IllegalArgumentException("OIDC scope must include openid");
    List<String> domains=normalize(raw.domains()).stream().map(value->value.toLowerCase(Locale.ROOT)).toList();
    for(String domain:domains)if(!domain.matches("^[a-z0-9](?:[a-z0-9.-]{0,251}[a-z0-9])?$"))
      throw new IllegalArgumentException("Invalid routing domain");
    return new ProviderCommand(code,raw.name().trim(),type,uris.validate(raw.issuerUrl(),"issuer_url"),
        uris.validate(raw.authorizationEndpoint(),"authorization_endpoint"),
        uris.validate(raw.tokenEndpoint(),"token_endpoint"),uris.validate(raw.jwksUri(),"jwks_uri"),
        blank(raw.userInfoEndpoint())?null:uris.validate(raw.userInfoEndpoint(),"user_info_endpoint"),
        raw.clientId().trim(),raw.clientSecretReference().trim(),raw.enabled(),nullable(raw.tenantId()),
        domains,scopes,normalize(raw.audiences()),subject,username,groups);
  }
  private static boolean matches(ProviderView value,String tenant,String domain){
    if(!blank(tenant)&&!tenant.equalsIgnoreCase(value.tenantId()))return false;
    if(blank(domain))return true;String normalized=domain.trim().toLowerCase(Locale.ROOT);
    int at=normalized.lastIndexOf('@');String domainValue=at>=0?normalized.substring(at+1):normalized;
    return value.domains().stream().anyMatch(item->item.equalsIgnoreCase(domainValue));
  }
  private void event(String actor,String event,UUID id,String name,String action,Map<String,Object> before,Map<String,Object> after){
    audit.success("IDENTITY_PROVIDER",event,null,null,"IDENTITY_PROVIDER",id.toString(),name,action,before,after);}
  private static ProviderSummary summary(ProviderView value){return new ProviderSummary(value.code(),value.name(),
      value.type(),value.issuerUrl(),value.tenantId(),value.domains(),value.connectionStatus());}
  private static String claim(String value,String fallback){String result=blank(value)?fallback:value.trim();
    if(!CLAIM.matcher(result).matches())throw new IllegalArgumentException("Invalid claim name");return result;}
  private static List<String> normalize(List<String> values){if(values==null)return List.of();return List.copyOf(
      new LinkedHashSet<>(values.stream().filter(value->!blank(value)).map(String::trim).toList()));}
  private static String normalizeCode(String value){return text(value).toLowerCase(Locale.ROOT);}
  private static String upper(String value){return text(value).toUpperCase(Locale.ROOT);}
  private static String nullable(String value){return blank(value)?null:value.trim();}
  private static String actor(String value){if(blank(value)||value.length()>500)throw new IllegalArgumentException("Invalid actor");return value;}
  private static String safe(String value){return value==null?"":value;}
  private static String text(String value){return value==null?"":value.trim();}
  private static boolean blank(String value){return value==null||value.isBlank();}
}

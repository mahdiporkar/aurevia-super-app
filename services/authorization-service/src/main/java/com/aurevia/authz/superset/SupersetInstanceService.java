package com.aurevia.authz.superset;

import static com.aurevia.authz.api.dto.SupersetInstanceDtos.*;
import static com.aurevia.artifacts.security.UiArtifactUriPolicy.ArtifactType.EXTERNAL_ORIGIN;

import com.aurevia.artifacts.security.UiArtifactUriPolicy;
import com.aurevia.authz.identity.SubjectKey;
import com.aurevia.authz.observability.AuditTrail;
import com.aurevia.authz.openfga.RelationshipAuthorizationPort;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class SupersetInstanceService {
  private static final Pattern CODE=Pattern.compile("^[a-z][a-z0-9-]{2,79}$");
  private static final Pattern CONNECTION=Pattern.compile("^connection://[a-zA-Z0-9._/-]+$");
  private static final Set<String> AUTH_MODES=Set.of("REMOTE_USER","OIDC","GUEST_TOKEN");
  private final SupersetInstanceRepository instances;
  private final SupersetAssetRepository assets;
  private final RelationshipAuthorizationPort relationships;
  private final AuditTrail audit;
  private final ObjectMapper json;
  private final UiArtifactUriPolicy targetPolicy;

  public SupersetInstanceService(SupersetInstanceRepository instances,SupersetAssetRepository assets,
      RelationshipAuthorizationPort relationships,AuditTrail audit,ObjectMapper json,
      @Value("${aurevia.superset.network-policy:DEVELOPMENT}") String networkPolicy,
      @Value("${aurevia.superset.allow-http:true}") boolean allowHttp,
      @Value("${aurevia.superset.development-host:}") String developmentHost,
      @Value("${aurevia.superset.allowed-private-cidrs:}") String allowedPrivateCidrs) {
    this.instances=instances;this.assets=assets;this.relationships=relationships;
    this.audit=audit;this.json=json;
    this.targetPolicy=new UiArtifactUriPolicy(networkPolicy,allowHttp,developmentHost,
        allowedPrivateCidrs);
  }

  public List<InstanceView> instances() { return instances.instances(); }
  public List<MappingView> mappings() { return instances.mappings(); }
  public List<IntegrationView> integrationsForSubject(String issuer,String subject) {
    String user=new SubjectKey(issuer,subject).openFgaUser();
    boolean administrator=relationships.check(user,"can_manage","application:aurevia");
    Map<String,String> mappedTargets=instances.mappings().stream().filter(MappingView::active)
        .collect(java.util.stream.Collectors.toMap(MappingView::publicCode,
            MappingView::operationCode,(first,ignored)->first));
    return instances.activeIntegrations().stream().filter(integration->administrator
        ||relationships.check(user,"can_view","application:"+integration.key())
        ||assets.publishedAssets(mappedTargets.getOrDefault(integration.key(),integration.key()))
            .stream().anyMatch(asset->canView(user,asset))).toList();
  }

  public boolean canAccess(String issuer,String subject,String integrationCode,String targetCode) {
    String user=new SubjectKey(issuer,subject).openFgaUser();
    return relationships.check(user,"can_manage","application:aurevia")
        ||relationships.check(user,"can_view","application:"+integrationCode)
        ||assets.publishedAssets(targetCode).stream().anyMatch(asset->canView(user,asset));
  }

  @Transactional
  public VersionResponse create(InstanceRequest request,String actor) {
    String safeActor=actor(actor);
    var value=validate(request,UUID.randomUUID());
    instances.insert(value,safeActor);
    instances.ensureApplicationResource(value);
    audit.success("SUPERSET","superset.instance.created",null,null,"SUPERSET_INSTANCE",
        value.id().toString(),value.code(),"CREATE",null,Map.of("zone",value.zone()));
    return new VersionResponse(value.id(),0);
  }

  @Transactional
  public VersionResponse update(UUID id,InstanceRequest request,String actor) {
    String safeActor=actor(actor);
    var value=validate(request,id);
    if(!instances.update(id,request.version(),value,request.active(),safeActor)) {
      throw new OptimisticLockingFailureException("VERSION_CONFLICT");
    }
    instances.ensureApplicationResource(value);
    audit.success("SUPERSET","superset.instance.updated",null,null,"SUPERSET_INSTANCE",
        id.toString(),value.code(),"UPDATE",null,Map.of("zone",value.zone()));
    return new VersionResponse(id,request.version()+1);
  }

  @Transactional
  public IdResponse map(MappingRequest request,String actor) {
    String safeActor=actor(actor);
    assertZones(request.publicInstanceId(),request.operationInstanceId());
    String path=validatePath(request.publicPath());
    if(request.isDefault()) instances.clearDefaultMappings(safeActor);
    UUID id=instances.upsertMapping(UUID.randomUUID(),request.publicInstanceId(),
        request.operationInstanceId(),path,request.isDefault(),request.active(),safeActor);
    audit.success("SUPERSET","superset.mapping.changed",null,null,"SUPERSET_MAPPING",
        id.toString(),path,"UPSERT",null,Map.of("default",request.isDefault()));
    return new IdResponse(id);
  }

  @Transactional
  public void updateHealth(String code,String status) {
    String normalized=status==null?"":status.trim().toUpperCase(Locale.ROOT);
    if(!Set.of("ACTIVE","UNREACHABLE").contains(normalized)) {
      throw new IllegalArgumentException("Invalid Superset health status");
    }
    instances.updateHealth(code,normalized);
  }

  private void assertZones(UUID publicId,UUID operationId) {
    List<String> zones=instances.activeZones(publicId,operationId);
    if(zones.size()!=2||!zones.contains("PUBLIC")||!zones.contains("OPERATION")) {
      throw new IllegalArgumentException(
          "Mapping requires one active PUBLIC and one active OPERATION instance");
    }
  }

  private SupersetInstanceRepository.InstanceValue validate(InstanceRequest request,
      UUID id) {
    String code=request.code().trim().toLowerCase(Locale.ROOT);
    String zone=request.zone().trim().toUpperCase(Locale.ROOT);
    String auth=request.authMode().trim().toUpperCase(Locale.ROOT);
    if(!CODE.matcher(code).matches()) throw new IllegalArgumentException("Invalid Superset instance code");
    if(!Set.of("PUBLIC","OPERATION").contains(zone)) throw new IllegalArgumentException("Invalid Superset zone");
    if(!AUTH_MODES.contains(auth)) throw new IllegalArgumentException("Invalid Superset auth mode");
    String connection=request.connectionRef()==null||request.connectionRef().isBlank()
        ?"connection://superset/"+code:request.connectionRef().trim();
    if(!CONNECTION.matcher(connection).matches()) throw new IllegalArgumentException("Invalid connection reference");
    URI uri;
    try { uri=targetPolicy.validateConfigured(request.baseUrl(),EXTERNAL_ORIGIN,"Superset base URL"); }
    catch(RuntimeException invalid) { throw new IllegalArgumentException("Invalid Superset base URL",invalid); }
    if(request.tlsRequired()&&!"https".equals(uri.getScheme())) {
      throw new IllegalArgumentException("TLS-required Superset must use HTTPS");
    }
    String normalized=uri.toString();
    if(normalized.endsWith("/")&&uri.getPath().length()>1) {
      normalized=normalized.substring(0,normalized.length()-1);
    }
    String metadata;
    try {
      metadata=json.writeValueAsString(request.metadata()==null?Map.of():request.metadata());
    } catch(Exception invalid) {
      throw new IllegalArgumentException("Invalid Superset metadata",invalid);
    }
    if(metadata.length()>32768) throw new IllegalArgumentException("Superset metadata is too large");
    return new SupersetInstanceRepository.InstanceValue(id,code,request.name().trim(),zone,
        normalized,connection,auth,request.tlsRequired(),request.active(),
        request.proxyMode()==null||request.proxyMode(),metadata);
  }

  private boolean canView(String user,SupersetAssetModels.AssetView asset) {
    String object="external_resource:"
        +asset.resourceKey().replaceFirst("^external_resource:","").replace(':','/');
    return relationships.check(user,"can_view",object);
  }

  private static String validatePath(String value) {
    String path=value==null?"":value.trim();
    if(!path.matches("^/[a-zA-Z0-9/_-]*$")||path.contains("..")) {
      throw new IllegalArgumentException("Invalid public proxy path");
    }
    return path.length()>1&&path.endsWith("/")?path.substring(0,path.length()-1):path;
  }

  private static String actor(String value) {
    if(value==null||value.isBlank()||value.length()>500) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Invalid actor");
    }
    return value;
  }
}

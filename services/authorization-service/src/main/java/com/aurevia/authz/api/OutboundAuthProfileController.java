package com.aurevia.authz.api;

import com.aurevia.authz.observability.AuditTrail;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

/** Metadata only: this service deliberately has no access to credential values. */
@RestController
public class OutboundAuthProfileController {
  private static final Set<String> MODES=Set.of("FORWARD_USER_TOKEN","LEGACY_SERVICE_TOKEN");
  private static final Set<String> FORMATS=Set.of("FORM_URLENCODED","JSON","HTTP_BASIC","OAUTH_CLIENT_CREDENTIALS","CUSTOM_LEGACY_ADAPTER");
  private static final Set<String> TRANSPORTS=Set.of("USER_AUTHORIZATION_HEADER","INTERNAL_LEGACY_HEADER");
  private final JdbcClient db; private final AuditTrail audit;
  public OutboundAuthProfileController(JdbcClient db,AuditTrail audit){this.db=db;this.audit=audit;}

  @GetMapping("/internal/v1/registry/outbound-auth-profiles")
  public List<Map<String,Object>> list(@RequestParam(defaultValue="") String search){return db.sql("""
    select p.*,(select count(*) from service_target st where st.outbound_auth_profile_id=p.id) usage_count
    from outbound_auth_profile p where (:search='' or lower(p.code||' '||p.name) like lower('%'||:search||'%')) order by p.code
    """).param("search",limit(search,100)).query().listOfRows();}
  @GetMapping("/internal/v1/registry/outbound-auth-profiles/{id}") public Map<String,Object> one(@PathVariable UUID id){return row(id);}
  @GetMapping("/internal/v1/outbound-auth-profiles/{id}") public Map<String,Object> runtime(@PathVariable UUID id){Map<String,Object> p=row(id);if(!Boolean.TRUE.equals(p.get("active")))throw new NotFound();return p;}

  @PostMapping("/internal/v1/registry/outbound-auth-profiles") @ResponseStatus(HttpStatus.CREATED) @Transactional
  public Map<String,Object> create(@Valid @RequestBody Write p,@RequestHeader("X-Actor") String actor){validate(p);UUID id=UUID.randomUUID();db.sql("""
    insert into outbound_auth_profile(id,code,name,description,auth_mode,token_connection_ref,token_endpoint_path,request_format,
    credential_secret_ref,client_id_secret_ref,client_secret_ref,scope,audience,token_response_pointer,expires_in_response_pointer,
    refresh_token_response_pointer,token_type_response_pointer,authorization_scheme,credential_transport,expiry_skew_seconds,
    connect_timeout_ms,response_timeout_ms,max_token_response_size,active,created_by,updated_by)
    values(:id,:code,:name,nullif(:description,''),:mode,nullif(:connection,''),nullif(:endpoint,''),:format,nullif(:secret,''),
    nullif(:clientId,''),nullif(:clientSecret,''),nullif(:scope,''),nullif(:audience,''),:token,:expiry,nullif(:refresh,''),
    :type,:scheme,:transport,:skew,:connect,:response,:size,:active,:actor,:actor)
    """).param("id",id).param("code",code(p.code())).param("name",limit(p.name(),255)).param("description",nullable(p.description()))
    .param("mode",upper(p.authMode())).param("connection",reference(p.tokenConnectionRef(),"connection://"))
    .param("endpoint",endpoint(p.tokenEndpointPath())).param("format",upper(p.requestFormat()))
    .param("secret",reference(p.credentialSecretRef(),"secret://")).param("clientId",reference(p.clientIdSecretRef(),"secret://"))
    .param("clientSecret",reference(p.clientSecretRef(),"secret://")).param("scope",nullable(p.scope())).param("audience",nullable(p.audience()))
    .param("token",pointer(p.tokenResponsePointer())).param("expiry",pointer(p.expiresInResponsePointer()))
    .param("refresh",optionalPointer(p.refreshTokenResponsePointer())).param("type",pointer(p.tokenTypeResponsePointer()))
    .param("scheme",scheme(p.authorizationScheme())).param("transport",upper(p.credentialTransport())).param("skew",p.expirySkewSeconds())
    .param("connect",p.connectTimeoutMs()).param("response",p.responseTimeoutMs()).param("size",p.maxTokenResponseSize())
    .param("active",p.active()).param("actor",limit(actor,255)).update();audit.success("OUTBOUND_AUTH","auth.profile.created",null,null,"OUTBOUND_AUTH_PROFILE",id.toString(),p.code(),"CREATE",null,safe(p));return row(id);}

  @PutMapping("/internal/v1/registry/outbound-auth-profiles/{id}") @Transactional
  public Map<String,Object> update(@PathVariable UUID id,@RequestParam long version,@Valid @RequestBody Write p,@RequestHeader("X-Actor") String actor){validate(p);Map<String,Object> before=row(id);int n=db.sql("""
    update outbound_auth_profile set code=:code,name=:name,description=nullif(:description,''),auth_mode=:mode,
    token_connection_ref=nullif(:connection,''),token_endpoint_path=nullif(:endpoint,''),request_format=:format,
    credential_secret_ref=nullif(:secret,''),client_id_secret_ref=nullif(:clientId,''),client_secret_ref=nullif(:clientSecret,''),
    scope=nullif(:scope,''),audience=nullif(:audience,''),token_response_pointer=:token,expires_in_response_pointer=:expiry,
    refresh_token_response_pointer=nullif(:refresh,''),token_type_response_pointer=:type,authorization_scheme=:scheme,
    credential_transport=:transport,expiry_skew_seconds=:skew,connect_timeout_ms=:connect,response_timeout_ms=:response,
    max_token_response_size=:size,active=:active,version=version+1,updated_at=now(),updated_by=:actor where id=:id and version=:version
    """).param("id",id).param("version",version).param("code",code(p.code())).param("name",limit(p.name(),255))
    .param("description",nullable(p.description())).param("mode",upper(p.authMode())).param("connection",reference(p.tokenConnectionRef(),"connection://"))
    .param("endpoint",endpoint(p.tokenEndpointPath())).param("format",upper(p.requestFormat())).param("secret",reference(p.credentialSecretRef(),"secret://"))
    .param("clientId",reference(p.clientIdSecretRef(),"secret://")).param("clientSecret",reference(p.clientSecretRef(),"secret://"))
    .param("scope",nullable(p.scope())).param("audience",nullable(p.audience())).param("token",pointer(p.tokenResponsePointer()))
    .param("expiry",pointer(p.expiresInResponsePointer())).param("refresh",optionalPointer(p.refreshTokenResponsePointer()))
    .param("type",pointer(p.tokenTypeResponsePointer())).param("scheme",scheme(p.authorizationScheme())).param("transport",upper(p.credentialTransport()))
    .param("skew",p.expirySkewSeconds()).param("connect",p.connectTimeoutMs()).param("response",p.responseTimeoutMs())
    .param("size",p.maxTokenResponseSize()).param("active",p.active()).param("actor",limit(actor,255)).update();if(n!=1)throw new OptimisticLockingFailureException("VERSION_CONFLICT");Map<String,Object> after=row(id);audit.success("OUTBOUND_AUTH","auth.profile.updated",null,null,"OUTBOUND_AUTH_PROFILE",id.toString(),p.code(),"UPDATE",safeRow(before),safeRow(after));return after;}
  @PatchMapping("/internal/v1/registry/outbound-auth-profiles/{id}/status") @Transactional public Map<String,Object> status(@PathVariable UUID id,@RequestParam long version,@RequestBody Status p,@RequestHeader("X-Actor") String actor){int n=db.sql("update outbound_auth_profile set active=:active,version=version+1,updated_at=now(),updated_by=:actor where id=:id and version=:version").param("active",p.active()).param("actor",limit(actor,255)).param("id",id).param("version",version).update();if(n!=1)throw new OptimisticLockingFailureException("VERSION_CONFLICT");audit.success("OUTBOUND_AUTH",p.active()?"auth.profile.activated":"auth.profile.deactivated",null,null,"OUTBOUND_AUTH_PROFILE",id.toString(),id.toString(),"STATUS",null,Map.of("active",p.active()));return row(id);}
  @GetMapping("/internal/v1/registry/outbound-auth-profiles/{id}/usage") public List<Map<String,Object>> usage(@PathVariable UUID id){row(id);return db.sql("select id,code,name,active from service_target where outbound_auth_profile_id=:id order by code").param("id",id).query().listOfRows();}

  private Map<String,Object> row(UUID id){return db.sql("select * from outbound_auth_profile where id=:id").param("id",id).query().listOfRows().stream().findFirst().orElseThrow(NotFound::new);}
  private void validate(Write p){if(!MODES.contains(upper(p.authMode()))||!FORMATS.contains(upper(p.requestFormat()))||!TRANSPORTS.contains(upper(p.credentialTransport())))throw new BadRequest();if("LEGACY_SERVICE_TOKEN".equals(upper(p.authMode()))&&(reference(p.tokenConnectionRef(),"connection://").isEmpty()||endpoint(p.tokenEndpointPath()).isEmpty()||reference(p.credentialSecretRef(),"secret://").isEmpty()||!"INTERNAL_LEGACY_HEADER".equals(upper(p.credentialTransport()))))throw new BadRequest();}
  private static String endpoint(String v){String s=nullable(v);if(s.isEmpty())return s;if(!s.startsWith("/")||s.contains("://")||s.contains("..")||s.contains("%")||s.contains("\\")||s.contains("?")||s.contains("#")||s.contains("//"))throw new BadRequest();return s;}
  private static String pointer(String v){String s=limit(v,255);if(!s.matches("/(?:[A-Za-z0-9_-]+)(?:/(?:[A-Za-z0-9_-]+))*"))throw new BadRequest();return s;}
  private static String optionalPointer(String v){String s=nullable(v);return s.isEmpty()?s:pointer(s);}
  private static String reference(String v,String prefix){String s=nullable(v);if(s.isEmpty())return s;if(!s.startsWith(prefix)||!s.matches("[A-Za-z]+://[A-Za-z0-9._/-]+"))throw new BadRequest();return s;}
  private static String scheme(String v){String s=limit(v,40);if(!s.matches("[A-Za-z][A-Za-z0-9_-]*"))throw new BadRequest();return s;}
  private static String code(String v){String s=limit(v,160);if(!s.matches("[A-Za-z][A-Za-z0-9._-]*"))throw new BadRequest();return s;}
  private static String upper(String v){return limit(v,50).toUpperCase(java.util.Locale.ROOT);}
  private static String nullable(String v){return v==null?"":limit(v,1000).trim();} private static String limit(String v,int n){if(v==null||v.length()>n)throw new BadRequest();return v.trim();}
  private static Map<String,Object> safe(Write p){return Map.of("code",p.code(),"authMode",p.authMode(),"tokenConnectionRef",nullable(p.tokenConnectionRef()),"credentialSecretRef",nullable(p.credentialSecretRef()),"active",p.active());}
  private static Map<String,Object> safeRow(Map<String,Object> p){return Map.of("code",p.get("code"),"authMode",p.get("auth_mode"),"tokenConnectionRef",String.valueOf(p.get("token_connection_ref")),"credentialSecretRef",String.valueOf(p.get("credential_secret_ref")),"version",p.get("version"));}
  public record Write(@NotBlank String code,@NotBlank String name,String description,@NotBlank String authMode,String tokenConnectionRef,String tokenEndpointPath,@NotBlank String requestFormat,String credentialSecretRef,String clientIdSecretRef,String clientSecretRef,String scope,String audience,@NotBlank String tokenResponsePointer,@NotBlank String expiresInResponsePointer,String refreshTokenResponsePointer,@NotBlank String tokenTypeResponsePointer,@NotBlank String authorizationScheme,@NotBlank String credentialTransport,@Min(5) @Max(600) int expirySkewSeconds,@Min(100) @Max(30000) int connectTimeoutMs,@Min(100) @Max(120000) int responseTimeoutMs,@Min(1024) @Max(5242880) long maxTokenResponseSize,boolean active){}
  public record Status(boolean active){}
  @ResponseStatus(HttpStatus.BAD_REQUEST) static class BadRequest extends RuntimeException{} @ResponseStatus(HttpStatus.NOT_FOUND) static class NotFound extends RuntimeException{}
}

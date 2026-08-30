package com.aurevia.authz.api;

import com.aurevia.authz.observability.AuditTrail;
import com.aurevia.authz.routing.RoutePathPolicy;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.net.URI;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

/** Control-plane API. It stores only approved gateway metadata, never credentials or tokens. */
@RestController
@RequestMapping("/internal/v1/registry")
public class ProxyRouteAdminController {
  private static final Set<String> METHODS=Set.of("GET","HEAD","OPTIONS","POST","PUT","PATCH","DELETE");
  private final JdbcClient db;
  private final AuditTrail audit;
  private final RouteResolutionController resolver;
  private final Set<String> approvedGatewayHosts;

  public ProxyRouteAdminController(JdbcClient db,AuditTrail audit,RouteResolutionController resolver,
      @Value("${aurevia.routing.approved-gateway-hosts:operation-gateway}") String hosts) {
    this.db=db;this.audit=audit;this.resolver=resolver;
    this.approvedGatewayHosts=Arrays.stream(hosts.split(",")).map(String::trim)
        .map(value->value.toLowerCase(Locale.ROOT)).filter(value->!value.isBlank()).collect(java.util.stream.Collectors.toUnmodifiableSet());
  }

  @GetMapping("/service-targets")
  public List<Map<String,Object>> targets(@RequestParam(defaultValue="") String search) {
    return db.sql("""
        select st.*,(select count(*) from proxy_route pr where pr.service_target_id=st.id) as route_count
        from service_target st where (:search='' or lower(st.code||' '||st.name) like lower('%'||:search||'%'))
        order by st.code
        """).param("search",limit(search,100)).query().listOfRows();
  }

  @GetMapping("/service-targets/{id}")
  public Map<String,Object> target(@PathVariable UUID id){return row("select * from service_target where id=:id",id);}

  @PostMapping("/service-targets") @ResponseStatus(HttpStatus.CREATED) @Transactional
  public Map<String,Object> createTarget(@Valid @RequestBody TargetWrite request,@RequestHeader("X-Actor") String actor) {
    URI uri=validateGateway(request.gatewayBaseUrl());UUID id=UUID.randomUUID();
    db.sql("""
        insert into service_target(id,code,name,description,gateway_base_url,upstream_base_path,environment,
        tls_profile_ref,secret_ref,health_check_path,connect_timeout_ms,response_timeout_ms,max_response_size,active,created_by,updated_by)
        values(:id,:code,:name,nullif(:description,''),:url,:upstream,:environment,nullif(:tls,''),nullif(:secret,''),
        :health,:connect,:response,:size,:active,:actor,:actor)
        """)
      .param("id",id).param("code",code(request.code())).param("name",limit(request.name(),255))
      .param("description",nullable(request.description())).param("url",uri.toString())
      .param("upstream",RoutePathPolicy.path(request.upstreamBasePath())).param("environment",code(request.environment()))
      .param("tls",reference(request.tlsProfileRef())).param("secret",reference(request.secretRef()))
      .param("health",RoutePathPolicy.path(request.healthCheckPath())).param("connect",request.connectTimeoutMs())
      .param("response",request.responseTimeoutMs()).param("size",request.maxResponseSize())
      .param("active",request.active()).param("actor",limit(actor,255)).update();
    audit.success("PROXY_ROUTE","proxy.target.created",null,null,"SERVICE_TARGET",id.toString(),request.code(),"CREATE",null,safeTarget(request));
    return target(id);
  }

  @PutMapping("/service-targets/{id}") @Transactional
  public Map<String,Object> updateTarget(@PathVariable UUID id,@RequestParam long version,
      @Valid @RequestBody TargetWrite request,@RequestHeader("X-Actor") String actor) {
    Map<String,Object> before=target(id);URI uri=validateGateway(request.gatewayBaseUrl());
    int changed=db.sql("""
        update service_target set code=:code,name=:name,description=nullif(:description,''),gateway_base_url=:url,
        upstream_base_path=:upstream,environment=:environment,tls_profile_ref=nullif(:tls,''),secret_ref=nullif(:secret,''),
        health_check_path=:health,connect_timeout_ms=:connect,response_timeout_ms=:response,max_response_size=:size,
        active=:active,version=version+1,updated_at=now(),updated_by=:actor where id=:id and version=:version
        """)
      .param("code",code(request.code())).param("name",limit(request.name(),255)).param("description",nullable(request.description()))
      .param("url",uri.toString()).param("upstream",RoutePathPolicy.path(request.upstreamBasePath()))
      .param("environment",code(request.environment())).param("tls",reference(request.tlsProfileRef()))
      .param("secret",reference(request.secretRef())).param("health",RoutePathPolicy.path(request.healthCheckPath()))
      .param("connect",request.connectTimeoutMs()).param("response",request.responseTimeoutMs())
      .param("size",request.maxResponseSize()).param("active",request.active()).param("actor",limit(actor,255))
      .param("id",id).param("version",version).update();
    optimistic(changed);Map<String,Object> after=target(id);
    audit.success("PROXY_ROUTE","proxy.target.updated",null,null,"SERVICE_TARGET",id.toString(),request.code(),"UPDATE",before,after);
    return after;
  }

  @PatchMapping("/service-targets/{id}/status") @Transactional
  public Map<String,Object> targetStatus(@PathVariable UUID id,@RequestParam long version,@RequestBody StatusWrite request,@RequestHeader("X-Actor") String actor) {
    optimistic(db.sql("update service_target set active=:active,version=version+1,updated_at=now(),updated_by=:actor where id=:id and version=:version")
      .param("active",request.active()).param("actor",limit(actor,255)).param("id",id).param("version",version).update());
    audit.success("PROXY_ROUTE",request.active()?"proxy.target.activated":"proxy.target.deactivated",null,null,"SERVICE_TARGET",id.toString(),id.toString(),"STATUS",null,Map.of("active",request.active()));return target(id);
  }

  @GetMapping("/proxy-routes")
  public List<Map<String,Object>> routes(@RequestParam(defaultValue="") String search,@RequestParam(required=false) UUID panelId,
      @RequestParam(required=false) UUID targetId,@RequestParam(required=false) Boolean active) {
    return db.sql("""
        select pr.*,p.slug as panel_slug,p.name_fa as panel_name,st.code as target_code
        from proxy_route pr join panel p on p.id=pr.panel_id join service_target st on st.id=pr.service_target_id
        where (:search='' or lower(pr.code||' '||pr.path_prefix) like lower('%'||:search||'%'))
        and (nullif(:panel,'') is null or pr.panel_id=cast(nullif(:panel,'') as uuid))
        and (nullif(:target,'') is null or pr.service_target_id=cast(nullif(:target,'') as uuid))
        and (nullif(:active,'') is null or pr.active=cast(nullif(:active,'') as boolean)) order by length(pr.normalized_path_prefix) desc,pr.priority desc,pr.code
        """)
      .param("search",limit(search,100)).param("panel",panelId==null?"":panelId.toString())
      .param("target",targetId==null?"":targetId.toString()).param("active",active==null?"":active.toString())
      .query().listOfRows().stream().map(ProxyRouteAdminController::serializableRow).toList();
  }

  @GetMapping("/proxy-routes/{id}") public Map<String,Object> route(@PathVariable UUID id){return serializableRow(row("select * from proxy_route where id=:id",id));}

  @PostMapping("/proxy-routes/validate") public Map<String,Object> validate(@Valid @RequestBody RouteWrite request){validateRoute(request,null);return Map.of("valid",true,"normalizedPathPrefix",RoutePathPolicy.prefix(request.pathPrefix()));}
  @PostMapping("/proxy-routes/preview") public Map<String,Object> preview(@RequestBody PreviewRequest request){Map<String,Object> route=route(request.routeId());return previewRoute(route,RoutePathPolicy.path(request.path()));}
  @PostMapping("/proxy-routes/resolve-test") public RouteResolutionController.RouteResolution resolveTest(@RequestBody MatchRequest request){RouteResolutionController.RouteResolution result=resolver.resolve(request.path(),request.method());audit.success("PROXY_ROUTE","proxy.route.resolve_test",null,null,"PROXY_ROUTE",result.routeId().toString(),result.routeKey(),"TEST",null,Map.of("method",request.method(),"path",request.path(),"operationId",result.operationId().toString()));return result;}

  @PostMapping("/proxy-routes") @ResponseStatus(HttpStatus.CREATED) @Transactional
  public Map<String,Object> createRoute(@Valid @RequestBody RouteWrite request,@RequestHeader("X-Actor") String actor) {
    validateRoute(request,null);UUID id=UUID.randomUUID();String prefix=RoutePathPolicy.prefix(request.pathPrefix());
    db.sql("""
        insert into proxy_route(id,code,panel_id,service_target_id,path_prefix,normalized_path_prefix,strip_prefix,
        rewrite_pattern,rewrite_replacement,priority,allowed_methods,preserve_host,retry_enabled,max_retries,active,created_by,updated_by)
        values(:id,:code,:panel,:target,:path,:normalized,:strip,nullif(:pattern,''),nullif(:replacement,''),:priority,
        :methods,:host,:retry,:retries,:active,:actor,:actor)
        """)
      .param("id",id).param("code",code(request.code())).param("panel",request.panelId()).param("target",request.serviceTargetId())
      .param("path",RoutePathPolicy.path(request.pathPrefix())).param("normalized",prefix).param("strip",request.stripPrefix())
      .param("pattern",nullable(request.rewritePattern())).param("replacement",nullable(request.rewriteReplacement()))
      .param("priority",request.priority()).param("methods",request.allowedMethods().stream().map(this::method).toArray(String[]::new))
      .param("host",request.preserveHost()).param("retry",request.retryEnabled()).param("retries",request.maxRetries())
      .param("active",request.active()).param("actor",limit(actor,255)).update();
    audit.success("PROXY_ROUTE","proxy.route.created",null,null,"PROXY_ROUTE",id.toString(),request.code(),"CREATE",null,Map.of("pathPrefix",prefix,"targetId",request.serviceTargetId().toString()));return route(id);
  }

  @PutMapping("/proxy-routes/{id}") @Transactional
  public Map<String,Object> updateRoute(@PathVariable UUID id,@RequestParam long version,@Valid @RequestBody RouteWrite request,@RequestHeader("X-Actor") String actor) {
    validateRoute(request,id);Map<String,Object> before=route(id);String prefix=RoutePathPolicy.prefix(request.pathPrefix());
    int changed=db.sql("""
        update proxy_route set code=:code,panel_id=:panel,service_target_id=:target,path_prefix=:path,
        normalized_path_prefix=:normalized,strip_prefix=:strip,rewrite_pattern=nullif(:pattern,''),rewrite_replacement=nullif(:replacement,''),
        priority=:priority,allowed_methods=:methods,preserve_host=:host,retry_enabled=:retry,max_retries=:retries,active=:active,
        version=version+1,updated_at=now(),updated_by=:actor where id=:id and version=:version
        """)
      .param("code",code(request.code())).param("panel",request.panelId()).param("target",request.serviceTargetId())
      .param("path",RoutePathPolicy.path(request.pathPrefix())).param("normalized",prefix).param("strip",request.stripPrefix())
      .param("pattern",nullable(request.rewritePattern())).param("replacement",nullable(request.rewriteReplacement()))
      .param("priority",request.priority()).param("methods",request.allowedMethods().stream().map(this::method).toArray(String[]::new))
      .param("host",request.preserveHost()).param("retry",request.retryEnabled()).param("retries",request.maxRetries())
      .param("active",request.active()).param("actor",limit(actor,255)).param("id",id).param("version",version).update();
    optimistic(changed);Map<String,Object> after=route(id);audit.success("PROXY_ROUTE","proxy.route.updated",null,null,"PROXY_ROUTE",id.toString(),request.code(),"UPDATE",before,after);return after;
  }

  @PatchMapping("/proxy-routes/{id}/status") @Transactional public Map<String,Object> routeStatus(@PathVariable UUID id,@RequestParam long version,@RequestBody StatusWrite request,@RequestHeader("X-Actor") String actor){optimistic(db.sql("update proxy_route set active=:active,version=version+1,updated_at=now(),updated_by=:actor where id=:id and version=:version").param("active",request.active()).param("actor",limit(actor,255)).param("id",id).param("version",version).update());audit.success("PROXY_ROUTE",request.active()?"proxy.route.activated":"proxy.route.deactivated",null,null,"PROXY_ROUTE",id.toString(),id.toString(),"STATUS",null,Map.of("active",request.active()));return route(id);}

  @GetMapping("/proxy-routes/{routeId}/operations") public List<Map<String,Object>> operations(@PathVariable UUID routeId){return db.sql("select * from route_operation where proxy_route_id=:id order by http_method,normalized_path_pattern").param("id",routeId).query().listOfRows();}
  @PostMapping("/proxy-routes/{routeId}/operations") @ResponseStatus(HttpStatus.CREATED) @Transactional
  public Map<String,Object> createOperation(@PathVariable UUID routeId,@Valid @RequestBody OperationWrite request,@RequestHeader("X-Actor") String actor){validateOperation(routeId,request,null);UUID id=UUID.randomUUID();insertOperation(id,routeId,request,actor);audit.success("PROXY_ROUTE","proxy.operation.created",null,null,"ROUTE_OPERATION",id.toString(),request.resourceKey()+":"+request.actionKey(),"CREATE",null,Map.of("method",request.httpMethod(),"pattern",request.pathPattern()));return operation(id);}
  @PutMapping("/proxy-routes/{routeId}/operations/{id}") @Transactional public Map<String,Object> updateOperation(@PathVariable UUID routeId,@PathVariable UUID id,@RequestParam long version,@Valid @RequestBody OperationWrite request,@RequestHeader("X-Actor") String actor){validateOperation(routeId,request,id);Map<String,Object> before=operation(id);UUID resource=resource(request.resourceKey(),request.actionKey());int changed=db.sql("""
      update route_operation set http_method=:method,path_pattern=:pattern,normalized_path_pattern=:pattern,
      resource_id=:resource,action_id=(select id from action where action_key=:action),resource_key=:resourceKey,action_key=:action,
      authorization_required=:required,data_policy_key=nullif(:policy,''),active=:active,max_body_bytes=:body,version=version+1,
      updated_at=now(),updated_by=:actor where id=:id and proxy_route_id=:route and version=:version
      """).param("method",method(request.httpMethod())).param("pattern",RoutePathPolicy.pattern(request.pathPattern())).param("resource",resource).param("action",code(request.actionKey())).param("resourceKey",request.resourceKey()).param("required",request.authorizationRequired()).param("policy",nullable(request.dataPolicyKey())).param("active",request.active()).param("body",request.maxBodyBytes()).param("actor",limit(actor,255)).param("id",id).param("route",routeId).param("version",version).update();optimistic(changed);Map<String,Object> after=operation(id);audit.success("PROXY_ROUTE","proxy.operation.updated",null,null,"ROUTE_OPERATION",id.toString(),request.resourceKey()+":"+request.actionKey(),"UPDATE",before,after);return after;}
  @DeleteMapping("/proxy-routes/{routeId}/operations/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) @Transactional public void deleteOperation(@PathVariable UUID routeId,@PathVariable UUID id,@RequestParam long version){optimistic(db.sql("update route_operation set active=false,version=version+1,updated_at=now() where id=:id and proxy_route_id=:route and version=:version").param("id",id).param("route",routeId).param("version",version).update());audit.success("PROXY_ROUTE","proxy.operation.deactivated",null,null,"ROUTE_OPERATION",id.toString(),id.toString(),"DEACTIVATE",null,Map.of("active",false));}

  @PostMapping("/proxy-routes/{routeId}/operations/match-test") public Map<String,Object> match(@PathVariable UUID routeId,@RequestBody MatchRequest request){String path=RoutePathPolicy.path(request.path());String verb=method(request.method());List<Map<String,Object>> matches=operations(routeId).stream().filter(row->Boolean.TRUE.equals(row.get("active"))&&verb.equals(row.get("http_method"))&&RoutePathPolicy.matches(String.valueOf(row.get("normalized_path_pattern")),path)).sorted((a,b)->Integer.compare(RoutePathPolicy.specificity(String.valueOf(b.get("normalized_path_pattern"))),RoutePathPolicy.specificity(String.valueOf(a.get("normalized_path_pattern"))))).toList();if(matches.isEmpty())return Map.of("matched",false);if(matches.size()>1&&RoutePathPolicy.specificity(String.valueOf(matches.get(0).get("normalized_path_pattern")))==RoutePathPolicy.specificity(String.valueOf(matches.get(1).get("normalized_path_pattern"))))throw new Conflict("AMBIGUOUS_OPERATION");return Map.of("matched",true,"operation",matches.get(0));}

  private void insertOperation(UUID id,UUID routeId,OperationWrite request,String actor){UUID resource=resource(request.resourceKey(),request.actionKey());String pattern=RoutePathPolicy.pattern(request.pathPattern());db.sql("""
      insert into route_operation(id,proxy_route_id,http_method,path_pattern,normalized_path_pattern,resource_id,action_id,
      resource_key,action_key,authorization_required,data_policy_key,active,max_body_bytes,created_by,updated_by)
      values(:id,:route,:method,:pattern,:pattern,:resource,(select id from action where action_key=:action),:resourceKey,:action,
      :required,nullif(:policy,''),:active,:body,:actor,:actor)
      """).param("id",id).param("route",routeId).param("method",method(request.httpMethod())).param("pattern",pattern).param("resource",resource).param("action",code(request.actionKey())).param("resourceKey",request.resourceKey()).param("required",request.authorizationRequired()).param("policy",nullable(request.dataPolicyKey())).param("active",request.active()).param("body",request.maxBodyBytes()).param("actor",limit(actor,255)).update();}
  private Map<String,Object> operation(UUID id){return row("select * from route_operation where id=:id",id);}
  private UUID resource(String key,String action){return db.sql("""
      select r.id from resource r join resource_action ra on ra.resource_id=r.id join action a on a.id=ra.action_id
      where r.resource_key=:resource and r.status='ACTIVE' and a.action_key=:action
      """).param("resource",limit(key,500)).param("action",code(action)).query(UUID.class).optional().orElseThrow(()->new BadRequest("INVALID_RESOURCE_ACTION"));}
  private void validateOperation(UUID routeId,OperationWrite request,UUID self){route(routeId);String verb=method(request.httpMethod());String pattern=RoutePathPolicy.pattern(request.pathPattern());resource(request.resourceKey(),request.actionKey());long conflicts=db.sql("select count(*) from route_operation where proxy_route_id=:route and http_method=:method and normalized_path_pattern=:pattern and id<>cast(:self as uuid)").param("route",routeId).param("method",verb).param("pattern",pattern).param("self",self==null?"00000000-0000-0000-0000-000000000000":self.toString()).query(Long.class).single();if(conflicts>0)throw new Conflict("DUPLICATE_OPERATION");}
  private void validateRoute(RouteWrite request,UUID self){String prefix=RoutePathPolicy.prefix(request.pathPrefix());Map<String,Object> panel=row("select id,slug from panel where id=:id",request.panelId());String slug=String.valueOf(panel.get("slug"));String expected="/"+slug+"/",legacyApiNamespace="/"+slug+"-micro/";if(!prefix.startsWith(expected)&&!prefix.startsWith(legacyApiNamespace))throw new BadRequest("PREFIX_MUST_START_WITH_PANEL_SLUG");target(request.serviceTargetId());request.allowedMethods().forEach(this::method);if(request.retryEnabled()&&request.allowedMethods().stream().anyMatch(value->!Set.of("GET","HEAD","OPTIONS").contains(value.toUpperCase())))throw new BadRequest("RETRY_REQUIRES_SAFE_METHODS");validateRewrite(request.rewritePattern(),request.rewriteReplacement());long conflict=db.sql("select count(*) from proxy_route where normalized_path_prefix=:prefix and priority=:priority and id<>cast(:self as uuid)").param("prefix",prefix).param("priority",request.priority()).param("self",self==null?"00000000-0000-0000-0000-000000000000":self.toString()).query(Long.class).single();if(conflict>0){audit.success("PROXY_ROUTE","proxy.route.prefix_collision",null,null,"PROXY_ROUTE",prefix,prefix,"VALIDATE",null,Map.of("priority",request.priority()));throw new Conflict("PREFIX_COLLISION");}}
  private Map<String,Object> previewRoute(Map<String,Object> route,String path){String prefix=String.valueOf(route.get("normalized_path_prefix"));if(!path.startsWith(prefix)&&!path.equals(prefix.substring(0,prefix.length()-1)))throw new BadRequest("PATH_OUTSIDE_ROUTE");String relative=Boolean.TRUE.equals(((Number)route.get("strip_prefix")).intValue()>0)?path.substring(prefix.length()-1):path;String pattern=(String)route.get("rewrite_pattern"),replacement=(String)route.get("rewrite_replacement");String upstream=pattern==null?relative:relative.replaceFirst(java.util.regex.Pattern.quote(pattern.substring(1)),java.util.regex.Matcher.quoteReplacement(replacement));RoutePathPolicy.path(upstream);return Map.of("incomingPath",path,"upstreamPath",upstream,"routeId",route.get("id"));}
  private URI validateGateway(String value){URI uri;try{uri=URI.create(value);}catch(Exception e){throw new BadRequest("INVALID_GATEWAY_URL");}String host=uri.getHost()==null?"":uri.getHost().toLowerCase(Locale.ROOT);if(!Set.of("http","https").contains(uri.getScheme())||uri.getUserInfo()!=null||uri.getQuery()!=null||uri.getFragment()!=null||!approvedGatewayHosts.contains(host)||isForbiddenHost(host))throw new BadRequest("UNAPPROVED_GATEWAY_HOST");return uri;}
  private static boolean isForbiddenHost(String host){return host.equals("localhost")||host.equals("0.0.0.0")||host.equals("169.254.169.254")||host.startsWith("127.")||host.startsWith("169.254.")||host.equals("::1");}
  private static void validateRewrite(String pattern,String replacement){if((pattern==null||pattern.isBlank())!=(replacement==null||replacement.isBlank()))throw new BadRequest("REWRITE_PAIR_REQUIRED");if(pattern==null||pattern.isBlank())return;if(!pattern.startsWith("^/")||pattern.substring(1).contains(".")||pattern.contains("*")||pattern.contains("[")||pattern.contains("(")||replacement.contains("://"))throw new BadRequest("UNSAFE_REWRITE");RoutePathPolicy.path(pattern.substring(1));RoutePathPolicy.path(replacement);}
  private Map<String,Object> row(String sql,UUID id){List<Map<String,Object>> rows=db.sql(sql).param("id",id).query().listOfRows();if(rows.isEmpty())throw new NotFound();return rows.get(0);}
  private String method(String value){String result=code(value);if(!METHODS.contains(result))throw new BadRequest("INVALID_HTTP_METHOD");return result;}
  private static String code(String value){String result=limit(value,160).trim();if(!result.matches("[A-Za-z][A-Za-z0-9._-]*"))throw new BadRequest("INVALID_CODE");return result;}
  private static String reference(String value){String result=nullable(value);if(result.isEmpty())return result;if(!result.matches("(secret|tls)://[A-Za-z0-9._/-]+"))throw new BadRequest("INVALID_SECRET_REFERENCE");return result;}
  private static String nullable(String value){return value==null?"":limit(value,1000).trim();}
  private static String limit(String value,int max){if(value==null||value.length()>max)throw new BadRequest("INVALID_FIELD_LENGTH");return value;}
  private static void optimistic(int changed){if(changed!=1)throw new OptimisticLockingFailureException("VERSION_CONFLICT");}
  private static Map<String,Object> safeTarget(TargetWrite value){return Map.of("code",value.code(),"gatewayHost",URI.create(value.gatewayBaseUrl()).getHost(),"environment",value.environment(),"active",value.active());}
  private static Map<String,Object> serializableRow(Map<String,Object> row){
    java.util.LinkedHashMap<String,Object> result=new java.util.LinkedHashMap<>(row);
    result.replaceAll((key,value)->{
      if(!(value instanceof java.sql.Array array))return value;
      try{return Arrays.asList((Object[])array.getArray());}
      catch(java.sql.SQLException failure){throw new IllegalStateException("Cannot read SQL array",failure);}
    });
    return result;
  }

  public record TargetWrite(@NotBlank String code,@NotBlank String name,String description,@NotBlank String gatewayBaseUrl,
      @NotBlank String upstreamBasePath,@NotBlank String environment,String tlsProfileRef,String secretRef,
      @NotBlank String healthCheckPath,@Min(100) @Max(30000) int connectTimeoutMs,@Min(100) @Max(120000) int responseTimeoutMs,
      @Min(1024) @Max(104857600) long maxResponseSize,boolean active){}
  public record RouteWrite(@NotBlank String code,UUID panelId,UUID serviceTargetId,@NotBlank String pathPrefix,
      @Min(0) @Max(20) int stripPrefix,String rewritePattern,String rewriteReplacement,@Min(-1000) @Max(1000) int priority,
      @NotEmpty List<String> allowedMethods,boolean preserveHost,boolean retryEnabled,@Min(0) @Max(3) int maxRetries,boolean active){}
  public record OperationWrite(@NotBlank String httpMethod,@NotBlank String pathPattern,@NotBlank String resourceKey,
      @NotBlank String actionKey,boolean authorizationRequired,String dataPolicyKey,boolean active,@Min(0) @Max(104857600) long maxBodyBytes){}
  public record StatusWrite(boolean active){}
  public record PreviewRequest(UUID routeId,@NotBlank String path){}
  public record MatchRequest(@NotBlank String method,@NotBlank String path){}
  @ResponseStatus(HttpStatus.BAD_REQUEST) static class BadRequest extends RuntimeException{BadRequest(String message){super(message);}}
  @ResponseStatus(HttpStatus.NOT_FOUND) static class NotFound extends RuntimeException{}
  @ResponseStatus(HttpStatus.CONFLICT) static class Conflict extends RuntimeException{Conflict(String message){super(message);}}
}

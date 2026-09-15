package com.aurevia.authz.routing;

import static com.aurevia.authz.api.dto.ProxyRouteDtos.*;

import com.aurevia.authz.observability.AuditTrail;
import java.net.URI;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Application service for the credential-free proxy route control plane. */
@Service
public class ProxyRouteAdministrationService {
  private static final UUID NO_ID=UUID.fromString("00000000-0000-0000-0000-000000000000");
  private static final Set<String> METHODS=Set.of(
      "GET","HEAD","OPTIONS","POST","PUT","PATCH","DELETE");
  private final ProxyRouteRepository routes;
  private final AuditTrail audit;
  private final RouteResolutionService resolver;
  private final Set<String> approvedGatewayHosts;

  public ProxyRouteAdministrationService(ProxyRouteRepository routes,AuditTrail audit,
      RouteResolutionService resolver,
      @Value("${aurevia.routing.approved-gateway-hosts:operation-gateway}") String hosts) {
    this.routes=routes;this.audit=audit;this.resolver=resolver;
    this.approvedGatewayHosts=Arrays.stream(hosts.split(",")).map(String::trim)
        .map(value->value.toLowerCase(Locale.ROOT)).filter(value->!value.isBlank())
        .collect(Collectors.toUnmodifiableSet());
  }

  public List<Map<String,Object>> targets(String search) { return routes.targets(limit(search,100)); }
  public Map<String,Object> target(UUID id) { return routes.target(id).orElseThrow(ProxyRouteAdministrationService::notFound); }
  public List<Map<String,Object>> routes(String search,UUID panelId,UUID targetId,Boolean active) {
    return routes.routes(limit(search,100),panelId,targetId,active);
  }
  public Map<String,Object> route(UUID id) { return routes.route(id).orElseThrow(ProxyRouteAdministrationService::notFound); }
  public List<Map<String,Object>> operations(UUID routeId) { return routes.operations(routeId); }

  @Transactional
  public Map<String,Object> createTarget(TargetRequest request,String actor) {
    UUID id=UUID.randomUUID();
    var value=targetValue(id,request,actor);
    routes.insertTarget(value);
    audit.success("PROXY_ROUTE","proxy.target.created",null,null,"SERVICE_TARGET",
        id.toString(),request.code(),"CREATE",null,safeTarget(request));
    return target(id);
  }

  @Transactional
  public Map<String,Object> updateTarget(UUID id,long version,TargetRequest request,String actor) {
    Map<String,Object> before=target(id);
    if(!routes.updateTarget(id,version,targetValue(id,request,actor))) conflictVersion();
    Map<String,Object> after=target(id);
    audit.success("PROXY_ROUTE","proxy.target.updated",null,null,"SERVICE_TARGET",
        id.toString(),request.code(),"UPDATE",before,after);
    return after;
  }

  @Transactional
  public Map<String,Object> updateTargetStatus(UUID id,long version,StatusRequest request,
      String actor) {
    if(!routes.updateTargetStatus(id,version,request.active(),limit(actor,255))) conflictVersion();
    audit.success("PROXY_ROUTE",request.active()?"proxy.target.activated":"proxy.target.deactivated",
        null,null,"SERVICE_TARGET",id.toString(),id.toString(),"STATUS",null,
        Map.of("active",request.active()));
    return target(id);
  }

  public ValidationResponse validate(RouteRequest request) {
    validateRoute(request,null);
    return new ValidationResponse(true,RoutePathPolicy.prefix(request.pathPrefix()));
  }

  public PreviewResponse preview(PreviewRequest request) {
    return previewRoute(route(request.routeId()),RoutePathPolicy.path(request.path()));
  }

  public ResolvedRoute resolveTest(MatchRequest request) {
    ResolvedRoute result=resolver.resolve(request.path(),request.method());
    audit.success("PROXY_ROUTE","proxy.route.resolve_test",null,null,"PROXY_ROUTE",
        result.routeId().toString(),result.routeKey(),"TEST",null,
        Map.of("method",request.method(),"path",request.path(),
            "operationId",result.operationId().toString()));
    return result;
  }

  @Transactional
  public Map<String,Object> createRoute(RouteRequest request,String actor) {
    validateRoute(request,null);
    UUID id=UUID.randomUUID();
    String prefix=RoutePathPolicy.prefix(request.pathPrefix());
    routes.insertRoute(routeValue(id,request,actor));
    audit.success("PROXY_ROUTE","proxy.route.created",null,null,"PROXY_ROUTE",
        id.toString(),request.code(),"CREATE",null,
        Map.of("pathPrefix",prefix,"targetId",request.serviceTargetId().toString()));
    return route(id);
  }

  @Transactional
  public Map<String,Object> updateRoute(UUID id,long version,RouteRequest request,String actor) {
    validateRoute(request,id);
    Map<String,Object> before=route(id);
    if(!routes.updateRoute(id,version,routeValue(id,request,actor))) conflictVersion();
    Map<String,Object> after=route(id);
    audit.success("PROXY_ROUTE","proxy.route.updated",null,null,"PROXY_ROUTE",
        id.toString(),request.code(),"UPDATE",before,after);
    return after;
  }

  @Transactional
  public Map<String,Object> updateRouteStatus(UUID id,long version,StatusRequest request,
      String actor) {
    if(!routes.updateRouteStatus(id,version,request.active(),limit(actor,255))) conflictVersion();
    audit.success("PROXY_ROUTE",request.active()?"proxy.route.activated":"proxy.route.deactivated",
        null,null,"PROXY_ROUTE",id.toString(),id.toString(),"STATUS",null,
        Map.of("active",request.active()));
    return route(id);
  }

  @Transactional
  public Map<String,Object> createOperation(UUID routeId,OperationRequest request,String actor) {
    validateOperation(routeId,request,null);
    UUID id=UUID.randomUUID();
    routes.insertOperation(operationValue(id,routeId,request,actor));
    audit.success("PROXY_ROUTE","proxy.operation.created",null,null,"ROUTE_OPERATION",
        id.toString(),request.resourceKey()+":"+request.actionKey(),"CREATE",null,
        Map.of("method",request.httpMethod(),"pattern",request.pathPattern()));
    return operation(id);
  }

  @Transactional
  public Map<String,Object> updateOperation(UUID routeId,UUID id,long version,
      OperationRequest request,String actor) {
    validateOperation(routeId,request,id);
    Map<String,Object> before=operation(id);
    if(!routes.updateOperation(id,routeId,version,
        operationValue(id,routeId,request,actor))) conflictVersion();
    Map<String,Object> after=operation(id);
    audit.success("PROXY_ROUTE","proxy.operation.updated",null,null,"ROUTE_OPERATION",
        id.toString(),request.resourceKey()+":"+request.actionKey(),"UPDATE",before,after);
    return after;
  }

  @Transactional
  public void deleteOperation(UUID routeId,UUID id,long version) {
    if(!routes.disableOperation(id,routeId,version)) conflictVersion();
    audit.success("PROXY_ROUTE","proxy.operation.deactivated",null,null,"ROUTE_OPERATION",
        id.toString(),id.toString(),"DEACTIVATE",null,Map.of("active",false));
  }

  public Map<String,Object> match(UUID routeId,MatchRequest request) {
    String path=RoutePathPolicy.path(request.path());
    String verb=method(request.method());
    List<Map<String,Object>> matches=operations(routeId).stream()
        .filter(row->Boolean.TRUE.equals(row.get("active"))&&verb.equals(row.get("http_method"))
            &&RoutePathPolicy.matches(String.valueOf(row.get("normalized_path_pattern")),path))
        .sorted((first,second)->Integer.compare(
            RoutePathPolicy.specificity(String.valueOf(second.get("normalized_path_pattern"))),
            RoutePathPolicy.specificity(String.valueOf(first.get("normalized_path_pattern")))))
        .toList();
    if(matches.isEmpty()) return Map.of("matched",false);
    if(matches.size()>1&&RoutePathPolicy.specificity(
        String.valueOf(matches.getFirst().get("normalized_path_pattern")))
        ==RoutePathPolicy.specificity(String.valueOf(matches.get(1).get("normalized_path_pattern")))) {
      throw conflict("AMBIGUOUS_OPERATION");
    }
    return Map.of("matched",true,"operation",matches.getFirst());
  }

  private ProxyRouteRepository.TargetValue targetValue(UUID id,TargetRequest request,String actor) {
    URI gateway=validateGateway(request.gatewayBaseUrl());
    return new ProxyRouteRepository.TargetValue(id,code(request.code()),limit(request.name(),255),
        nullable(request.description()),gateway.toString(),
        RoutePathPolicy.path(request.upstreamBasePath()),code(request.environment()),
        reference(request.tlsProfileRef()),reference(request.secretRef()),
        RoutePathPolicy.path(request.healthCheckPath()),request.connectTimeoutMs(),
        request.responseTimeoutMs(),request.maxResponseSize(),request.outboundAuthProfileId(),
        request.active(),limit(actor,255));
  }

  private ProxyRouteRepository.RouteValue routeValue(UUID id,RouteRequest request,String actor) {
    return new ProxyRouteRepository.RouteValue(id,code(request.code()),request.panelId(),
        request.serviceTargetId(),serviceSlug(request.serviceSlug()),
        RoutePathPolicy.path(request.pathPrefix()),RoutePathPolicy.prefix(request.pathPrefix()),
        request.stripPrefix(),nullable(request.rewritePattern()),
        nullable(request.rewriteReplacement()),request.priority(),request.allowedMethods().stream()
            .map(this::method).toArray(String[]::new),request.preserveHost(),request.retryEnabled(),
        request.maxRetries(),request.active(),limit(actor,255));
  }

  private ProxyRouteRepository.OperationValue operationValue(UUID id,UUID routeId,
      OperationRequest request,String actor) {
    return new ProxyRouteRepository.OperationValue(id,routeId,method(request.httpMethod()),
        RoutePathPolicy.pattern(request.pathPattern()),resource(request.resourceKey(),request.actionKey()),
        limit(request.resourceKey(),500),code(request.actionKey()),request.authorizationRequired(),
        nullable(request.dataPolicyKey()),request.active(),request.maxBodyBytes(),limit(actor,255));
  }

  private void validateOperation(UUID routeId,OperationRequest request,UUID self) {
    route(routeId);
    String verb=method(request.httpMethod());
    String pattern=RoutePathPolicy.pattern(request.pathPattern());
    resource(request.resourceKey(),request.actionKey());
    if(routes.operationConflict(routeId,verb,pattern,self==null?NO_ID:self)>0) {
      throw conflict("DUPLICATE_OPERATION");
    }
  }

  private void validateRoute(RouteRequest request,UUID self) {
    String prefix=RoutePathPolicy.prefix(request.pathPrefix());
    String slug=routes.panelSlug(request.panelId()).orElseThrow(
        ProxyRouteAdministrationService::notFound);
    String service=serviceSlug(request.serviceSlug());
    if(!prefix.startsWith("/api/proxy/"+service+"/")
        &&!prefix.startsWith("/"+slug+"-micro/")) {
      throw bad("PREFIX_MUST_START_WITH_SERVICE_SLUG");
    }
    if(!routes.targetExists(request.serviceTargetId())) throw notFound();
    request.allowedMethods().forEach(this::method);
    if(request.retryEnabled()&&request.allowedMethods().stream()
        .anyMatch(value->!Set.of("GET","HEAD","OPTIONS").contains(value.toUpperCase(Locale.ROOT)))) {
      throw bad("RETRY_REQUIRES_SAFE_METHODS");
    }
    validateRewrite(request.rewritePattern(),request.rewriteReplacement());
    if(routes.routeConflict(prefix,request.priority(),self==null?NO_ID:self)>0) {
      audit.success("PROXY_ROUTE","proxy.route.prefix_collision",null,null,"PROXY_ROUTE",
          prefix,prefix,"VALIDATE",null,Map.of("priority",request.priority()));
      throw conflict("PREFIX_COLLISION");
    }
  }

  private PreviewResponse previewRoute(Map<String,Object> route,String path) {
    String prefix=String.valueOf(route.get("normalized_path_prefix"));
    if(!path.startsWith(prefix)&&!path.equals(prefix.substring(0,prefix.length()-1))) {
      throw bad("PATH_OUTSIDE_ROUTE");
    }
    boolean strip=((Number)route.get("strip_prefix")).intValue()>0;
    String relative=strip?path.substring(prefix.length()-1):path;
    String pattern=(String)route.get("rewrite_pattern");
    String replacement=(String)route.get("rewrite_replacement");
    String upstream=pattern==null?relative:relative.replaceFirst(
        java.util.regex.Pattern.quote(pattern.substring(1)),
        java.util.regex.Matcher.quoteReplacement(replacement));
    RoutePathPolicy.path(upstream);
    return new PreviewResponse(path,upstream,(UUID)route.get("id"));
  }

  private UUID resource(String key,String action) {
    return routes.resourceAction(limit(key,500),code(action))
        .orElseThrow(()->bad("INVALID_RESOURCE_ACTION"));
  }
  private URI validateGateway(String value) {
    URI uri;
    try { uri=URI.create(value); }
    catch(Exception failure) { throw bad("INVALID_GATEWAY_URL"); }
    String host=uri.getHost()==null?"":uri.getHost().toLowerCase(Locale.ROOT);
    if(!Set.of("http","https").contains(uri.getScheme())||uri.getUserInfo()!=null
        ||uri.getQuery()!=null||uri.getFragment()!=null||!approvedGatewayHosts.contains(host)
        ||isForbiddenHost(host)) throw bad("UNAPPROVED_GATEWAY_HOST");
    return uri;
  }
  private static boolean isForbiddenHost(String host) {
    return host.equals("localhost")||host.equals("0.0.0.0")||host.equals("169.254.169.254")
        ||host.startsWith("127.")||host.startsWith("169.254.")||host.equals("::1");
  }
  private static void validateRewrite(String pattern,String replacement) {
    if((pattern==null||pattern.isBlank())!=(replacement==null||replacement.isBlank())) {
      throw bad("REWRITE_PAIR_REQUIRED");
    }
    if(pattern==null||pattern.isBlank()) return;
    if(!pattern.startsWith("^/")||pattern.substring(1).contains(".")||pattern.contains("*")
        ||pattern.contains("[")||pattern.contains("(")||replacement.contains("://")) {
      throw bad("UNSAFE_REWRITE");
    }
    RoutePathPolicy.path(pattern.substring(1));RoutePathPolicy.path(replacement);
  }
  private Map<String,Object> operation(UUID id) {
    return routes.operation(id).orElseThrow(ProxyRouteAdministrationService::notFound);
  }
  private String method(String value) {
    String result=code(value);
    if(!METHODS.contains(result)) throw bad("INVALID_HTTP_METHOD");
    return result;
  }
  private static String code(String value) {
    String result=limit(value,160).trim();
    if(!result.matches("[A-Za-z][A-Za-z0-9._-]*")) throw bad("INVALID_CODE");
    return result;
  }
  private static String serviceSlug(String value) {
    String result=limit(value,80).trim();
    if(!result.matches("[a-z][a-z0-9-]{1,49}")) throw bad("INVALID_SERVICE_SLUG");
    return result;
  }
  private static String reference(String value) {
    String result=nullable(value);
    if(result.isEmpty()) return result;
    if(!result.matches("(secret|tls)://[A-Za-z0-9._/-]+")) {
      throw bad("INVALID_SECRET_REFERENCE");
    }
    return result;
  }
  private static String nullable(String value) { return value==null?"":limit(value,1000).trim(); }
  private static String limit(String value,int max) {
    if(value==null||value.length()>max) throw bad("INVALID_FIELD_LENGTH");
    return value;
  }
  private static Map<String,Object> safeTarget(TargetRequest value) {
    return Map.of("code",value.code(),"gatewayHost",URI.create(value.gatewayBaseUrl()).getHost(),
        "environment",value.environment(),"active",value.active());
  }
  private static void conflictVersion() { throw new OptimisticLockingFailureException("VERSION_CONFLICT"); }
  private static ResponseStatusException bad(String message) { return new ResponseStatusException(HttpStatus.BAD_REQUEST,message); }
  private static ResponseStatusException notFound() { return new ResponseStatusException(HttpStatus.NOT_FOUND); }
  private static ResponseStatusException conflict(String message) { return new ResponseStatusException(HttpStatus.CONFLICT,message); }
}

package com.aurevia.authz.routing;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public interface ProxyRouteRepository {
  List<Map<String,Object>> targets(String search);
  Optional<Map<String,Object>> target(UUID id);
  void insertTarget(TargetValue value);
  boolean updateTarget(UUID id,long version,TargetValue value);
  boolean updateTargetStatus(UUID id,long version,boolean active,String actor);
  List<Map<String,Object>> routes(String search,UUID panelId,UUID targetId,Boolean active);
  Optional<Map<String,Object>> route(UUID id);
  void insertRoute(RouteValue value);
  boolean updateRoute(UUID id,long version,RouteValue value);
  boolean updateRouteStatus(UUID id,long version,boolean active,String actor);
  List<Map<String,Object>> operations(UUID routeId);
  Optional<Map<String,Object>> operation(UUID id);
  void insertOperation(OperationValue value);
  boolean updateOperation(UUID id,UUID routeId,long version,OperationValue value);
  boolean disableOperation(UUID id,UUID routeId,long version);
  Optional<UUID> resourceAction(String resourceKey,String actionKey);
  long operationConflict(UUID routeId,String method,String pattern,UUID self);
  Optional<String> panelSlug(UUID panelId);
  boolean targetExists(UUID targetId);
  long routeConflict(String prefix,int priority,UUID self);

  record TargetValue(UUID id,String code,String name,String description,String gatewayBaseUrl,
      String upstreamBasePath,String environment,String tlsProfileRef,String secretRef,
      String healthCheckPath,int connectTimeoutMs,int responseTimeoutMs,long maxResponseSize,
      UUID outboundAuthProfileId,boolean active,String actor) {}
  record RouteValue(UUID id,String code,UUID panelId,UUID serviceTargetId,String serviceSlug,
      String pathPrefix,String normalizedPathPrefix,int stripPrefix,String rewritePattern,
      String rewriteReplacement,int priority,String[] allowedMethods,boolean preserveHost,
      boolean retryEnabled,int maxRetries,boolean active,String actor) {}
  record OperationValue(UUID id,UUID routeId,String httpMethod,String pathPattern,
      UUID resourceId,String resourceKey,String actionKey,boolean authorizationRequired,
      String dataPolicyKey,boolean active,long maxBodyBytes,String actor) {}
}

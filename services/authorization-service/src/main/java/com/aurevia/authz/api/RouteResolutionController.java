package com.aurevia.authz.api;

import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/v1/routes")
public class RouteResolutionController {
  private final JdbcClient database;
  public RouteResolutionController(JdbcClient database) { this.database = database; }

  @GetMapping("/resolve")
  public RouteResolution resolve(@RequestParam String path, @RequestParam String method) {
    if (!path.startsWith("/") || path.contains("..") || path.contains("\\")) {
      throw new InvalidRouteException();
    }
    return database.sql("""
        select pr.route_key as "routeKey", pr.path_prefix as "pathPrefix",
          st.target_key as "targetKey", ro.resource_id as "resourceId",
          r.resource_key as "resourceKey", a.action_key as "actionKey",
          ro.max_body_bytes as "maxBodyBytes", st.connect_timeout_ms as "connectTimeoutMs",
          st.response_timeout_ms as "responseTimeoutMs", st.max_response_bytes as "maxResponseBytes"
        from proxy_route pr join service_target st on st.id=pr.target_id
        join route_operation ro on ro.route_id=pr.id
        join resource r on r.id=ro.resource_id join action a on a.id=ro.action_id
        where pr.active and st.active
          and (:path=pr.path_prefix or :path like pr.path_prefix || '/%')
          and ro.http_method=:method
          and (ro.relative_pattern='/**'
            or substring(:path from length(pr.path_prefix)+1)=ro.relative_pattern
            or substring(:path from length(pr.path_prefix)+1) like replace(ro.relative_pattern,'*','%'))
        order by length(pr.path_prefix) desc limit 1
        """).param("path", path).param("method", method.toUpperCase())
        .query(RouteResolution.class).optional()
        .orElseThrow(RouteNotFoundException::new);
  }

  public record RouteResolution(String routeKey,String pathPrefix,String targetKey,
      java.util.UUID resourceId,String resourceKey,String actionKey,long maxBodyBytes,
      int connectTimeoutMs,int responseTimeoutMs,long maxResponseBytes) {}
  @ResponseStatus(HttpStatus.NOT_FOUND) static class RouteNotFoundException extends RuntimeException {}
  @ResponseStatus(HttpStatus.BAD_REQUEST) static class InvalidRouteException extends RuntimeException {}
}

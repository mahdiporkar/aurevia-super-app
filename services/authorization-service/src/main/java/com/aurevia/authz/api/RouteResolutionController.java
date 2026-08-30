package com.aurevia.authz.api;

import com.aurevia.authz.routing.RoutePathPolicy;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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
    final String canonical;
    try { canonical=RoutePathPolicy.path(path); } catch(IllegalArgumentException failure) { throw new InvalidRouteException(); }
    String verb=method.toUpperCase();
    List<Candidate> rows=database.sql("""
        select pr.id as "routeId",pr.code as "routeKey",pr.path_prefix as "pathPrefix",
          pr.normalized_path_prefix as "normalizedPrefix",pr.strip_prefix as "stripPrefix",
          pr.rewrite_pattern as "rewritePattern",pr.rewrite_replacement as "rewriteReplacement",
          pr.priority,array_to_string(pr.allowed_methods,',') as "allowedMethods",pr.retry_enabled as "retryEnabled",
          pr.max_retries as "maxRetries",p.id as "panelId",p.slug as "panelSlug",
          st.id as "targetId",st.code as "targetKey",st.tls_profile_ref as "tlsProfileRef",
          ro.id as "operationId",ro.normalized_path_pattern as "pathPattern",
          ro.resource_id as "resourceId",ro.resource_key as "resourceKey",ro.action_key as "actionKey",
          ro.authorization_required as "authorizationRequired",ro.data_policy_key as "dataPolicyKey",
          ro.max_body_bytes as "maxBodyBytes",st.connect_timeout_ms as "connectTimeoutMs",
          st.response_timeout_ms as "responseTimeoutMs",st.max_response_size as "maxResponseBytes"
          ,ap.id as "authProfileId",ap.auth_mode as "authMode",ap.version as "authProfileVersion",
          ap.credential_transport as "credentialTransport"
        from proxy_route pr join panel p on p.id=pr.panel_id
        join service_target st on st.id=pr.service_target_id
        join outbound_auth_profile ap on ap.id=st.outbound_auth_profile_id
        join route_operation ro on ro.proxy_route_id=pr.id
        where p.active and pr.active and st.active and ap.active and ro.active and ro.http_method=:method
        """).param("method",verb).query(Candidate.class).list();
    List<Candidate> matched=rows.stream().filter(r -> prefixMatch(canonical,r.normalizedPrefix()))
        .filter(r -> java.util.Arrays.asList(r.allowedMethods().split(",")).contains(verb))
        .filter(r -> RoutePathPolicy.matches(r.pathPattern(),relative(canonical,r.normalizedPrefix())))
        .sorted(Comparator.comparingInt((Candidate r)->r.normalizedPrefix().length()).reversed()
          .thenComparing(Comparator.comparingInt(Candidate::priority).reversed())
          .thenComparing(Comparator.comparingInt((Candidate r)->RoutePathPolicy.specificity(r.pathPattern())).reversed()))
        .toList();
    if(matched.isEmpty()) throw new RouteNotFoundException();
    Candidate selected=matched.get(0);
    if(matched.size()>1) {
      Candidate second=matched.get(1);
      if(selected.normalizedPrefix().length()==second.normalizedPrefix().length()
          && selected.priority()==second.priority()
          && RoutePathPolicy.specificity(selected.pathPattern())==RoutePathPolicy.specificity(second.pathPattern()))
        throw new AmbiguousRouteException();
    }
    return new RouteResolution(selected.routeId(),selected.operationId(),selected.panelId(),selected.panelSlug(),
        selected.routeKey(),selected.pathPrefix(),selected.targetId(),selected.targetKey(),selected.stripPrefix(),
        selected.rewritePattern(),selected.rewriteReplacement(),selected.resourceId(),selected.resourceKey(),
        selected.actionKey(),selected.authorizationRequired(),selected.dataPolicyKey(),selected.maxBodyBytes(),
        selected.connectTimeoutMs(),selected.responseTimeoutMs(),selected.maxResponseBytes(),selected.retryEnabled(),
        selected.maxRetries(),selected.tlsProfileRef(),selected.authProfileId(),selected.authMode(),
        selected.authProfileVersion(),selected.credentialTransport());
  }

  private static boolean prefixMatch(String path,String prefix){String bare=prefix.equals("/")?"/":prefix.substring(0,prefix.length()-1);return path.equals(bare)||path.startsWith(prefix);}
  private static String relative(String path,String prefix){String bare=prefix.equals("/")?"":prefix.substring(0,prefix.length()-1);String result=path.substring(bare.length());return result.isEmpty()?"/":result;}
  private record Candidate(UUID routeId,String routeKey,String pathPrefix,String normalizedPrefix,int stripPrefix,
      String rewritePattern,String rewriteReplacement,int priority,String allowedMethods,boolean retryEnabled,
      int maxRetries,UUID panelId,String panelSlug,UUID targetId,String targetKey,String tlsProfileRef,
      UUID operationId,String pathPattern,UUID resourceId,String resourceKey,String actionKey,
      boolean authorizationRequired,String dataPolicyKey,long maxBodyBytes,int connectTimeoutMs,
      int responseTimeoutMs,long maxResponseBytes,UUID authProfileId,String authMode,
      long authProfileVersion,String credentialTransport) {}
  public record RouteResolution(UUID routeId,UUID operationId,UUID panelId,String panelSlug,String routeKey,
      String pathPrefix,UUID targetId,String targetKey,int stripPrefix,String rewritePattern,
      String rewriteReplacement,UUID resourceId,String resourceKey,String actionKey,
      boolean authorizationRequired,String dataPolicyKey,long maxBodyBytes,int connectTimeoutMs,
      int responseTimeoutMs,long maxResponseBytes,boolean retryEnabled,int maxRetries,String tlsProfileRef,
      UUID authProfileId,String authMode,long authProfileVersion,String credentialTransport) {}
  @ResponseStatus(HttpStatus.NOT_FOUND) static class RouteNotFoundException extends RuntimeException {}
  @ResponseStatus(HttpStatus.BAD_REQUEST) static class InvalidRouteException extends RuntimeException {}
  @ResponseStatus(HttpStatus.CONFLICT) static class AmbiguousRouteException extends RuntimeException {}
}

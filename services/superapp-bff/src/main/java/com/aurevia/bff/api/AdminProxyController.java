package com.aurevia.bff.api;

import java.security.Principal;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;
import com.aurevia.bff.outboundauth.LegacyTokenManager;

@RestController
public class AdminProxyController {
  private final WebClient authorizationClient;
  private final WebClient operationGateway;
  private final LegacyTokenManager legacyTokens;

  public AdminProxyController(
      @Qualifier("authorizationWebClient") WebClient authorizationClient,
      @Qualifier("operationGatewayClient") WebClient operationGateway,LegacyTokenManager legacyTokens) {
    this.authorizationClient = authorizationClient;
    this.operationGateway = operationGateway;
    this.legacyTokens=legacyTokens;
  }

  @PostMapping("/api/v1/admin/outbound-auth-profiles/{id}/token-test")
  public Mono<ResponseEntity<Map<String,Object>>> tokenTest(@PathVariable String id,Principal principal){long start=System.nanoTime();return requireProfilePermission(principal,"manage").then(legacyTokens.test(id)).thenReturn(ResponseEntity.ok(result(true,(System.nanoTime()-start)/1_000_000,"TOKEN_ACQUIRED"))).onErrorResume(ResponseStatusException.class,error->Mono.just(ResponseEntity.status(error.getStatusCode()).body(result(false,(System.nanoTime()-start)/1_000_000,"ACCESS_DENIED")))).onErrorReturn(ResponseEntity.status(502).body(result(false,(System.nanoTime()-start)/1_000_000,"TOKEN_ACQUISITION_FAILED")));}
  @PostMapping("/api/v1/admin/outbound-auth-profiles/{id}/connection-test")
  public Mono<ResponseEntity<Map<String,Object>>> connectionTest(@PathVariable String id,Principal principal){return tokenTest(id,principal);}
  @PostMapping("/api/v1/admin/outbound-auth-profiles/{id}/invalidate-token")
  public Mono<ResponseEntity<Map<String,Object>>> invalidate(@PathVariable String id,Principal principal){return requireProfilePermission(principal,"manage").then(legacyTokens.invalidate(id)).thenReturn(ResponseEntity.ok(Map.of("success",true,"code","CACHE_INVALIDATED")));}
  @GetMapping("/api/v1/admin/outbound-auth-profiles/{id}/cache-status")
  public Mono<ResponseEntity<Map<String,Object>>> cacheStatus(@PathVariable String id,Principal principal){return requireProfilePermission(principal,"view").then(legacyTokens.cacheStatus(id)).map(cached->ResponseEntity.ok(Map.of("cached",cached)));}
  private Mono<Void> requireProfilePermission(Principal principal,String action){
    if(principal==null)return Mono.error(new ResponseStatusException(HttpStatus.UNAUTHORIZED));
    Map<String,Object> check=Map.of("subjectId",principal.getName(),"issuer","public-iam",
        "resource","resource:integration.auth-profile","action",action,"context",Map.of(),
        "correlationId",UUID.randomUUID().toString());
    return authorizationClient.post().uri("/internal/v1/authorize/check")
        .contentType(MediaType.APPLICATION_JSON).bodyValue(check).retrieve().bodyToMono(Map.class)
        .filter(decision->"ALLOW".equals(decision.get("result")))
        .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.FORBIDDEN))).then();
  }
  private static Map<String,Object> result(boolean success,long latency,String code){return Map.of("success",success,"latencyMs",latency,"code",code);}

  /** Connectivity is tested server-side against the fixed approved Gateway client. */
  @PostMapping("/api/v1/admin/service-targets/{id}/health-check")
  public Mono<ResponseEntity<Map<String,Object>>> health(@PathVariable UUID id,Principal principal) {
    long started=System.nanoTime();
    return authorizationClient.get().uri("/internal/v1/registry/service-targets/{id}",id)
        .header("X-Actor",principal.getName()).retrieve().bodyToMono(Map.class)
        .flatMap(target -> operationGateway.get().uri(String.valueOf(target.get("health_check_path")))
            .exchangeToMono(response -> Mono.just(ResponseEntity.ok(Map.of(
                "healthy",response.statusCode().is2xxSuccessful(),"status",response.statusCode().value(),
                "latencyMs",(System.nanoTime()-started)/1_000_000,"checkedTarget",target.get("code"))))))
        .timeout(Duration.ofSeconds(10));
  }

  @RequestMapping("/api/v1/admin/{*path}")
  public Mono<ResponseEntity<byte[]>> proxy(
      @PathVariable("path") String path,
      @RequestBody(required = false) Mono<byte[]> requestBody,
      ServerWebExchange exchange,
      Principal principal) {
    String query = exchange.getRequest().getURI().getRawQuery();
    String targetUri = "/internal/v1/registry" + path
        + (query == null ? "" : "?" + query);

    return authorizationClient
        .method(exchange.getRequest().getMethod())
        .uri(targetUri)
        .contentType(MediaType.APPLICATION_JSON)
        .header("X-Actor", principal.getName())
        .body(requestBody, byte[].class)
        .exchangeToMono(response -> response.bodyToMono(byte[].class)
            .defaultIfEmpty(new byte[0])
            .map(bytes -> ResponseEntity
                .status(response.statusCode())
                .contentType(MediaType.APPLICATION_JSON)
                .body(bytes)))
        .flatMap(entity -> entity.getStatusCode().is2xxSuccessful() && mutatesProfile(path,exchange)
            ? legacyTokens.invalidate(profileId(path)).thenReturn(entity) : Mono.just(entity));
  }
  private static boolean mutatesProfile(String path,ServerWebExchange exchange){return path.matches("/outbound-auth-profiles/[0-9a-fA-F-]+(?:/status)?")&&Set.of("PUT","PATCH").contains(exchange.getRequest().getMethod().name());}
  private static String profileId(String path){return path.split("/")[2];}
}

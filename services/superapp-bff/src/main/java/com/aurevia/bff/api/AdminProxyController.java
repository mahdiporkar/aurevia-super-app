package com.aurevia.bff.api;

import java.security.Principal;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@RestController
public class AdminProxyController {
  private final WebClient authorizationClient;
  private final WebClient operationGateway;

  public AdminProxyController(
      @Qualifier("authorizationWebClient") WebClient authorizationClient,
      @Qualifier("operationGatewayClient") WebClient operationGateway) {
    this.authorizationClient = authorizationClient;
    this.operationGateway = operationGateway;
  }

  /** Connectivity is tested server-side against the fixed approved Gateway client. */
  @RequestMapping("/api/v1/admin/service-targets/{id}/health-check")
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
                .body(bytes)));
  }
}

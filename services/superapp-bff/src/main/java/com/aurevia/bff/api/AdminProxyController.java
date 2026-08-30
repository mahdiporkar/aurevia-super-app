package com.aurevia.bff.api;

import java.security.Principal;
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

  public AdminProxyController(
      @Qualifier("authorizationWebClient") WebClient authorizationClient) {
    this.authorizationClient = authorizationClient;
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

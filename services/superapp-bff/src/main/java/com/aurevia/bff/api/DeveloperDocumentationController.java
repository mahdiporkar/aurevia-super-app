package com.aurevia.bff.api;

import com.aurevia.bff.security.SessionIdentity;
import io.swagger.v3.oas.annotations.Hidden;
import java.security.Principal;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

/**
 * Development-only façade used by the aggregated Swagger UI.
 * Internal Basic/mTLS credentials remain in the configured server-side WebClient.
 */
@Hidden
@Profile("!prod")
@RestController
public class DeveloperDocumentationController {
  private final WebClient authorization;

  public DeveloperDocumentationController(
      @Qualifier("authorizationWebClient") WebClient authorization) {
    this.authorization = authorization;
  }

  @GetMapping("/api/v1/docs/authorization/openapi")
  Mono<ResponseEntity<byte[]>> specification() {
    return authorization.get().uri("/v3/api-docs").accept(MediaType.APPLICATION_JSON)
        .exchangeToMono(response -> response.bodyToMono(byte[].class).defaultIfEmpty(new byte[0])
            .map(body -> ResponseEntity.status(response.statusCode())
                .contentType(MediaType.APPLICATION_JSON).body(body)));
  }

  @RequestMapping("/api/v1/docs/authorization/execute/{*path}")
  Mono<ResponseEntity<byte[]>> execute(@PathVariable("path") String path,
      @RequestBody(required = false) Mono<byte[]> requestBody, ServerWebExchange exchange,
      Principal principal) {
    SessionIdentity identity = SessionIdentity.from(principal);
    if (!path.startsWith("/internal/v1/")) {
      return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST,
          "Only /internal/v1 authorization-service paths are available"));
    }
    String query = exchange.getRequest().getURI().getRawQuery();
    String target = path + (query == null ? "" : "?" + query);
    WebClient.RequestBodySpec request = authorization.method(exchange.getRequest().getMethod())
        .uri(target)
        .headers(headers -> {
          headers.set("X-Actor", identity.username());
          headers.set("X-Actor-Subject", identity.subject());
          headers.set("X-Actor-Issuer", identity.issuer());
          copyHeader(exchange.getRequest().getHeaders(), headers, HttpHeaders.CONTENT_TYPE);
          copyHeader(exchange.getRequest().getHeaders(), headers, HttpHeaders.ACCEPT);
          copyHeader(exchange.getRequest().getHeaders(), headers, "X-Correlation-ID");
        });
    return requireSwaggerPermission(identity).then(request
        .body(requestBody.defaultIfEmpty(new byte[0]), byte[].class)
        .exchangeToMono(response -> response.bodyToMono(byte[].class).defaultIfEmpty(new byte[0])
            .map(body -> {
              var builder = ResponseEntity.status(response.statusCode());
              MediaType contentType = response.headers().contentType().orElse(MediaType.APPLICATION_JSON);
              return builder.contentType(contentType).body(body);
            })));
  }

  private Mono<Void> requireSwaggerPermission(SessionIdentity identity) {
    Map<String, Object> check = Map.of(
        "subjectId", identity.subject(), "issuer", identity.issuer(),
        "resource", "application:aurevia/admin", "action", "manage",
        "context", Map.of("channel", "swagger"),
        "correlationId", UUID.randomUUID().toString());
    return authorization.post().uri("/internal/v1/authorize/check")
        .contentType(MediaType.APPLICATION_JSON).bodyValue(check).retrieve().bodyToMono(Map.class)
        .filter(decision -> "ALLOW".equals(decision.get("result")))
        .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.FORBIDDEN,
            "Swagger execution requires manage access to the admin application")))
        .then();
  }

  private static void copyHeader(HttpHeaders source, HttpHeaders target, String name) {
    String value = source.getFirst(name);
    if (value != null && !value.isBlank()) target.set(name, value);
  }
}

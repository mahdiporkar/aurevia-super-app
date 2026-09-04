package com.aurevia.bff.api;

import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

/** Entry point for all BFF-to-Authorization-Service calls. */
@Component
public class AuthorizationServiceClient {
  private final WebClient client;

  public AuthorizationServiceClient(
      @Qualifier("authorizationWebClient") WebClient client) {
    this.client = client;
  }

  Mono<Map> manifest(String issuer, String subject) {
    return client.get()
        .uri(builder -> builder.path("/internal/v1/subjects/{id}/manifest")
            .queryParam("issuer", issuer).build(subject))
        .accept(MediaType.APPLICATION_JSON)
        .retrieve()
        .bodyToMono(Map.class);
  }

  public Mono<Void> syncLogin(Map<String, Object> identity) {
    return client.post().uri("/internal/v1/identity/login-sync")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(identity)
        .retrieve()
        .bodyToMono(Void.class);
  }

  public Mono<RouteResolution> resolveRoute(String path, String method) {
    return client.get()
        .uri(builder -> builder.path("/internal/v1/routes/resolve")
            .queryParam("path", path).queryParam("method", method).build())
        .retrieve()
        .bodyToMono(RouteResolution.class);
  }

  public Mono<Map> outboundAuthProfile(String id) {
    return client.get().uri("/internal/v1/outbound-auth-profiles/{id}", id)
        .retrieve().bodyToMono(Map.class);
  }

  public Mono<Map> outboundConnection(String reference) {
    return client.get()
        .uri(builder -> builder.path("/internal/v1/outbound-connections/resolve")
            .queryParam("ref", reference).build())
        .retrieve().bodyToMono(Map.class);
  }

  public Mono<Map> check(Map<String, Object> request) {
    return client.post().uri("/internal/v1/authorize/check")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(request)
        .retrieve().bodyToMono(Map.class);
  }

  public Mono<Map> supersetAccess(String issuer, String subject, String instanceCode,
      String path, String method, String query, String assetType, String assetId) {
    return client.get()
        .uri(builder -> builder.path("/internal/v1/registry/subjects/{subject}/superset-access")
            .queryParam("issuer", issuer)
            .queryParam("instance", instanceCode)
            .queryParam("path", path)
            .queryParam("method", method)
            .queryParam("query", query == null ? "" : query)
            .queryParam("assetType", assetType == null ? "" : assetType)
            .queryParam("assetId", assetId == null ? "" : assetId)
            .build(subject))
        .retrieve().bodyToMono(Map.class);
  }

  public Mono<Map> resolveSupersetProxy(String publicInstance) {
    return client.get()
        .uri(builder -> builder.path("/internal/v1/superset-proxy/resolve")
            .queryParam("publicInstance", publicInstance).build())
        .retrieve().bodyToMono(Map.class);
  }

  public Mono<List<Map>> supersetAssets(String issuer, String subject,
      String instanceCode) {
    return client.get()
        .uri(builder -> builder.path("/internal/v1/registry/subjects/{subject}/superset-assets")
            .queryParam("issuer", issuer)
            .queryParam("instance", instanceCode)
            .build(subject))
        .retrieve().bodyToFlux(Map.class).collectList();
  }

  public Mono<Void> ingestApiLog(Map<String, Object> entry) {
    return client.post().uri("/internal/v1/logging/api")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(entry)
        .retrieve().bodyToMono(Void.class);
  }
}

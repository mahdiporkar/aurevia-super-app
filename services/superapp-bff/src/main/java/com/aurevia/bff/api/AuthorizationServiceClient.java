package com.aurevia.bff.api;

import java.util.List;
import java.util.Map;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.core.ParameterizedTypeReference;
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

  Mono<Map<String,Object>> manifest(String issuer, String subject) {
    return client.get()
        .uri(builder -> builder.path("/internal/v1/subjects/{id}/manifest")
            .queryParam("issuer", issuer).build(subject))
        .accept(MediaType.APPLICATION_JSON)
        .retrieve()
        .bodyToMono(new ParameterizedTypeReference<>() {});
  }

  public Mono<Void> syncLogin(Map<String, Object> identity) {
    return client.post().uri("/internal/v1/identity/login-sync")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(identity)
        .retrieve()
        .bodyToMono(Void.class);
  }

  public Mono<IdentityProviderRuntime> identityProvider(String code) {
    return client.get().uri("/internal/v1/identity-providers/{code}",code).retrieve()
        .bodyToMono(IdentityProviderRuntime.class);
  }

  public Mono<List<IdentityProviderSummary>> identityProviders(String tenant,String domain) {
    return client.get().uri(builder->{var uri=builder.path("/internal/v1/identity-providers");
      if(tenant!=null&&!tenant.isBlank())uri.queryParam("tenant",tenant);
      if(domain!=null&&!domain.isBlank())uri.queryParam("domain",domain);return uri.build();})
        .retrieve().bodyToFlux(IdentityProviderSummary.class).collectList();
  }

  public Mono<IdentityProviderSummary> routeIdentityProvider(String code,String tenant,String domain) {
    return client.get().uri(builder->{var uri=builder.path("/internal/v1/identity-providers/route");
      if(code!=null&&!code.isBlank())uri.queryParam("code",code);
      if(tenant!=null&&!tenant.isBlank())uri.queryParam("tenant",tenant);
      if(domain!=null&&!domain.isBlank())uri.queryParam("domain",domain);return uri.build();})
        .retrieve().bodyToMono(IdentityProviderSummary.class);
  }

  @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
  public record IdentityProviderRuntime(String code,String name,String issuerUrl,
      String authorizationEndpoint,String tokenEndpoint,String jwksUri,String userInfoEndpoint,
      String clientId,String clientSecretReference,List<String> scopes,List<String> audiences,
      String subjectClaim,String usernameClaim,String groupsClaim) {}
  @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
  public record IdentityProviderSummary(String code,String name,String type,String issuerUrl,
      String tenantId,List<String> domains,String connectionStatus) {}

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
    return supersetAccess(issuer,subject,instanceCode,instanceCode,path,method,query,
        assetType,assetId);
  }

  public Mono<Map> supersetAccess(String issuer,String subject,String integrationCode,
      String instanceCode,String path,String method,String query,String assetType,String assetId) {
    return client.get()
        .uri(builder -> builder.path("/internal/v1/registry/subjects/{subject}/superset-access")
            .queryParam("issuer", issuer)
            .queryParam("integration",integrationCode)
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
        .uri(builder -> {
          var target=builder.path("/internal/v1/superset-proxy/resolve");
          if(publicInstance!=null&&!publicInstance.isBlank()) {
            target.queryParam("publicInstance",publicInstance);
          }
          return target.build();
        })
        .retrieve().bodyToMono(Map.class);
  }

  public Mono<Map> resolveSupersetIntegration(String instanceCode) {
    return client.get().uri(builder->builder.path("/internal/v1/superset-proxy/resolve-integration")
        .queryParam("instance",instanceCode).build()).retrieve().bodyToMono(Map.class);
  }

  public Mono<List<Map>> supersetIntegrations(String issuer,String subject) {
    return client.get().uri(builder->builder
        .path("/internal/v1/superset-proxy/subjects/{subject}/integrations")
        .queryParam("issuer",issuer).build(subject))
        .retrieve().bodyToFlux(Map.class).collectList();
  }

  public Mono<Void> recordSupersetHealth(String instanceCode,String status) {
    return client.post().uri(builder->builder.path("/internal/v1/superset-proxy/{code}/health")
        .queryParam("status",status).build(instanceCode)).retrieve().bodyToMono(Void.class);
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

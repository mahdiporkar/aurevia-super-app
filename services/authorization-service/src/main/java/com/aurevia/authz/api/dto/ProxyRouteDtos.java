package com.aurevia.authz.api.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;
import java.util.UUID;

public final class ProxyRouteDtos {
  private ProxyRouteDtos() {}
  public record TargetRequest(@NotBlank String code,@NotBlank String name,String description,
      @NotBlank String gatewayBaseUrl,@NotBlank String upstreamBasePath,
      @NotBlank String environment,String tlsProfileRef,String secretRef,
      @NotBlank String healthCheckPath,@Min(100) @Max(30000) int connectTimeoutMs,
      @Min(100) @Max(120000) int responseTimeoutMs,
      @Min(1024) @Max(104857600) long maxResponseSize,UUID outboundAuthProfileId,
      boolean active) {}
  public record RouteRequest(@NotBlank String code,UUID panelId,UUID serviceTargetId,
      @NotBlank String serviceSlug,@NotBlank String pathPrefix,
      @Min(0) @Max(20) int stripPrefix,String rewritePattern,String rewriteReplacement,
      @Min(-1000) @Max(1000) int priority,@NotEmpty List<String> allowedMethods,
      boolean preserveHost,boolean retryEnabled,@Min(0) @Max(3) int maxRetries,
      boolean active) {}
  public record OperationRequest(@NotBlank String httpMethod,@NotBlank String pathPattern,
      @NotBlank String resourceKey,@NotBlank String actionKey,
      boolean authorizationRequired,String dataPolicyKey,boolean active,
      @Min(0) @Max(104857600) long maxBodyBytes) {}
  public record StatusRequest(boolean active) {}
  public record PreviewRequest(UUID routeId,@NotBlank String path) {}
  public record MatchRequest(@NotBlank String method,@NotBlank String path) {}
  public record ValidationResponse(boolean valid,String normalizedPathPrefix) {}
  public record PreviewResponse(String incomingPath,String upstreamPath,UUID routeId) {}
}

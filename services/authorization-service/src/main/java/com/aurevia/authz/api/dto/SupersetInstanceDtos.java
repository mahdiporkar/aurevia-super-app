package com.aurevia.authz.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public final class SupersetInstanceDtos {
  private SupersetInstanceDtos() {}

  public record InstanceRequest(@NotBlank String code,@NotBlank String name,
      @NotBlank @JsonAlias("type") String zone,@NotBlank String baseUrl,String connectionRef,
      @NotBlank String authMode,boolean tlsRequired,@JsonAlias("enabled") boolean active,
      Boolean proxyMode,Map<String,Object> metadata,long version) {}
  public record MappingRequest(@NotNull UUID publicInstanceId,
      @NotNull UUID operationInstanceId,@NotBlank String publicPath,
      boolean isDefault,boolean active) {}
  public record InstanceView(UUID id,String code,String name,String zone,String type,
      @JsonProperty("base_url") String baseUrl,
      @JsonProperty("connection_ref") String connectionRef,
      @JsonProperty("auth_mode") String authMode,
      @JsonProperty("tls_required") boolean tlsRequired,boolean active,boolean enabled,
      @JsonProperty("proxy_mode") boolean proxyMode,
      @JsonProperty("health_status") String healthStatus,
      @JsonProperty("health_checked_at") Instant healthCheckedAt,
      Map<String,Object> metadata,long version,
      @JsonProperty("created_at") Instant createdAt,
      @JsonProperty("updated_at") Instant updatedAt) {}
  public record IntegrationView(String key,String name,String type,String url,
      @JsonProperty("auth_mode") String authMode,
      @JsonProperty("proxy_mode") boolean proxyMode,
      @JsonProperty("health_status") String healthStatus) {}
  public record MappingView(UUID id,
      @JsonProperty("public_instance_id") UUID publicInstanceId,
      @JsonProperty("public_code") String publicCode,
      @JsonProperty("public_name") String publicName,
      @JsonProperty("operation_instance_id") UUID operationInstanceId,
      @JsonProperty("operation_code") String operationCode,
      @JsonProperty("operation_name") String operationName,
      @JsonProperty("public_path") String publicPath,
      @JsonProperty("is_default") boolean isDefault,boolean active,long version) {}
  public record VersionResponse(UUID id,long version) {}
  public record IdResponse(UUID id) {}
}

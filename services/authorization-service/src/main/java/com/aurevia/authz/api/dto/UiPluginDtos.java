package com.aurevia.authz.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import java.time.Instant;
import java.util.UUID;

public final class UiPluginDtos {
  private UiPluginDtos() {}

  public record ArtifactRequest(@NotBlank String artifactVersion,
      @NotBlank String remoteEntryUrl,@NotBlank String remoteName,
      @NotBlank String exposedModule,@NotBlank String contractVersion,
      String integrity,@NotBlank String manifest) {}

  public record MenuOverrideRequest(String title,String icon,Integer order,boolean hidden) {}

  public record ArtifactView(UUID id,
      @JsonProperty("panel_id") UUID panelId,
      @JsonProperty("artifact_version") String artifactVersion,
      @JsonProperty("remote_entry_url") String remoteEntryUrl,
      @JsonProperty("remote_name") String remoteName,
      @JsonProperty("exposed_module") String exposedModule,
      @JsonProperty("contract_version") String contractVersion,
      @JsonProperty("schema_version") String schemaVersion,
      String integrity,
      @JsonProperty("manifest_snapshot") String manifestSnapshot,
      @JsonProperty("validation_status") String validationStatus,
      @JsonProperty("validation_error") String validationError,
      boolean immutable,
      @JsonProperty("created_at") Instant createdAt,
      @JsonProperty("created_by") String createdBy,
      boolean active,
      @JsonProperty("panel_version") long panelVersion) {}

  public record ArtifactPublishedResponse(UUID id,String validationStatus) {}
  public record ArtifactActivatedResponse(UUID activeArtifactId,long version) {}
  public record MenuOverrideResponse(String menuId) {}
}

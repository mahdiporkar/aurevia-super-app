package com.aurevia.authz.api.dto;

import static com.aurevia.authz.superset.SupersetAssetModels.*;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public final class SupersetAssetDtos {
  private SupersetAssetDtos() {}
  public record AssetRequest(@NotBlank String externalId, @NotBlank String assetType,
      @NotBlank String title, @NotBlank String urlPath, String ownerExternalId,
      boolean published, String instanceCode) {
    public AssetCommand toCommand() {
      return new AssetCommand(externalId, assetType, title, urlPath, ownerExternalId, published,
          instanceCode);
    }
  }
  public record AssetGrantRequest(@NotBlank String subjectType, @NotNull UUID subjectId,
      @NotBlank String level) {}
}

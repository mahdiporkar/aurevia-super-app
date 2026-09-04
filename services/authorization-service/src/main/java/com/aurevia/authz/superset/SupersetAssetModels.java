package com.aurevia.authz.superset;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class SupersetAssetModels {
  private SupersetAssetModels() {}

  @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
  public record AssetView(UUID id, UUID resourceId, UUID instanceId, String instanceCode,
      String externalId, String assetType, String title, String urlPath, String ownerExternalId,
      boolean published, List<String> tags, String resourceKey, String nameFa, String nameEn) {}

  @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
  public record AssetGrantView(UUID id, String subjectType, UUID subjectId, String subjectName,
      String subjectKey, String actionKey, String relation, Instant expiresAt) {}

  public record SubjectOption(UUID id, String type, String key, String label) {}
  public record LevelOption(String level, String actionKey, String label) {}
  public record AccessOptions(List<SubjectOption> subjects, List<LevelOption> levels) {}
  public record RuntimeAccess(String result, String reasonCode) {}
  public record CreateResult(UUID id, UUID resourceId, String resourceKey, boolean existing) {}
  public record ExistingAsset(UUID id, UUID resourceId, String resourceKey) {}
  public record GrantTarget(UUID resourceId, UUID actionId) {}
  public record OperationInstance(UUID id, String code) {}
  public record AssetCommand(String externalId, String assetType, String title, String urlPath,
      String ownerExternalId, boolean published, String instanceCode) {}
}

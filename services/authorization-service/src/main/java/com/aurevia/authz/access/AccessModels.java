package com.aurevia.authz.access;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Stable application-layer contracts for access-control administration. */
public final class AccessModels {
  private AccessModels() {}

  @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
  public record ResourceView(
      UUID id,
      String resourceKey,
      String type,
      UUID parentId,
      String nameFa,
      String nameEn,
      String ownerDomain,
      String classification,
      String externalSystem,
      String externalType,
      String externalId,
      String source,
      Map<String, Object> metadata,
      String status,
      long version,
      long grantCount,
      List<ActionSummary> actions) {}

  public record ActionSummary(UUID id, String key, String nameFa, String nameEn) {}
  @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
  public record ActionView(UUID id, String actionKey, String nameFa, String nameEn) {}

  @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
  public record UserView(
      UUID id,
      String issuer,
      String externalId,
      String subjectKey,
      String username,
      String displayName,
      String email,
      String status,
      long version,
      long membershipVersion,
      List<String> organizationalUnits) {}

  @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
  public record GrantView(
      UUID id,
      UUID resourceId,
      String resourceKey,
      String resourceNameFa,
      String resourceNameEn,
      UUID actionId,
      String actionKey,
      String actionNameFa,
      String relation,
      Instant expiresAt,
      String status,
      long version) {}

  public record MutationResult(UUID id, long version) {}
  public record GrantResult(UUID id, long version, boolean existing) {}

  public record ResourceCommand(
      String resourceKey,
      String type,
      UUID parentId,
      String nameFa,
      String nameEn,
      String ownerDomain,
      String classification,
      String externalSystem,
      String externalType,
      String externalId,
      String source,
      Map<String, Object> metadata) {}

  public record ActionCommand(String actionKey, String nameFa, String nameEn) {}
  public record UserCommand(
      String issuer,
      String externalId,
      String username,
      String displayName,
      String email) {}
  public record GrantCommand(
      UUID userId,
      String subjectType,
      UUID subjectId,
      UUID resourceId,
      UUID actionId,
      Instant expiresAt) {}

  public record ResourceSnapshot(String resourceKey, UUID parentId) {}
  public record GrantTarget(String resourceType, String actionKey) {}
  public record ExistingGrant(UUID id, long version) {}
}

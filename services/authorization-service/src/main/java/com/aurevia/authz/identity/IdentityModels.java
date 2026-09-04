package com.aurevia.authz.identity;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.time.Instant;
import java.util.UUID;

public final class IdentityModels {
  private IdentityModels() {}

  @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
  public record DirectoryGroupView(UUID id, String issuer, String externalId, String normalizedPath,
      String displayName, UUID parentId, String status, Instant syncAt, long version) {}

  @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
  public record AccessGroupView(UUID id, String code, String name, String description,
      String groupType, String ruleCombiner, boolean active, long version) {}

  @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
  public record RoleView(UUID id, String roleKey, String nameFa, String nameEn, String status,
      long version) {}

  @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
  public record RoleAssignmentView(String subjectType, UUID subjectId, String subjectName,
      UUID roleId, String roleKey, Instant expiresAt, String assignedBy, Instant assignedAt) {}

  public record RoleCommand(String roleKey, String nameFa, String nameEn) {}
  public record RoleAssignmentCommand(String subjectType, UUID subjectId, UUID roleId,
      Instant expiresAt) {}
  public record MutationResult(UUID id, long version) {}
  public record RoleSnapshot(String roleKey, long version) {}
}

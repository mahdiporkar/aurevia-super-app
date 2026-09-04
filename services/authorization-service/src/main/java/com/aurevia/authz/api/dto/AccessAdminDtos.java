package com.aurevia.authz.api.dto;

import static com.aurevia.authz.access.AccessModels.*;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public final class AccessAdminDtos {
  private AccessAdminDtos() {}

  public record ResourceRequest(@NotBlank String resourceKey, @NotBlank String type, UUID parentId,
      @NotBlank String nameFa, @NotBlank String nameEn, String ownerDomain, String classification,
      String externalSystem, String externalType, String externalId, String source,
      Map<String, Object> metadata) {
    public ResourceCommand toCommand() {
      return new ResourceCommand(resourceKey, type, parentId, nameFa, nameEn, ownerDomain,
          classification, externalSystem, externalType, externalId, source, metadata);
    }
  }
  public record ActionRequest(@NotBlank String actionKey, @NotBlank String nameFa,
      @NotBlank String nameEn) {
    public ActionCommand toCommand() { return new ActionCommand(actionKey, nameFa, nameEn); }
  }
  public record UserRequest(@NotBlank String issuer, @NotBlank String externalId,
      @NotBlank String username, String displayName, String email) {
    public UserCommand toCommand() {
      return new UserCommand(issuer, externalId, username, displayName, email);
    }
  }
  public record GrantRequest(UUID userId, String subjectType, UUID subjectId,
      @NotNull UUID resourceId, @NotNull UUID actionId, String relation, Instant expiresAt) {
    public GrantCommand toCommand() {
      return new GrantCommand(userId, subjectType, subjectId, resourceId, actionId, expiresAt);
    }
  }
}

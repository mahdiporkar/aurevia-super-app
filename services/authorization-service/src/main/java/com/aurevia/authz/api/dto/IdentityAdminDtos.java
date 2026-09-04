package com.aurevia.authz.api.dto;

import static com.aurevia.authz.identity.IdentityModels.*;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.UUID;

public final class IdentityAdminDtos {
  private IdentityAdminDtos() {}
  public record RoleRequest(@NotBlank String roleKey, @NotBlank String nameFa,
      @NotBlank String nameEn) {
    public RoleCommand toCommand() { return new RoleCommand(roleKey, nameFa, nameEn); }
  }
  public record RoleAssignmentRequest(@NotBlank String subjectType, @NotNull UUID subjectId,
      @NotNull UUID roleId, Instant expiresAt) {
    public RoleAssignmentCommand toCommand() {
      return new RoleAssignmentCommand(subjectType, subjectId, roleId, expiresAt);
    }
  }
  public record StatusRequest(boolean active) {}
}

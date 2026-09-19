package com.aurevia.authz.diagnostics;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Administrator-facing explanation of why a subject can or cannot reach a resource.
 *
 * <p>Every value here is control-plane metadata or an OpenFGA decision. No token, secret, or
 * request payload is represented, so the record is safe to serialize to an administrator.</p>
 */
public final class AuthorizationDiagnostics {
  private AuthorizationDiagnostics() {}

  @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
  public record Explanation(
      String issuer,
      String subject,
      String canonicalSubject,
      String requestedResource,
      String openFgaObject,
      String action,
      String relation,
      String permission,
      boolean allowed,
      String reasonCode,
      ResourceState resource,
      List<GrantExplanation> grants,
      List<MembershipExplanation> memberships,
      List<RoleExplanation> roles,
      PanelState panel) {}

  /** Registry lifecycle of the resource itself. Inactive resources are denied before OpenFGA. */
  @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
  public record ResourceState(
      UUID id,
      String resourceKey,
      String type,
      String status,
      boolean visibilityEnabled,
      boolean actionAttached,
      UUID panelId) {}

  /**
   * A grant that contributes to the decision, including one inherited from an ancestor
   * resource. {@code grantedResourceKey} names the resource the grant is actually attached to.
   */
  @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
  public record GrantExplanation(
      UUID id,
      String subjectType,
      String subjectLabel,
      String grantedResourceKey,
      String actionKey,
      String relation,
      String status,
      Instant expiresAt,
      String projectionStatus,
      String projectionError) {}

  /** A directory or access group membership that can carry a grant to this subject. */
  @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
  public record MembershipExplanation(
      String kind,
      UUID id,
      String externalId,
      String displayName,
      String openFgaObject,
      boolean active) {}

  /** A role reaching this subject directly or through a group. */
  @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
  public record RoleExplanation(
      UUID id,
      String roleKey,
      String status,
      String via,
      String viaLabel,
      Instant expiresAt,
      String openFgaObject) {}

  /** UI artifact lifecycle, so a hidden micro frontend has a stated operational cause. */
  @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
  public record PanelState(
      UUID id,
      String code,
      String slug,
      boolean active,
      String classification,
      UUID activeArtifactId,
      String artifactValidationStatus,
      boolean manifestPresent,
      String discoveryResourceKey,
      String readiness) {}
}

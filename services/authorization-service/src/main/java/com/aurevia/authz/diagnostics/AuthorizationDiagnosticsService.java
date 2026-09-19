package com.aurevia.authz.diagnostics;

import static com.aurevia.authz.diagnostics.AuthorizationDiagnostics.*;

import com.aurevia.authz.identity.CanonicalIdentityResolver;
import com.aurevia.authz.openfga.RelationshipAuthorizationPort;
import com.aurevia.authz.semantics.AuthorizationSemanticsRegistry;
import com.aurevia.authz.semantics.ResourceObjectKey;
import java.util.List;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * Explains why a subject can or cannot reach a resource.
 *
 * <p>This is a read-only administrator tool. It performs the same OpenFGA check the runtime
 * performs and reports the control-plane state around it, so an administrator can tell a missing
 * grant apart from a grant whose projection never reached OpenFGA, a deactivated role, an expired
 * assignment, a disabled resource, or an unusable UI artifact.</p>
 *
 * <p>It deliberately never mutates state and never repairs projection drift: a diagnostic that
 * silently fixed what it measured would hide the very failures it exists to surface.</p>
 */
@Service
public class AuthorizationDiagnosticsService {

  private final AuthorizationDiagnosticsRepository diagnostics;
  private final RelationshipAuthorizationPort relationships;
  private final AuthorizationSemanticsRegistry semantics;
  private final CanonicalIdentityResolver identities;

  public AuthorizationDiagnosticsService(AuthorizationDiagnosticsRepository diagnostics,
      RelationshipAuthorizationPort relationships, AuthorizationSemanticsRegistry semantics,
      CanonicalIdentityResolver identities) {
    this.diagnostics = diagnostics;
    this.relationships = relationships;
    this.semantics = semantics;
    this.identities = identities;
  }

  public Explanation explain(String issuer, String subject, String resource, String action) {
    if (blank(issuer) || blank(subject) || blank(resource) || blank(action)) {
      throw new IllegalArgumentException("issuer, subject, resource, and action are required");
    }
    // The control-plane stores the bare canonical id; OpenFGA addresses the same principal
    // with a "user:" prefix. Mixing the two silently returns an empty explanation.
    String canonicalUserId = identities.canonicalUserId(issuer.trim(), subject.trim());
    String canonicalSubject = "user:" + canonicalUserId;
    String requestedResource = resource.trim();
    String requestedAction = action.trim();

    Optional<ResourceState> state = diagnostics.resource(requestedResource, requestedAction);
    if (state.isEmpty()) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND,
          "Resource is not present in the control-plane catalog");
    }
    ResourceState found = state.get();
    String object = ResourceObjectKey.from(found.type(), found.resourceKey());
    var meaning = semantics.resolve(found.type(), requestedAction);

    List<GrantExplanation> grants =
        diagnostics.contributingGrants(found.id(), canonicalUserId);
    List<MembershipExplanation> memberships = diagnostics.memberships(canonicalUserId);
    List<RoleExplanation> roles = diagnostics.roles(canonicalUserId);
    PanelState panel = diagnostics.panel(found.panelId()).orElse(null);

    boolean allowed = relationships.check(canonicalSubject, meaning.permission(), object);

    return new Explanation(issuer.trim(), subject.trim(), canonicalSubject, requestedResource,
        object, requestedAction, meaning.relation(), meaning.permission(), allowed,
        reason(allowed, found, grants, panel), found, grants, memberships, roles, panel);
  }

  /**
   * The single most useful cause, chosen from the earliest failed precondition. OpenFGA remains
   * the authority for {@code allowed}; this only names why that answer is what it is.
   */
  private static String reason(boolean allowed, ResourceState resource,
      List<GrantExplanation> grants, PanelState panel) {
    if (allowed) {
      return grants.isEmpty() ? "ALLOWED_WITHOUT_CONTROL_PLANE_GRANT" : "ALLOWED_BY_GRANT";
    }
    if (!"ACTIVE".equals(resource.status())) return "RESOURCE_NOT_ACTIVE";
    if (!resource.actionAttached()) return "ACTION_NOT_ATTACHED_TO_RESOURCE";
    if (grants.isEmpty()) return "NO_CONTRIBUTING_GRANT";
    if (grants.stream().anyMatch(grant -> "FAILED".equals(grant.projectionStatus()))) {
      return "PROJECTION_FAILED";
    }
    if (grants.stream().anyMatch(grant ->
        "PENDING".equals(grant.projectionStatus()) || "RETRYING".equals(grant.projectionStatus()))) {
      return "PROJECTION_NOT_APPLIED";
    }
    if (panel != null && !"READY".equals(panel.readiness())) return panel.readiness();
    // A grant exists and reports APPLIED, yet OpenFGA denies: genuine projection drift.
    return "GRANT_APPLIED_BUT_OPENFGA_DENIES";
  }

  private static boolean blank(String value) { return value == null || value.isBlank(); }
}

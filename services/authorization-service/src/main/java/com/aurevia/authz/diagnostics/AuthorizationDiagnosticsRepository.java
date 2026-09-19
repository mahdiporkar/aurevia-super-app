package com.aurevia.authz.diagnostics;

import static com.aurevia.authz.diagnostics.AuthorizationDiagnostics.*;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Persistence port for authorization diagnostics. SQL belongs to its adapter only. */
public interface AuthorizationDiagnosticsRepository {
  Optional<ResourceState> resource(String resourceOrObject, String actionKey);
  List<GrantExplanation> contributingGrants(UUID resourceId, String canonicalSubject);
  List<MembershipExplanation> memberships(String canonicalSubject);
  List<RoleExplanation> roles(String canonicalSubject);
  Optional<PanelState> panel(UUID panelId);
}

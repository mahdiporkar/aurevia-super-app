package com.aurevia.authz.diagnostics;

import static com.aurevia.authz.diagnostics.AuthorizationDiagnostics.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.aurevia.authz.identity.CanonicalIdentityResolver;
import com.aurevia.authz.openfga.RelationshipAuthorizationPort;
import com.aurevia.authz.semantics.AuthorizationSemanticsRegistry;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class AuthorizationDiagnosticsServiceTest {

  private static final String ISSUER = "https://issuer.example";
  private static final String SUBJECT = "alice";
  // Deliberately distinct: the control-plane stores the bare id and OpenFGA the prefixed
  // principal. Using one value for both would hide a prefix mix-up in the SQL lookups.
  private static final String CANONICAL_ID = "usr_alice";
  private static final String CANONICAL = "user:" + CANONICAL_ID;
  private static final String RESOURCE_KEY = "page:hr.employees";
  private static final String OBJECT = "resource:page/hr.employees";
  private static final UUID RESOURCE_ID = UUID.fromString("44444444-4444-4444-4444-444444444444");
  private static final UUID PANEL_ID = UUID.fromString("55555555-5555-5555-5555-555555555555");

  private final AuthorizationDiagnosticsRepository repository =
      mock(AuthorizationDiagnosticsRepository.class);
  private final RelationshipAuthorizationPort relationships =
      mock(RelationshipAuthorizationPort.class);
  private AuthorizationDiagnosticsService service;

  @BeforeEach void setUp() {
    CanonicalIdentityResolver identities = mock(CanonicalIdentityResolver.class);
    when(identities.canonicalUserId(anyString(), anyString())).thenReturn(CANONICAL_ID);
    when(identities.openFgaUser(anyString(), anyString())).thenReturn(CANONICAL);
    service = new AuthorizationDiagnosticsService(repository, relationships,
        new AuthorizationSemanticsRegistry(), identities);
    when(repository.resource(anyString(), anyString()))
        .thenReturn(Optional.of(resource("ACTIVE", true)));
    when(repository.contributingGrants(any(), anyString())).thenReturn(List.of());
    when(repository.memberships(anyString())).thenReturn(List.of());
    when(repository.roles(anyString())).thenReturn(List.of());
    when(repository.panel(any())).thenReturn(Optional.of(panel("READY", "VALID")));
  }

  @Test void anAllowedDecisionReportsTheCanonicalSubjectObjectRelationAndPermission() {
    when(repository.contributingGrants(eq(RESOURCE_ID), eq(CANONICAL_ID)))
        .thenReturn(List.of(grant("USER", "alice", RESOURCE_KEY, "APPLIED")));
    when(relationships.check(CANONICAL, "can_view", OBJECT)).thenReturn(true);

    var explanation = service.explain(ISSUER, SUBJECT, RESOURCE_KEY, "view");

    assertThat(explanation.canonicalSubject()).isEqualTo(CANONICAL);
    assertThat(explanation.openFgaObject()).isEqualTo(OBJECT);
    assertThat(explanation.relation()).isEqualTo("viewer");
    assertThat(explanation.permission()).isEqualTo("can_view");
    assertThat(explanation.allowed()).isTrue();
    assertThat(explanation.reasonCode()).isEqualTo("ALLOWED_BY_GRANT");
  }

  @Test void inheritedGrantsFromGroupsAccessGroupsAndRolesAreAllReported() {
    when(repository.contributingGrants(eq(RESOURCE_ID), eq(CANONICAL_ID))).thenReturn(List.of(
        grant("GROUP", "Finance", RESOURCE_KEY, "APPLIED"),
        grant("ACCESS_GROUP", "Tehran OU", "module:hr", "APPLIED"),
        grant("ROLE", "auditor", "application:aurevia/hr", "APPLIED")));
    when(repository.memberships(CANONICAL_ID)).thenReturn(List.of(
        new MembershipExplanation("DIRECTORY_GROUP", UUID.randomUUID(), "cn=finance",
            "Finance", "group:directory/" + UUID.randomUUID(), true),
        new MembershipExplanation("ACCESS_GROUP", UUID.randomUUID(), "TEHRAN",
            "Tehran OU", "group:tehran", true)));
    when(repository.roles(CANONICAL_ID)).thenReturn(List.of(new RoleExplanation(UUID.randomUUID(),
        "auditor", "ACTIVE", "ACCESS_GROUP", "Tehran OU", null, "role:auditor")));
    when(relationships.check(CANONICAL, "can_view", OBJECT)).thenReturn(true);

    var explanation = service.explain(ISSUER, SUBJECT, RESOURCE_KEY, "view");

    assertThat(explanation.grants()).extracting(GrantExplanation::subjectType)
        .containsExactly("GROUP", "ACCESS_GROUP", "ROLE");
    // An ancestor grant names the resource it is actually attached to.
    assertThat(explanation.grants()).extracting(GrantExplanation::grantedResourceKey)
        .containsExactly(RESOURCE_KEY, "module:hr", "application:aurevia/hr");
    assertThat(explanation.memberships()).extracting(MembershipExplanation::kind)
        .containsExactly("DIRECTORY_GROUP", "ACCESS_GROUP");
    assertThat(explanation.roles()).singleElement()
        .satisfies(role -> assertThat(role.openFgaObject()).isEqualTo("role:auditor"));
  }

  @Test void aFailedProjectionIsNamedRatherThanReportedAsAMissingGrant() {
    when(repository.contributingGrants(eq(RESOURCE_ID), eq(CANONICAL_ID)))
        .thenReturn(List.of(grant("USER", "alice", RESOURCE_KEY, "FAILED")));
    when(relationships.check(CANONICAL, "can_view", OBJECT)).thenReturn(false);

    var explanation = service.explain(ISSUER, SUBJECT, RESOURCE_KEY, "view");

    assertThat(explanation.allowed()).isFalse();
    assertThat(explanation.reasonCode()).isEqualTo("PROJECTION_FAILED");
    assertThat(explanation.grants()).singleElement()
        .satisfies(grant -> assertThat(grant.projectionError()).isEqualTo("OpenFGA unavailable"));
  }

  @Test void anUnappliedProjectionIsDistinguishedFromAnAppliedOne() {
    when(repository.contributingGrants(eq(RESOURCE_ID), eq(CANONICAL_ID)))
        .thenReturn(List.of(grant("USER", "alice", RESOURCE_KEY, "RETRYING")));
    when(relationships.check(CANONICAL, "can_view", OBJECT)).thenReturn(false);

    assertThat(service.explain(ISSUER, SUBJECT, RESOURCE_KEY, "view").reasonCode())
        .isEqualTo("PROJECTION_NOT_APPLIED");
  }

  @Test void anAppliedGrantThatOpenFgaStillDeniesIsReportedAsDrift() {
    when(repository.contributingGrants(eq(RESOURCE_ID), eq(CANONICAL_ID)))
        .thenReturn(List.of(grant("USER", "alice", RESOURCE_KEY, "APPLIED")));
    when(relationships.check(CANONICAL, "can_view", OBJECT)).thenReturn(false);

    assertThat(service.explain(ISSUER, SUBJECT, RESOURCE_KEY, "view").reasonCode())
        .isEqualTo("GRANT_APPLIED_BUT_OPENFGA_DENIES");
  }

  @Test void withoutAnyContributingGrantTheMissingGrantIsTheStatedCause() {
    when(relationships.check(CANONICAL, "can_view", OBJECT)).thenReturn(false);

    assertThat(service.explain(ISSUER, SUBJECT, RESOURCE_KEY, "view").reasonCode())
        .isEqualTo("NO_CONTRIBUTING_GRANT");
  }

  @Test void registryLifecycleFailuresArePreferredOverTheMissingGrantCause() {
    when(repository.resource(anyString(), anyString()))
        .thenReturn(Optional.of(resource("DEPRECATED", true)));
    when(relationships.check(CANONICAL, "can_view", OBJECT)).thenReturn(false);

    assertThat(service.explain(ISSUER, SUBJECT, RESOURCE_KEY, "view").reasonCode())
        .isEqualTo("RESOURCE_NOT_ACTIVE");

    when(repository.resource(anyString(), anyString()))
        .thenReturn(Optional.of(resource("ACTIVE", false)));

    assertThat(service.explain(ISSUER, SUBJECT, RESOURCE_KEY, "view").reasonCode())
        .isEqualTo("ACTION_NOT_ATTACHED_TO_RESOURCE");
  }

  @Test void anUnusableUiArtifactIsExplainedInsteadOfSilentlyHidingTheMicroFrontend() {
    when(repository.contributingGrants(eq(RESOURCE_ID), eq(CANONICAL_ID)))
        .thenReturn(List.of(grant("USER", "alice", RESOURCE_KEY, "APPLIED")));
    when(repository.panel(PANEL_ID))
        .thenReturn(Optional.of(panel("NO_ACTIVE_ARTIFACT", null)));
    when(relationships.check(CANONICAL, "can_view", OBJECT)).thenReturn(false);

    var explanation = service.explain(ISSUER, SUBJECT, RESOURCE_KEY, "view");

    assertThat(explanation.reasonCode()).isEqualTo("NO_ACTIVE_ARTIFACT");
    assertThat(explanation.panel().readiness()).isEqualTo("NO_ACTIVE_ARTIFACT");
  }

  @Test void anUnknownResourceIsNotFoundAndIncompleteInputIsRejected() {
    when(repository.resource(anyString(), anyString())).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.explain(ISSUER, SUBJECT, "page:missing", "view"))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("control-plane catalog");

    assertThatThrownBy(() -> service.explain(ISSUER, "", RESOURCE_KEY, "view"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  private static ResourceState resource(String status, boolean actionAttached) {
    return new ResourceState(RESOURCE_ID, RESOURCE_KEY, "PAGE", status, true, actionAttached,
        PANEL_ID);
  }

  private static PanelState panel(String readiness, String validationStatus) {
    return new PanelState(PANEL_ID, "HR", "hr", true, "REAL",
        "NO_ACTIVE_ARTIFACT".equals(readiness) ? null : UUID.randomUUID(), validationStatus,
        true, null, readiness);
  }

  private static GrantExplanation grant(String subjectType, String label, String resourceKey,
      String projectionStatus) {
    return new GrantExplanation(UUID.randomUUID(), subjectType, label, resourceKey, "view",
        "viewer", "ACTIVE", null, projectionStatus,
        "FAILED".equals(projectionStatus) ? "OpenFGA unavailable" : null);
  }
}

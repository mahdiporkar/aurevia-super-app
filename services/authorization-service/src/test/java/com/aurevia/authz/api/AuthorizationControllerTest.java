package com.aurevia.authz.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.aurevia.authz.audit.AuthorizationDecisionAuditor;
import com.aurevia.authz.authorization.AuthorizationDecisionService;
import com.aurevia.authz.authorization.AuthorizationQueryRepository;
import com.aurevia.authz.api.dto.AuthorizationDtos.CheckRequest;
import com.aurevia.authz.openfga.RelationshipAuthorizationPort;
import com.aurevia.authz.policy.RuntimePolicyService;
import com.aurevia.authz.semantics.AuthorizationSemanticsRegistry;
import com.aurevia.authz.identity.SubjectKey;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AuthorizationControllerTest {
  RelationshipAuthorizationPort relationships = mock(RelationshipAuthorizationPort.class);
  RuntimePolicyService policies = mock(RuntimePolicyService.class);
  AuthorizationDecisionAuditor auditor = mock(AuthorizationDecisionAuditor.class);
  AuthorizationController controller;

  @BeforeEach void setUp() {
    var service = new AuthorizationDecisionService(relationships,
        mock(AuthorizationQueryRepository.class),
        new AuthorizationSemanticsRegistry(), policies, auditor,
        new com.fasterxml.jackson.databind.ObjectMapper());
    controller = new AuthorizationController(service);
  }

  @Test void openFgaAllowAndPolicyAllowReturnsObligations() {
    when(relationships.check(new SubjectKey("issuer","u1").openFgaUser(),
        "can_view", "resource:hr.employee")).thenReturn(true);
    when(policies.evaluate("issuer","u1", "resource:hr.employee", "view")).thenReturn(
        new RuntimePolicyService.Evaluation(true, "POLICY_ALLOWED",
            Map.of("maximumRows", 100), List.of("hr-scope:2")));
    var decision = controller.check(request("view"));
    assertThat(decision.result()).isEqualTo("ALLOW");
    assertThat(decision.obligations()).containsEntry("maximumRows", 100);
    verify(auditor).record(any());
  }

  @Test void policyDenyCannotOverrideOpenFgaIntoAllow() {
    when(relationships.check(any(), any(), any())).thenReturn(true);
    when(policies.evaluate(any(), any(), any(), any())).thenReturn(
        new RuntimePolicyService.Evaluation(false, "POLICY_CONTEXT_MISSING", Map.of(), List.of()));
    assertThat(controller.check(request("view")).result()).isEqualTo("DENY");
  }

  @Test void openFgaDenySkipsPolicyAndDenies() {
    when(relationships.check(any(), any(), any())).thenReturn(false);
    assertThat(controller.check(request("view")).result()).isEqualTo("DENY");
    verifyNoInteractions(policies);
  }

  @Test void auditFailureNeverReturnsAllow() {
    when(relationships.check(any(), any(), any())).thenReturn(true);
    when(policies.evaluate(any(), any(), any(), any())).thenReturn(
        new RuntimePolicyService.Evaluation(true, "POLICY_ALLOWED", Map.of(), List.of()));
    doThrow(new IllegalStateException("audit unavailable")).when(auditor).record(any());
    assertThatThrownBy(() -> controller.check(request("view")))
        .isInstanceOf(IllegalStateException.class);
  }

  private static CheckRequest request(String action) {
    return new CheckRequest("u1", "issuer", "resource:hr.employee",
        action, Map.of("orgUnit", "frontend-must-not-be-trusted"), "correlation-1");
  }
}

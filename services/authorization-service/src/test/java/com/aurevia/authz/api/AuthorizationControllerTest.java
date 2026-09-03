package com.aurevia.authz.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.aurevia.authz.audit.AuthorizationDecisionAuditor;
import com.aurevia.authz.openfga.RelationshipAuthorizationPort;
import com.aurevia.authz.policy.RuntimePolicyService;
import com.aurevia.authz.semantics.AuthorizationSemanticsRegistry;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;

class AuthorizationControllerTest {
  RelationshipAuthorizationPort relationships = mock(RelationshipAuthorizationPort.class);
  RuntimePolicyService policies = mock(RuntimePolicyService.class);
  AuthorizationDecisionAuditor auditor = mock(AuthorizationDecisionAuditor.class);
  AuthorizationController controller;

  @BeforeEach void setUp() {
    controller = new AuthorizationController(relationships, mock(JdbcClient.class),
        new AuthorizationSemanticsRegistry(), policies, auditor,
        new com.fasterxml.jackson.databind.ObjectMapper());
  }

  @Test void openFgaAllowAndPolicyAllowReturnsObligations() {
    when(relationships.check("user:u1", "can_view", "resource:hr.employee")).thenReturn(true);
    when(policies.evaluate("u1", "resource:hr.employee", "view")).thenReturn(
        new RuntimePolicyService.Evaluation(true, "POLICY_ALLOWED",
            Map.of("maximumRows", 100), List.of("hr-scope:2")));
    var decision = controller.check(request("view"));
    assertThat(decision.result()).isEqualTo("ALLOW");
    assertThat(decision.obligations()).containsEntry("maximumRows", 100);
    verify(auditor).record(any());
  }

  @Test void policyDenyCannotOverrideOpenFgaIntoAllow() {
    when(relationships.check(any(), any(), any())).thenReturn(true);
    when(policies.evaluate(any(), any(), any())).thenReturn(
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
    when(policies.evaluate(any(), any(), any())).thenReturn(
        new RuntimePolicyService.Evaluation(true, "POLICY_ALLOWED", Map.of(), List.of()));
    doThrow(new IllegalStateException("audit unavailable")).when(auditor).record(any());
    assertThatThrownBy(() -> controller.check(request("view")))
        .isInstanceOf(IllegalStateException.class);
  }

  private static AuthorizationController.CheckRequest request(String action) {
    return new AuthorizationController.CheckRequest("u1", "issuer", "resource:hr.employee",
        action, Map.of("orgUnit", "frontend-must-not-be-trusted"), "correlation-1");
  }
}

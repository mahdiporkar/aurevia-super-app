package com.aurevia.authz.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class AuthorizationDecisionAuditor {
  private final AuthorizationDecisionAuditRepository decisions;
  private final ObjectMapper json;

  public AuthorizationDecisionAuditor(AuthorizationDecisionAuditRepository decisions, ObjectMapper json) {
    this.decisions = decisions;
    this.json = json;
  }

  public void record(Record decision) {
    try {
      decisions.insert(decision,json.writeValueAsString(decision.policyReferences()));
    } catch (Exception failure) {
      throw new IllegalStateException("Authorization decision audit failed", failure);
    }
  }

  public record Record(String decisionId,String subject,String resource,String action,
      String permission,boolean openFgaAllowed,boolean policyAllowed,boolean allowed,String reason,
      long latencyMs,String correlationId,List<String> policyReferences) {}
}

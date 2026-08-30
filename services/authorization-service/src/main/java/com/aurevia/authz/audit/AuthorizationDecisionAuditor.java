package com.aurevia.authz.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

@Service
public class AuthorizationDecisionAuditor {
  private final JdbcClient database;
  private final ObjectMapper json;

  public AuthorizationDecisionAuditor(JdbcClient database, ObjectMapper json) {
    this.database = database;
    this.json = json;
  }

  public void record(Record decision) {
    try {
      database.sql("""
          insert into authorization_decision_log(
            decision_id,subject_key,resource_key,action_key,result,reason_code,model_version,
            correlation_id,normalized_permission,openfga_allowed,policy_allowed,latency_ms,
            policy_references)
          values(:decision,:subject,:resource,:action,:result,:reason,'configured-model',
            :correlation,:permission,:openfga,:policy,:latency,cast(:policies as jsonb))
          """).param("decision", decision.decisionId()).param("subject", decision.subject())
          .param("resource", decision.resource()).param("action", decision.action())
          .param("result", decision.allowed() ? "ALLOW" : "DENY")
          .param("reason", decision.reason()).param("correlation", decision.correlationId())
          .param("permission", decision.permission()).param("openfga", decision.openFgaAllowed())
          .param("policy", decision.policyAllowed()).param("latency", decision.latencyMs())
          .param("policies", json.writeValueAsString(decision.policyReferences())).update();
    } catch (Exception failure) {
      throw new IllegalStateException("Authorization decision audit failed", failure);
    }
  }

  public record Record(String decisionId,String subject,String resource,String action,
      String permission,boolean openFgaAllowed,boolean policyAllowed,boolean allowed,String reason,
      long latencyMs,String correlationId,List<String> policyReferences) {}
}

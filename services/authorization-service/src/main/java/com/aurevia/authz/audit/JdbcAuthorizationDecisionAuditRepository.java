package com.aurevia.authz.audit;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcAuthorizationDecisionAuditRepository implements AuthorizationDecisionAuditRepository {
  private final JdbcClient database;
  JdbcAuthorizationDecisionAuditRepository(JdbcClient database) { this.database=database; }

  @Override public void insert(AuthorizationDecisionAuditor.Record value,String policies) {
    database.sql("""
        insert into authorization_decision_log(decision_id,subject_key,resource_key,action_key,
          result,reason_code,model_version,correlation_id,normalized_permission,openfga_allowed,
          policy_allowed,latency_ms,policy_references)
        values(:decision,:subject,:resource,:action,:result,:reason,'configured-model',
          :correlation,:permission,:openfga,:policy,:latency,cast(:policies as jsonb))
        """).param("decision",value.decisionId()).param("subject",value.subject())
        .param("resource",value.resource()).param("action",value.action())
        .param("result",value.allowed()?"ALLOW":"DENY").param("reason",value.reason())
        .param("correlation",value.correlationId()).param("permission",value.permission())
        .param("openfga",value.openFgaAllowed()).param("policy",value.policyAllowed())
        .param("latency",value.latencyMs()).param("policies",policies).update();
  }
}

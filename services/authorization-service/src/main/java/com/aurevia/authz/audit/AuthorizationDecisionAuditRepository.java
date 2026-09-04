package com.aurevia.authz.audit;

interface AuthorizationDecisionAuditRepository {
  void insert(AuthorizationDecisionAuditor.Record decision,String policyReferencesJson);
}

package com.aurevia.authz.policy;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface RuntimePolicyRepository {
  Optional<ResourceContext> activeResource(String canonicalKey,String registryKey);
  List<PolicyRow> activePolicies(UUID resourceId,String actionKey);
  Optional<OrgContext> primaryOrganization(String issuer,String subject);

  record ResourceContext(UUID id,String classification,String ownerDomain,String ownerId) {}
  record OrgContext(String orgUnit,String branch) {}
  record PolicyRow(String policyKey,long version,String expression,String obligations) {}
}

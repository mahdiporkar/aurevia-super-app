package com.aurevia.authz.policy;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface RuntimePolicyRepository {
  /** Matches the historical colon key, the registry key, or the resource whose canonical OpenFGA object equals {@code canonicalObject}. */
  Optional<ResourceContext> activeResource(String canonicalKey,String registryKey,String canonicalObject);
  List<PolicyRow> activePolicies(UUID resourceId,String actionKey);
  Optional<OrgContext> primaryOrganization(String issuer,String subject);

  record ResourceContext(UUID id,String classification,String ownerDomain,String ownerId) {}
  record OrgContext(String orgUnit,String branch) {}
  record PolicyRow(String policyKey,long version,String expression,String obligations) {}
}

package com.aurevia.authz.sync;

import java.util.List;
import java.util.UUID;

/** Finds relational authorization records whose {@code expires_at} has passed. */
interface ExpirationRepository {
  /** Expired ACTIVE grants, locked for the calling transaction. */
  List<ExpiredGrant> expiredGrants();
  /** Expired role assignments of every subject type, locked for the calling transaction. */
  List<ExpiredRoleAssignment> expiredRoleAssignments();
  int archiveGrant(UUID id);

  record ExpiredGrant(UUID id, long version) {}
  record ExpiredRoleAssignment(String subjectType, UUID subjectId, UUID roleId, long version) {}
}

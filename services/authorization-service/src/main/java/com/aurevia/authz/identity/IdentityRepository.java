package com.aurevia.authz.identity;

import static com.aurevia.authz.identity.IdentityModels.*;

import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;

public interface IdentityRepository {
  List<DirectoryGroupView> directoryGroups();
  List<AccessGroupView> accessGroups();
  List<RoleView> roles();
  List<RoleAssignmentView> roleAssignments();
  void createRole(UUID id, RoleCommand command);
  Optional<RoleSnapshot> role(UUID id);
  int updateRole(UUID id, long version, RoleCommand command);
  int updateRoleStatus(UUID id, long version, boolean active);
  long upsertRoleAssignment(String subjectType, UUID subjectId, UUID roleId,
      java.time.Instant expiresAt, String actor);
  OptionalLong roleAssignmentVersion(String subjectType, UUID subjectId, UUID roleId);
  int deleteRoleAssignment(String subjectType, UUID subjectId, UUID roleId);
  void enqueueRoleAssignment(String subjectType, UUID subjectId, UUID roleId, String eventType,
      long aggregateVersion);
  void appendAudit(String actor, String eventType, String targetType, String targetKey);
}

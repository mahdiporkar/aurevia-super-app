package com.aurevia.authz.access;

import static com.aurevia.authz.access.AccessModels.*;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Persistence port. SQL and database-specific details belong in its adapter only. */
public interface AccessRepository {
  List<ResourceView> resources();
  List<ActionView> actions();
  List<UserView> users();
  List<GrantView> grants(String subjectType, UUID subjectId);
  Optional<ResourceSnapshot> resource(UUID id);
  boolean resourceExists(UUID id);
  boolean resourceHierarchyContains(UUID ancestorId, UUID candidateId);
  void createResource(UUID id, ResourceCommand command, String normalizedSource);
  int updateResource(UUID id, long version, ResourceCommand command, String normalizedSource);
  void createAction(UUID id, ActionCommand command);
  void attachAction(UUID resourceId, UUID actionId);
  void detachAction(UUID resourceId, UUID actionId);
  void createUser(UUID id, UserCommand command);
  Optional<GrantTarget> grantTarget(UUID resourceId, UUID actionId);
  void archiveExpiredGrant(String subjectType, UUID subjectId, UUID resourceId, UUID actionId);
  Optional<ExistingGrant> activeGrant(String subjectType, UUID subjectId, UUID resourceId, UUID actionId);
  void createGrant(UUID id, String subjectType, UUID subjectId, UUID resourceId, UUID actionId,
      String relation, Instant expiresAt);
  boolean isActiveGrant(UUID id);
  void archiveGrant(UUID id);
  void enqueueGrant(UUID id, String eventType, long aggregateVersion);
  void enqueueParent(UUID childId, UUID parentId, String eventType);
  void appendAudit(String actor, String eventType, String targetType, String targetKey);
}

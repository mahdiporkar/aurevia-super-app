package com.aurevia.authz.identity;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public interface IdentitySyncRepository {
  Set<UUID> membershipGroupIds(UUID userId);
  Optional<UUID> directoryGroupId(String issuer,String externalId);
  long incrementMembershipVersion(UUID userId);
  long membershipVersion(UUID userId);
  String subjectKey(UUID userId);
  UUID upsertDirectoryGroup(String issuer,String externalId,String normalizedPath,
      String displayName);
  void addMembership(UUID userId,UUID groupId);
  String directoryGroupExternalId(UUID groupId);
  void removeMembership(UUID userId,UUID groupId);
  void enqueueMembership(UUID userId,UUID groupId,String subjectKey,String groupExternalId,
      String event,long version);
}

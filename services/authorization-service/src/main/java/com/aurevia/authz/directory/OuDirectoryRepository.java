package com.aurevia.authz.directory;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

interface OuDirectoryRepository {
  UUID upsertUser(OuAccessService.LoginDirectoryIdentity identity,String safeAttributesJson);
  Optional<String> ouExternalIdByDn(String issuer,String ouDn);
  UUID upsertOu(String issuer,String externalId,DirectoryDnParser.ParsedUserDn parsed);
  void deactivateOtherAssignments(UUID userId,UUID ouId);
  void assignOu(UUID userId,UUID ouId);
  void deactivateAssignments(UUID userId);
  String subjectKey(UUID userId);
  Optional<String> activeUserDn(UUID userId);
  List<CalculatedGroup> calculatedGroups();
  List<GroupRule> activeRules(UUID groupId);
  Set<UUID> activeRuleSources(UUID userId,UUID groupId);
  void activateRuleMembership(UUID userId,UUID groupId,UUID sourceId);
  void deactivateRuleMembership(UUID userId,UUID groupId,UUID sourceId);
  boolean hasOtherMembershipSource(UUID userId,UUID groupId);
  long incrementMembershipVersion(UUID userId);
  void enqueueMembership(UUID userId,UUID groupId,String subjectKey,String groupCode,
      String event,long version);
  int effectiveGroupCount(UUID userId);
  void enqueueRecalculation(UUID groupId,String actor);
  Optional<RecalculationClaim> claimRecalculation(UUID owner);
  List<UUID> usersAfter(UUID cursor,int limit);
  void completeRecalculation(UUID id,UUID owner,int processed);
  void releaseRecalculation(UUID id,UUID owner,UUID cursor,int processed);
  void retryRecalculation(UUID id,UUID owner,int maxAttempts,String safeError);

  record CalculatedGroup(UUID id,String code,String combiner,boolean active) {}
  record GroupRule(UUID id,String mode,String externalDn) {}
  record RecalculationClaim(UUID id,UUID lastUserId) {}
}

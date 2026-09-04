package com.aurevia.authz.directory;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Persistence port for one authoritative Active Directory snapshot. */
interface ActiveDirectorySyncRepository {
  void startRun(UUID runId);
  void completeRun(UUID runId,int discoveredOus,int discoveredUsers);
  void failRun(UUID runId,int discoveredOus,int discoveredUsers,String safeError);
  RemovalStats ouRemovalStats(String issuer,Set<String> observedExternalIds);
  UUID upsertOu(String issuer,String externalId,String canonicalDn,String path,String name);
  void updateOuParent(UUID ouId,UUID parentOuId);
  void deactivateMissingOus(String issuer,Set<String> observedExternalIds);
  List<UUID> linkedUsers(String issuer,String directoryExternalId);
  RemovalStats userRemovalStats(String issuer,Set<String> observedExternalIds);
  List<UUID> deactivateMissingUserAssignments(String issuer,Set<String> observedExternalIds);

  record RemovalStats(int active,int missing) {}
}

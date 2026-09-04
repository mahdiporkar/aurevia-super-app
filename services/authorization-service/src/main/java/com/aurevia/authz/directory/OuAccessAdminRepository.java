package com.aurevia.authz.directory;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public interface OuAccessAdminRepository {
  List<Map<String,Object>> ous();
  List<Map<String,Object>> groups();
  List<Map<String,Object>> rules(UUID groupId);
  List<Map<String,Object>> members(UUID groupId);
  List<Map<String,Object>> recalculationJobs();
  void insertGroup(UUID id,String code,String name,String description,String combiner,String actor);
  String groupCode(UUID id);
  boolean updateGroup(UUID id,long version,String name,String description,String combiner,
      boolean active);
  void insertRule(UUID id,UUID groupId,UUID ouId,String mode,String actor);
  void bumpGroup(UUID groupId);
  void disableRule(UUID groupId,UUID ruleId);
  String groupCombiner(UUID groupId);
  List<OuRule> activeRules(UUID groupId);
  List<OuCandidate> ouCandidates();
  Set<UUID> currentMembers(UUID groupId);
  List<Map<String,Object>> applicationGrants();
  GrantTarget activeGrantTarget(UUID applicationId,UUID groupId);
  void insertApplicationGrant(UUID id,UUID applicationId,UUID groupId,String actor);
  GrantTarget activeGrant(UUID grantId);
  void revokeGrant(UUID grantId,String actor);
  void enqueueGrant(UUID grantId,String event,String groupCode,String panelSlug);
  Map<String,Object> user(UUID userId);
  List<Map<String,Object>> userOus(UUID userId);
  List<Map<String,Object>> membershipPaths(UUID userId);
  List<Map<String,Object>> userApplications(UUID userId);

  record OuRule(String mode,String externalDn) {}
  record OuCandidate(UUID id,String username,String displayName,String externalDn) {}
  record GrantTarget(String groupCode,String panelSlug) {}
}

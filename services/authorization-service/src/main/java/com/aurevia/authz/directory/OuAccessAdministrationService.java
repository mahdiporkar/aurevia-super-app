package com.aurevia.authz.directory;

import static com.aurevia.authz.api.dto.OuAccessDtos.*;

import com.aurevia.authz.observability.AuditTrail;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OuAccessAdministrationService {
  private static final Set<String> MODES=Set.of("EXACT","SUBTREE");
  private static final Set<String> COMBINERS=Set.of("ANY_OF","ALL_OF");
  private final OuAccessAdminRepository repository;
  private final OuRecalculationQueue recalculations;
  private final AuditTrail audit;

  public OuAccessAdministrationService(OuAccessAdminRepository repository,
      OuRecalculationQueue recalculations,AuditTrail audit) {
    this.repository=repository;this.recalculations=recalculations;this.audit=audit;
  }

  public List<Map<String,Object>> ous() { return repository.ous(); }
  public List<Map<String,Object>> groups() { return repository.groups(); }
  public List<Map<String,Object>> rules(UUID groupId) { return repository.rules(groupId); }
  public List<Map<String,Object>> members(UUID groupId) { return repository.members(groupId); }
  public List<Map<String,Object>> recalculationJobs() { return repository.recalculationJobs(); }
  public List<Map<String,Object>> grants() { return repository.applicationGrants(); }

  @Transactional
  public VersionResponse createGroup(GroupRequest request,String actor) {
    validateGroup(request);
    UUID id=UUID.randomUUID();
    repository.insertGroup(id,request.code(),request.name(),request.description(),
        request.ruleCombiner(),actor);
    audit.success("OU_ACCESS","ACCESS_GROUP_CREATED",null,null,"ACCESS_GROUP",
        id.toString(),request.code(),"CREATE",null,
        Map.of("combiner",request.ruleCombiner()));
    return new VersionResponse(id,0);
  }

  @Transactional
  public VersionResponse updateGroup(UUID id,long version,GroupRequest request,String actor) {
    validateGroup(request);
    if(!repository.groupCode(id).equals(request.code())) {
      throw new IllegalArgumentException("access group code is immutable");
    }
    if(!repository.updateGroup(id,version,request.name(),request.description(),
        request.ruleCombiner(),request.active())) {
      throw new OptimisticLockingFailureException("access group changed or missing");
    }
    recalculations.enqueue(id,actor);
    audit.success("OU_ACCESS","ACCESS_GROUP_UPDATED",null,null,"ACCESS_GROUP",
        id.toString(),request.code(),"UPDATE",null,Map.of("version",version+1));
    return new VersionResponse(id,version+1);
  }

  @Transactional
  public VersionResponse addRule(UUID groupId,RuleRequest request,String actor) {
    String mode=request.matchMode().toUpperCase(Locale.ROOT);
    if(!MODES.contains(mode)) throw new IllegalArgumentException("matchMode must be EXACT or SUBTREE");
    UUID ruleId=UUID.randomUUID();
    repository.insertRule(ruleId,groupId,request.ouId(),mode,actor);
    repository.bumpGroup(groupId);
    recalculations.enqueue(groupId,actor);
    audit.success("OU_ACCESS","OU_RULE_CREATED",null,null,"ACCESS_GROUP",
        groupId.toString(),groupId.toString(),"UPDATE",null,
        Map.of("ruleId",ruleId.toString(),"matchMode",mode));
    return new VersionResponse(ruleId,0);
  }

  @Transactional
  public void removeRule(UUID groupId,UUID ruleId,String actor) {
    repository.disableRule(groupId,ruleId);
    repository.bumpGroup(groupId);
    recalculations.enqueue(groupId,actor);
    audit.success("OU_ACCESS","OU_RULE_DISABLED",null,null,"ACCESS_GROUP",
        groupId.toString(),groupId.toString(),"UPDATE",null,
        Map.of("ruleId",ruleId.toString()));
  }

  public PreviewResponse preview(UUID groupId) {
    String combiner=repository.groupCombiner(groupId);
    var rules=repository.activeRules(groupId);
    List<Map<String,Object>> members=repository.ouCandidates().stream()
        .filter(user->OuRuleEvaluator.combine(rules.stream()
            .map(rule->OuRuleEvaluator.matches(user.externalDn(),rule.externalDn(),rule.mode()))
            .toList(),combiner))
        .map(user->{
          Map<String,Object> row=new LinkedHashMap<>();
          row.put("id",user.id());row.put("username",user.username());
          row.put("display_name",user.displayName());return row;
        }).toList();
    Set<UUID> current=repository.currentMembers(groupId);
    long added=members.stream().map(row->(UUID)row.get("id"))
        .filter(id->!current.contains(id)).count();
    long removed=current.stream().filter(id->members.stream()
        .noneMatch(member->id.equals(member.get("id")))).count();
    return new PreviewResponse(members,members.size(),added,removed,combiner);
  }

  @Transactional
  public PendingGrantResponse grant(GrantRequest request,String actor) {
    var target=repository.activeGrantTarget(request.applicationId(),request.accessGroupId());
    UUID id=UUID.randomUUID();
    repository.insertApplicationGrant(id,request.applicationId(),request.accessGroupId(),actor);
    repository.enqueueGrant(id,"APPLICATION_GROUP_GRANT_WRITE",target.groupCode(),
        target.panelSlug());
    audit.success("OU_ACCESS","APPLICATION_GROUP_GRANTED",null,null,
        "APPLICATION_GROUP_GRANT",id.toString(),id.toString(),"CREATE",null,Map.of());
    return new PendingGrantResponse(id,"PENDING",0);
  }

  @Transactional
  public void revoke(UUID grantId,String actor) {
    var target=repository.activeGrant(grantId);
    repository.revokeGrant(grantId,actor);
    repository.enqueueGrant(grantId,"APPLICATION_GROUP_GRANT_DELETE",target.groupCode(),
        target.panelSlug());
    audit.success("OU_ACCESS","APPLICATION_GROUP_REVOKED",null,null,
        "APPLICATION_GROUP_GRANT",grantId.toString(),grantId.toString(),"DELETE",null,Map.of());
  }

  public ExplanationResponse explain(UUID userId) {
    List<Map<String,Object>> ous=repository.userOus(userId);
    return new ExplanationResponse(repository.user(userId),ous.isEmpty()?Map.of():ous.getFirst(),
        repository.membershipPaths(userId),repository.userApplications(userId));
  }

  private static void validateGroup(GroupRequest request) {
    if(!request.code().matches("^[A-Z][A-Z0-9_]{2,159}$")) {
      throw new IllegalArgumentException("invalid access group code");
    }
    if(!COMBINERS.contains(request.ruleCombiner())) {
      throw new IllegalArgumentException("ruleCombiner must be ANY_OF or ALL_OF");
    }
  }
}

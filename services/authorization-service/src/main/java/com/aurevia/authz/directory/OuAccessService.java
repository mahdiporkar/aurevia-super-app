package com.aurevia.authz.directory;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Calculates OU rules in PostgreSQL and projects only their effective result to OpenFGA. */
@Service
public class OuAccessService {
  private final OuDirectoryRepository directory;
  public OuAccessService(OuDirectoryRepository directory) { this.directory=directory; }

  @Transactional
  public SyncResult syncLogin(LoginDirectoryIdentity identity) {
    UUID userId=upsertUser(identity);
    if(identity.distinguishedName()==null || identity.distinguishedName().isBlank()) {
      // Missing trusted directory data fails closed for calculated memberships.
      deactivateAssignment(userId);recalculateUser(userId);return new SyncResult(userId,null,0);
    }
    var parsed=DirectoryDnParser.parseUser(identity.distinguishedName());
    String knownExternal=directory.ouExternalIdByDn(identity.issuer(),parsed.ouDn()).orElse(null);
    String external=nonBlank(identity.ouExternalId()) ? identity.ouExternalId()
        : nonBlank(knownExternal)?knownExternal:"dn-sha256:"+sha256(parsed.ouDn().toLowerCase(Locale.ROOT));
    UUID ouId=directory.upsertOu(identity.issuer(),external,parsed);
    directory.deactivateOtherAssignments(userId,ouId);
    directory.assignOu(userId,ouId);
    int memberships=recalculateUser(userId);
    return new SyncResult(userId,ouId,memberships);
  }

  /** Applies a trusted service-account directory observation to an already-linked OIDC user. */
  @Transactional
  public SyncResult syncDirectoryObservation(UUID userId,String issuer,String distinguishedName,
      String ouExternalId) {
    var parsed=DirectoryDnParser.parseUser(distinguishedName);
    String knownExternal=directory.ouExternalIdByDn(issuer,parsed.ouDn()).orElse(null);
    String external=nonBlank(ouExternalId)?ouExternalId:nonBlank(knownExternal)?knownExternal:"dn-sha256:"+sha256(parsed.ouDn().toLowerCase(Locale.ROOT));
    UUID ouId=directory.upsertOu(issuer,external,parsed);
    directory.deactivateOtherAssignments(userId,ouId);
    directory.assignOu(userId,ouId);
    return new SyncResult(userId,ouId,recalculateUser(userId));
  }

  private UUID upsertUser(LoginDirectoryIdentity x) {
    return directory.upsertUser(x,safeAttributes(x.attributes()));
  }

  private void deactivateAssignment(UUID userId) {
    directory.deactivateAssignments(userId);
  }

  @Transactional
  public int recalculateUser(UUID userId) {
    String subjectKey=directory.subjectKey(userId);
    String userDn=directory.activeUserDn(userId).orElse(null);
    for(var group:directory.calculatedGroups()) {
      recalculateGroup(userId,subjectKey,group.active()?userDn:null,group);
    }
    return directory.effectiveGroupCount(userId);
  }

  private void recalculateGroup(UUID userId,String subjectKey,String userDn,
      OuDirectoryRepository.CalculatedGroup group) {
    var rules=directory.activeRules(group.id());
    List<UUID> matches=rules.stream()
      .filter(rule->OuRuleEvaluator.matches(userDn,rule.externalDn(),rule.mode()))
      .map(OuDirectoryRepository.GroupRule::id).toList();
    Set<UUID> desired=new LinkedHashSet<>();
    if("ALL_OF".equals(group.combiner())) {
      if(!rules.isEmpty()&&matches.size()==rules.size()) desired.addAll(matches);
    }
    else desired.addAll(matches);
    Set<UUID> current=directory.activeRuleSources(userId,group.id());
    boolean hasOtherSource=directory.hasOtherMembershipSource(userId,group.id());
    boolean hadAny=!current.isEmpty()||hasOtherSource;
    for(UUID source:desired) directory.activateRuleMembership(userId,group.id(),source);
    for(UUID source:current) if(!desired.contains(source)) {
      directory.deactivateRuleMembership(userId,group.id(),source);
    }
    boolean hasAny=!desired.isEmpty()||hasOtherSource;
    if(hadAny!=hasAny) {
      long version=directory.incrementMembershipVersion(userId);
      enqueueMembership(userId,group,subjectKey,hasAny,version);
    }
  }

  private void enqueueMembership(UUID userId,OuDirectoryRepository.CalculatedGroup group,
      String subjectKey,
      boolean write,long version) {
    String event=write?"ACCESS_GROUP_MEMBERSHIP_WRITE":"ACCESS_GROUP_MEMBERSHIP_DELETE";
    directory.enqueueMembership(userId,group.id(),subjectKey,group.code(),event,version);
  }
  private static boolean nonBlank(String x){return x!=null&&!x.isBlank();}
  private static String sha256(String x){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(x.getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException(e);}}
  private static String safeAttributes(Map<String,String> attrs) {
    if(attrs==null||attrs.isEmpty())return "{}";
    Set<String> allowed=Set.of("department","title","employeeType");StringBuilder json=new StringBuilder("{");
    for(var e:attrs.entrySet())if(allowed.contains(e.getKey())&&e.getValue()!=null){if(json.length()>1)json.append(',');json.append('"').append(e.getKey()).append("\":\"").append(e.getValue().replace("\\","\\\\").replace("\"","\\\"")).append('"');}
    return json.append('}').toString();
  }
  public record LoginDirectoryIdentity(String issuer,String subject,String username,String displayName,String email,String distinguishedName,String ouExternalId,String directoryExternalId,Map<String,String> attributes) {}
  public record SyncResult(UUID userId,UUID ouId,int effectiveGroups) {}
}

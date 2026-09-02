package com.aurevia.authz.directory;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Calculates OU rules in PostgreSQL and projects only their effective result to OpenFGA. */
@Service
public class OuAccessService {
  private final JdbcClient db;
  public OuAccessService(JdbcClient db) { this.db=db; }

  @Transactional
  public SyncResult syncLogin(LoginDirectoryIdentity identity) {
    UUID userId=upsertUser(identity);
    if(identity.distinguishedName()==null || identity.distinguishedName().isBlank()) {
      // Missing trusted directory data fails closed for calculated memberships.
      deactivateAssignment(userId);recalculateUser(userId);return new SyncResult(userId,null,0);
    }
    var parsed=DirectoryDnParser.parseUser(identity.distinguishedName());
    String knownExternal=db.sql("select external_id from directory_ou where issuer=:issuer and lower(external_dn)=lower(:dn)")
      .param("issuer",identity.issuer()).param("dn",parsed.ouDn()).query(String.class).optional().orElse(null);
    String external=nonBlank(identity.ouExternalId()) ? identity.ouExternalId()
        : nonBlank(knownExternal)?knownExternal:"dn-sha256:"+sha256(parsed.ouDn().toLowerCase(Locale.ROOT));
    UUID ouId=upsertOu(identity.issuer(),external,parsed);
    db.sql("update user_ou_assignment set active=false,removed_at=now(),updated_at=now() where user_id=:user and active and ou_id<>:ou")
        .param("user",userId).param("ou",ouId).update();
    db.sql("""
      insert into user_ou_assignment(user_id,ou_id) values(:user,:ou)
      on conflict (user_id) where active and source='ACTIVE_DIRECTORY'
      do update set ou_id=excluded.ou_id,last_seen_at=now(),updated_at=now(),removed_at=null
      """).param("user",userId).param("ou",ouId).update();
    int memberships=recalculateUser(userId);
    return new SyncResult(userId,ouId,memberships);
  }

  /** Applies a trusted service-account directory observation to an already-linked OIDC user. */
  @Transactional
  public SyncResult syncDirectoryObservation(UUID userId,String issuer,String distinguishedName,
      String ouExternalId) {
    var parsed=DirectoryDnParser.parseUser(distinguishedName);
    String knownExternal=db.sql("select external_id from directory_ou where issuer=:issuer and lower(external_dn)=lower(:dn)")
      .param("issuer",issuer).param("dn",parsed.ouDn()).query(String.class).optional().orElse(null);
    String external=nonBlank(ouExternalId)?ouExternalId:nonBlank(knownExternal)?knownExternal:"dn-sha256:"+sha256(parsed.ouDn().toLowerCase(Locale.ROOT));
    UUID ouId=upsertOu(issuer,external,parsed);
    db.sql("update user_ou_assignment set active=false,removed_at=now(),updated_at=now() where user_id=:user and active and ou_id<>:ou")
      .param("user",userId).param("ou",ouId).update();
    db.sql("""
      insert into user_ou_assignment(user_id,ou_id) values(:user,:ou)
      on conflict (user_id) where active and source='ACTIVE_DIRECTORY'
      do update set ou_id=excluded.ou_id,last_seen_at=now(),updated_at=now(),removed_at=null
      """).param("user",userId).param("ou",ouId).update();
    return new SyncResult(userId,ouId,recalculateUser(userId));
  }

  private UUID upsertUser(LoginDirectoryIdentity x) {
    return db.sql("""
      insert into app_user(issuer,external_id,username,display_name,email,directory_attributes,directory_external_id)
      values(:issuer,:subject,:username,:display,:email,cast(:attrs as jsonb),:directoryExternal)
      on conflict(issuer,external_id) do update set username=excluded.username,
        display_name=excluded.display_name,email=excluded.email,status='ACTIVE',
        directory_attributes=excluded.directory_attributes,directory_external_id=coalesce(excluded.directory_external_id,app_user.directory_external_id),updated_at=now()
      returning id
      """).param("issuer",x.issuer()).param("subject",x.subject()).param("username",x.username())
      .param("display",x.displayName()).param("email",x.email()).param("attrs",safeAttributes(x.attributes())).param("directoryExternal",x.directoryExternalId()).query(UUID.class).single();
  }

  private UUID upsertOu(String issuer,String external,DirectoryDnParser.ParsedUserDn parsed) {
    var byDn=db.sql("select id,external_id from directory_ou where issuer=:issuer and lower(external_dn)=lower(:dn)")
      .param("issuer",issuer).param("dn",parsed.ouDn()).query().listOfRows();
    if(!byDn.isEmpty()) {
      UUID id=(UUID)byDn.getFirst().get("id");
      db.sql("update directory_ou set external_id=:external,external_path=:path,name=:name,active=true,last_synced_at=now(),updated_at=now(),version=version+1 where id=:id")
        .param("id",id).param("external",external).param("path",parsed.ouPath()).param("name",parsed.ouName()).update();
      return id;
    }
    return db.sql("""
      insert into directory_ou(issuer,external_id,external_dn,external_path,name)
      values(:issuer,:external,:dn,:path,:name)
      on conflict(issuer,external_id) do update set external_dn=excluded.external_dn,
        external_path=excluded.external_path,name=excluded.name,active=true,last_synced_at=now(),
        updated_at=now(),version=directory_ou.version+1 returning id
      """).param("issuer",issuer).param("external",external).param("dn",parsed.ouDn())
      .param("path",parsed.ouPath()).param("name",parsed.ouName()).query(UUID.class).single();
  }

  private void deactivateAssignment(UUID userId) {
    db.sql("update user_ou_assignment set active=false,removed_at=now(),updated_at=now() where user_id=:user and active")
      .param("user",userId).update();
  }

  @Transactional
  public int recalculateUser(UUID userId) {
    String userExternal=db.sql("select external_id from app_user where id=:id").param("id",userId).query(String.class).single();
    String userDn=db.sql("select o.external_dn from user_ou_assignment a join directory_ou o on o.id=a.ou_id where a.user_id=:user and a.active and o.active")
      .param("user",userId).query(String.class).optional().orElse(null);
    var groups=db.sql("select id,code,rule_combiner::text combiner,active from access_group where group_type='CALCULATED'").query().listOfRows();
    for(var group:groups) recalculateGroup(userId,userExternal,Boolean.TRUE.equals(group.get("active"))?userDn:null,(UUID)group.get("id"),(String)group.get("code"),(String)group.get("combiner"));
    return db.sql("select count(distinct access_group_id) from effective_group_membership where user_id=:user and active")
      .param("user",userId).query(Integer.class).single();
  }

  private void recalculateGroup(UUID userId,String userExternal,String userDn,UUID groupId,String code,String combiner) {
    var rules=db.sql("select r.id,r.match_mode::text mode,o.external_dn from access_group_ou_rule r join directory_ou o on o.id=r.ou_id where r.access_group_id=:group and r.active and o.active")
      .param("group",groupId).query().listOfRows();
    List<UUID> matches=rules.stream().filter(r->OuRuleEvaluator.matches(userDn,(String)r.get("external_dn"),(String)r.get("mode"))).map(r->(UUID)r.get("id")).toList();
    Set<UUID> desired=new LinkedHashSet<>();
    if("ALL_OF".equals(combiner)) { if(!rules.isEmpty()&&matches.size()==rules.size()) desired.add(groupId); }
    else desired.addAll(matches);
    Set<UUID> current=new LinkedHashSet<>(db.sql("select source_id from effective_group_membership where user_id=:user and access_group_id=:group and source_type='OU_RULE' and active")
      .param("user",userId).param("group",groupId).query(UUID.class).list());
    boolean hadAny=!current.isEmpty();
    for(UUID source:desired) db.sql("""
      insert into effective_group_membership(user_id,access_group_id,source_type,source_id)
      values(:user,:group,'OU_RULE',:source)
      on conflict(user_id,access_group_id,source_type,source_id) do update set
        active=true,removed_at=null,calculated_at=now(),membership_version=effective_group_membership.membership_version+1
      """).param("user",userId).param("group",groupId).param("source",source).update();
    for(UUID source:current) if(!desired.contains(source)) db.sql("update effective_group_membership set active=false,removed_at=now(),calculated_at=now(),membership_version=membership_version+1 where user_id=:user and access_group_id=:group and source_type='OU_RULE' and source_id=:source")
      .param("user",userId).param("group",groupId).param("source",source).update();
    boolean hasAny=!desired.isEmpty() || db.sql("select exists(select 1 from effective_group_membership where user_id=:user and access_group_id=:group and active and source_type<>'OU_RULE')")
      .param("user",userId).param("group",groupId).query(Boolean.class).single();
    if(hadAny!=hasAny) {
      enqueueMembership(userId,groupId,userExternal,code,hasAny);
      db.sql("update app_user set membership_version=membership_version+1,updated_at=now() where id=:user").param("user",userId).update();
    }
  }

  private void enqueueMembership(UUID userId,UUID groupId,String user,String code,boolean write) {
    String event=write?"ACCESS_GROUP_MEMBERSHIP_WRITE":"ACCESS_GROUP_MEMBERSHIP_DELETE";
    db.sql("insert into outbox_event(aggregate_type,aggregate_id,event_type,payload,idempotency_key) values('access-group-membership',:group,:event,jsonb_build_object('user','user:'||:user,'relation','member','object','group:'||lower(:code)),:event||':'||:userId||':'||:group||':'||(select membership_version from app_user where id=:userId))")
      .param("group",groupId).param("event",event).param("user",user).param("code",code).param("userId",userId).update();
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

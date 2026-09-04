package com.aurevia.authz.directory;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcOuDirectoryRepository implements OuDirectoryRepository {
  private final JdbcClient database;
  JdbcOuDirectoryRepository(JdbcClient database) { this.database=database; }

  @Override public UUID upsertUser(OuAccessService.LoginDirectoryIdentity value,String attrs) {
    return database.sql("""
      insert into app_user(issuer,external_id,username,display_name,email,directory_attributes,
        directory_external_id)
      values(:issuer,:subject,:username,:display,:email,cast(:attrs as jsonb),:directoryExternal)
      on conflict(issuer,external_id) do update set username=excluded.username,
        display_name=excluded.display_name,email=excluded.email,status='ACTIVE',
        directory_attributes=excluded.directory_attributes,
        directory_external_id=coalesce(excluded.directory_external_id,app_user.directory_external_id),
        updated_at=now() returning id
      """).param("issuer",value.issuer()).param("subject",value.subject())
      .param("username",value.username()).param("display",value.displayName())
      .param("email",value.email()).param("attrs",attrs)
      .param("directoryExternal",value.directoryExternalId()).query(UUID.class).single();
  }
  @Override public Optional<String> ouExternalIdByDn(String issuer,String dn) {
    return database.sql("""
      select external_id from directory_ou
      where issuer=:issuer and lower(external_dn)=lower(:dn)
      """).param("issuer",issuer).param("dn",dn).query(String.class).optional();
  }
  @Override public UUID upsertOu(String issuer,String external,DirectoryDnParser.ParsedUserDn parsed) {
    Optional<UUID> byDn=database.sql("""
      select id from directory_ou where issuer=:issuer and lower(external_dn)=lower(:dn)
      """).param("issuer",issuer).param("dn",parsed.ouDn()).query(UUID.class).optional();
    if(byDn.isPresent()) {
      database.sql("""
        update directory_ou set external_id=:external,external_path=:path,name=:name,
          active=true,last_synced_at=now(),updated_at=now(),version=version+1 where id=:id
        """).param("id",byDn.get()).param("external",external)
        .param("path",parsed.ouPath()).param("name",parsed.ouName()).update();
      return byDn.get();
    }
    return database.sql("""
      insert into directory_ou(issuer,external_id,external_dn,external_path,name)
      values(:issuer,:external,:dn,:path,:name)
      on conflict(issuer,external_id) do update set external_dn=excluded.external_dn,
        external_path=excluded.external_path,name=excluded.name,active=true,
        last_synced_at=now(),updated_at=now(),version=directory_ou.version+1 returning id
      """).param("issuer",issuer).param("external",external).param("dn",parsed.ouDn())
      .param("path",parsed.ouPath()).param("name",parsed.ouName()).query(UUID.class).single();
  }
  @Override public void deactivateOtherAssignments(UUID user,UUID ou) { database.sql("""
      update user_ou_assignment set active=false,removed_at=now(),updated_at=now()
      where user_id=:user and active and ou_id<>:ou
      """).param("user",user).param("ou",ou).update(); }
  @Override public void assignOu(UUID user,UUID ou) { database.sql("""
      insert into user_ou_assignment(user_id,ou_id) values(:user,:ou)
      on conflict (user_id) where active and source='ACTIVE_DIRECTORY'
      do update set ou_id=excluded.ou_id,last_seen_at=now(),updated_at=now(),removed_at=null
      """).param("user",user).param("ou",ou).update(); }
  @Override public void deactivateAssignments(UUID user) { database.sql("""
      update user_ou_assignment set active=false,removed_at=now(),updated_at=now()
      where user_id=:user and active
      """).param("user",user).update(); }
  @Override public String subjectKey(UUID user) { return database.sql(
      "select subject_key from app_user where id=:id").param("id",user)
      .query(String.class).single(); }
  @Override public Optional<String> activeUserDn(UUID user) { return database.sql("""
      select o.external_dn from user_ou_assignment a join directory_ou o on o.id=a.ou_id
      where a.user_id=:user and a.active and o.active
      """).param("user",user).query(String.class).optional(); }
  @Override public List<CalculatedGroup> calculatedGroups() { return database.sql("""
      select id,code,rule_combiner::text combiner,active from access_group
      where group_type='CALCULATED'
      """).query(CalculatedGroup.class).list(); }
  @Override public List<GroupRule> activeRules(UUID group) { return database.sql("""
      select r.id,r.match_mode::text mode,o.external_dn as "externalDn"
      from access_group_ou_rule r join directory_ou o on o.id=r.ou_id
      where r.access_group_id=:group and r.active and o.active
      """).param("group",group).query(GroupRule.class).list(); }
  @Override public Set<UUID> activeRuleSources(UUID user,UUID group) { return new HashSet<>(database.sql("""
      select source_id from effective_group_membership
      where user_id=:user and access_group_id=:group and source_type='OU_RULE' and active
      """).param("user",user).param("group",group).query(UUID.class).list()); }
  @Override public void activateRuleMembership(UUID user,UUID group,UUID source) { database.sql("""
      insert into effective_group_membership(user_id,access_group_id,source_type,source_id)
      values(:user,:group,'OU_RULE',:source)
      on conflict(user_id,access_group_id,source_type,source_id) do update set
        active=true,removed_at=null,calculated_at=now(),
        membership_version=effective_group_membership.membership_version+1
      where not effective_group_membership.active
      """).param("user",user).param("group",group).param("source",source).update(); }
  @Override public void deactivateRuleMembership(UUID user,UUID group,UUID source) { database.sql("""
      update effective_group_membership set active=false,removed_at=now(),calculated_at=now(),
        membership_version=membership_version+1 where user_id=:user and access_group_id=:group
        and source_type='OU_RULE' and source_id=:source
      """).param("user",user).param("group",group).param("source",source).update(); }
  @Override public boolean hasOtherMembershipSource(UUID user,UUID group) { return database.sql("""
      select exists(select 1 from effective_group_membership
        where user_id=:user and access_group_id=:group and active and source_type<>'OU_RULE')
      """).param("user",user).param("group",group).query(Boolean.class).single(); }
  @Override public long incrementMembershipVersion(UUID user) { return database.sql("""
      update app_user set membership_version=membership_version+1,updated_at=now()
      where id=:user returning membership_version
      """).param("user",user).query(Long.class).single(); }
  @Override public void enqueueMembership(UUID user,UUID group,String subjectKey,String code,
      String event,long version) { database.sql("""
      insert into outbox_event(aggregate_type,aggregate_id,event_type,payload,idempotency_key)
      values('access-group-membership',:group,:event,
        jsonb_build_object('user','user:'||:subjectKey,'relation','member',
          'object','group:'||lower(:code)),
        :event||':'||:user||':'||:group||':'||:version)
      """).param("group",group).param("event",event).param("subjectKey",subjectKey)
      .param("code",code).param("user",user).param("version",version).update(); }
  @Override public int effectiveGroupCount(UUID user) { return database.sql("""
      select count(distinct access_group_id) from effective_group_membership
      where user_id=:user and active
      """).param("user",user).query(Integer.class).single(); }
  @Override public void enqueueRecalculation(UUID group,String actor) { database.sql("""
      insert into ou_recalculation_job(access_group_id,requested_by)
      values(:group,:actor) on conflict do nothing
      """).param("group",group).param("actor",actor).update(); }
  @Override public Optional<RecalculationClaim> claimRecalculation(UUID owner) { return database.sql("""
      with candidate as (
        select id from ou_recalculation_job
        where (status='PENDING' and available_at<=now())
           or (status='RUNNING' and claimed_at<now()-interval '2 minutes')
        order by created_at for update skip locked limit 1
      )
      update ou_recalculation_job job set status='RUNNING',claimed_at=now(),claim_owner=:owner
      from candidate where job.id=candidate.id
      returning job.id,job.last_user_id as "lastUserId"
      """).param("owner",owner).query(RecalculationClaim.class).optional(); }
  @Override public List<UUID> usersAfter(UUID cursor,int limit) { return database.sql("""
      select id from app_user where (:cursor is null or id>:cursor) order by id limit :limit
      """).param("cursor",cursor).param("limit",limit).query(UUID.class).list(); }
  @Override public void completeRecalculation(UUID id,UUID owner,int processed) { database.sql("""
      update ou_recalculation_job set status='COMPLETED',completed_at=now(),
        processed_users=processed_users+:processed,claimed_at=null,claim_owner=null
      where id=:id and claim_owner=:owner
      """).param("id",id).param("owner",owner).param("processed",processed).update(); }
  @Override public void releaseRecalculation(UUID id,UUID owner,UUID cursor,int processed) { database.sql("""
      update ou_recalculation_job set status='PENDING',last_user_id=:cursor,
        processed_users=processed_users+:processed,claimed_at=null,claim_owner=null
      where id=:id and claim_owner=:owner
      """).param("id",id).param("owner",owner).param("cursor",cursor)
      .param("processed",processed).update(); }
  @Override public void retryRecalculation(UUID id,UUID owner,int max,String error) { database.sql("""
      update ou_recalculation_job set attempts=attempts+1,
        status=case when attempts+1>=:max then 'FAILED'::recalculation_job_status
          else 'PENDING'::recalculation_job_status end,
        available_at=now()+make_interval(secs=>least(300,
          cast(power(2,least(attempts,8)) as integer))),safe_error=:error,
        claimed_at=null,claim_owner=null where id=:id and claim_owner=:owner
      """).param("id",id).param("owner",owner).param("max",max)
      .param("error",error).update(); }
}

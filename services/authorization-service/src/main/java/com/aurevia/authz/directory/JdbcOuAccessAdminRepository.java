package com.aurevia.authz.directory;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcOuAccessAdminRepository implements OuAccessAdminRepository {
  private final JdbcClient database;
  JdbcOuAccessAdminRepository(JdbcClient database) { this.database=database; }

  @Override public List<Map<String,Object>> ous() { return database.sql("""
      select o.*,count(a.id) filter(where a.active) user_count from directory_ou o
      left join user_ou_assignment a on a.ou_id=o.id group by o.id order by external_path
      """).query().listOfRows(); }
  @Override public List<Map<String,Object>> groups() { return database.sql("""
      select g.*,count(distinct m.user_id) filter(where m.active) member_count
      from access_group g left join effective_group_membership m on m.access_group_id=g.id
      group by g.id order by g.code
      """).query().listOfRows(); }
  @Override public List<Map<String,Object>> rules(UUID id) { return database.sql("""
      select r.*,o.name ou_name,o.external_path,o.external_dn
      from access_group_ou_rule r join directory_ou o on o.id=r.ou_id
      where r.access_group_id=:id order by o.external_path
      """).param("id",id).query().listOfRows(); }
  @Override public List<Map<String,Object>> members(UUID id) { return database.sql("""
      select u.id user_id,u.username,u.display_name,m.source_type,m.source_id,
        m.calculated_at,m.membership_version
      from effective_group_membership m join app_user u on u.id=m.user_id
      where m.access_group_id=:id and m.active order by u.username
      """).param("id",id).query().listOfRows(); }
  @Override public List<Map<String,Object>> recalculationJobs() { return database.sql("""
      select j.id,j.access_group_id,g.code group_code,j.status::text status,
        j.processed_users,j.attempts,j.safe_error,j.created_at,j.completed_at
      from ou_recalculation_job j join access_group g on g.id=j.access_group_id
      order by j.created_at desc limit 100
      """).query().listOfRows(); }
  @Override public void insertGroup(UUID id,String code,String name,String description,
      String combiner,String actor) { database.sql("""
      insert into access_group(id,code,name,description,group_type,rule_combiner,created_by)
      values(:id,:code,:name,:description,'CALCULATED',cast(:combiner as rule_combiner),:actor)
      """).param("id",id).param("code",code).param("name",name)
      .param("description",description).param("combiner",combiner).param("actor",actor).update(); }
  @Override public String groupCode(UUID id) { return database.sql(
      "select code from access_group where id=:id").param("id",id)
      .query(String.class).single(); }
  @Override public boolean updateGroup(UUID id,long version,String name,String description,
      String combiner,boolean active) { return database.sql("""
      update access_group set name=:name,description=:description,
        rule_combiner=cast(:combiner as rule_combiner),active=:active,
        version=version+1,updated_at=now() where id=:id and version=:version
      """).param("id",id).param("version",version).param("name",name)
      .param("description",description).param("combiner",combiner)
      .param("active",active).update()==1; }
  @Override public void insertRule(UUID id,UUID groupId,UUID ouId,String mode,String actor) {
    database.sql("""
      insert into access_group_ou_rule(id,access_group_id,ou_id,match_mode,created_by)
      values(:rule,:group,:ou,cast(:mode as ou_match_mode),:actor)
      """).param("rule",id).param("group",groupId).param("ou",ouId)
      .param("mode",mode).param("actor",actor).update(); }
  @Override public void bumpGroup(UUID id) { database.sql(
      "update access_group set version=version+1,updated_at=now() where id=:id")
      .param("id",id).update(); }
  @Override public void disableRule(UUID groupId,UUID ruleId) { database.sql("""
      update access_group_ou_rule set active=false,version=version+1,updated_at=now()
      where id=:rule and access_group_id=:group
      """).param("rule",ruleId).param("group",groupId).update(); }
  @Override public String groupCombiner(UUID id) { return database.sql(
      "select rule_combiner::text from access_group where id=:id")
      .param("id",id).query(String.class).single(); }
  @Override public List<OuRule> activeRules(UUID id) { return database.sql("""
      select r.match_mode::text mode,o.external_dn as "externalDn"
      from access_group_ou_rule r join directory_ou o on o.id=r.ou_id
      where r.access_group_id=:id and r.active and o.active
      """).param("id",id).query(OuRule.class).list(); }
  @Override public List<OuCandidate> ouCandidates() { return database.sql("""
      select u.id,u.username,u.display_name as "displayName",o.external_dn as "externalDn"
      from app_user u join user_ou_assignment a on a.user_id=u.id and a.active
      join directory_ou o on o.id=a.ou_id and o.active order by u.username
      """).query(OuCandidate.class).list(); }
  @Override public Set<UUID> currentMembers(UUID id) { return new HashSet<>(database.sql("""
      select distinct user_id from effective_group_membership
      where access_group_id=:group and active
      """).param("group",id).query(UUID.class).list()); }
  @Override public List<Map<String,Object>> applicationGrants() { return database.sql("""
      select g.*,p.code application_code,p.name_fa application_name,
        a.code group_code,a.name group_name from application_group_grant g
      join panel p on p.id=g.application_id join access_group a on a.id=g.access_group_id
      order by p.code,a.code,g.granted_at desc
      """).query().listOfRows(); }
  @Override public GrantTarget activeGrantTarget(UUID panel,UUID group) { return database.sql("""
      select a.code as "groupCode",p.slug as "panelSlug"
      from panel p cross join access_group a
      where p.id=:panel and a.id=:group and p.active and a.active
      """).param("panel",panel).param("group",group).query(GrantTarget.class).single(); }
  @Override public void insertApplicationGrant(UUID id,UUID panel,UUID group,String actor) {
    database.sql("""
      insert into application_group_grant(id,application_id,access_group_id,granted_by)
      values(:id,:panel,:group,:actor)
      """).param("id",id).param("panel",panel).param("group",group)
      .param("actor",actor).update(); }
  @Override public GrantTarget activeGrant(UUID id) { return database.sql("""
      select a.code as "groupCode",p.slug as "panelSlug"
      from application_group_grant g join access_group a on a.id=g.access_group_id
      join panel p on p.id=g.application_id where g.id=:id and g.revoked_at is null
      """).param("id",id).query(GrantTarget.class).single(); }
  @Override public void revokeGrant(UUID id,String actor) { database.sql("""
      update application_group_grant set status='PENDING',revoked_by=:actor,
        revoked_at=now(),version=version+1 where id=:id
      """).param("id",id).param("actor",actor).update(); }
  @Override public void enqueueGrant(UUID id,String event,String code,String slug) {
    database.sql("""
      insert into outbox_event(aggregate_type,aggregate_id,event_type,payload,idempotency_key)
      values('application-group-grant',:id,:event,
        jsonb_build_object('user','group:'||lower(:code)||'#member','relation','viewer',
          'object','application:aurevia/'||:slug),
        :event||':'||:id||':'||(select version from application_group_grant where id=:id))
      """).param("id",id).param("event",event).param("code",code)
      .param("slug",slug).update(); }
  @Override public Map<String,Object> user(UUID id) { return database.sql("""
      select id,username,display_name,membership_version from app_user where id=:id
      """).param("id",id).query().singleRow(); }
  @Override public List<Map<String,Object>> userOus(UUID id) { return database.sql("""
      select o.id,o.external_path,o.external_dn,o.last_synced_at
      from user_ou_assignment a join directory_ou o on o.id=a.ou_id
      where a.user_id=:id and a.active
      """).param("id",id).query().listOfRows(); }
  @Override public List<Map<String,Object>> membershipPaths(UUID id) { return database.sql("""
      select g.code,g.name,r.match_mode,o.external_path rule_path,m.source_type,
        m.source_id,m.calculated_at from effective_group_membership m
      join access_group g on g.id=m.access_group_id
      left join access_group_ou_rule r on r.id=m.source_id
      left join directory_ou o on o.id=r.ou_id
      where m.user_id=:id and m.active order by g.code
      """).param("id",id).query().listOfRows(); }
  @Override public List<Map<String,Object>> userApplications(UUID id) { return database.sql("""
      select distinct p.code,p.name_fa,g.code group_code,ag.status
      from effective_group_membership m join access_group g on g.id=m.access_group_id
      join application_group_grant ag on ag.access_group_id=g.id and ag.revoked_at is null
      join panel p on p.id=ag.application_id where m.user_id=:id and m.active order by p.code
      """).param("id",id).query().listOfRows(); }
}

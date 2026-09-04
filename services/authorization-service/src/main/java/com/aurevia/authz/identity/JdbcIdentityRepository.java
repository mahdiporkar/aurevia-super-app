package com.aurevia.authz.identity;

import static com.aurevia.authz.identity.IdentityModels.*;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcIdentityRepository implements IdentityRepository {
  private final JdbcClient database;
  public JdbcIdentityRepository(JdbcClient database) { this.database = database; }

  @Override public List<DirectoryGroupView> directoryGroups() {
    return database.sql("""
        select id,issuer,external_id,normalized_path,display_name,parent_id,status::text status,
          sync_at,version from directory_group order by normalized_path
        """).query((rs, row) -> new DirectoryGroupView(uuid(rs, "id"), rs.getString("issuer"),
            rs.getString("external_id"), rs.getString("normalized_path"),
            rs.getString("display_name"), uuid(rs, "parent_id"), rs.getString("status"),
            instant(rs, "sync_at"), rs.getLong("version"))).list();
  }

  @Override public List<AccessGroupView> accessGroups() {
    return database.sql("""
        select id,code,name,description,group_type::text group_type,
          rule_combiner::text rule_combiner,active,version
        from access_group order by code
        """).query((rs, row) -> new AccessGroupView(uuid(rs, "id"), rs.getString("code"),
            rs.getString("name"), rs.getString("description"), rs.getString("group_type"),
            rs.getString("rule_combiner"), rs.getBoolean("active"), rs.getLong("version"))).list();
  }

  @Override public List<RoleView> roles() {
    return database.sql("""
        select id,role_key,name_fa,name_en,status::text status,version
        from application_role order by role_key
        """).query((rs, row) -> new RoleView(uuid(rs, "id"), rs.getString("role_key"),
            rs.getString("name_fa"), rs.getString("name_en"), rs.getString("status"),
            rs.getLong("version"))).list();
  }

  @Override public List<RoleAssignmentView> roleAssignments() {
    return database.sql("""
        select 'USER' subject_type,ura.user_id subject_id,
          coalesce(u.display_name,u.username) subject_name,ura.role_id,r.role_key,ura.expires_at,
          ura.assigned_by,ura.assigned_at
        from user_role_assignment ura join app_user u on u.id=ura.user_id
        join application_role r on r.id=ura.role_id
        union all
        select 'DIRECTORY_GROUP',gra.group_id,g.display_name,gra.role_id,r.role_key,
          gra.expires_at,gra.assigned_by,gra.assigned_at
        from group_role_assignment gra join directory_group g on g.id=gra.group_id
        join application_role r on r.id=gra.role_id
        union all
        select 'ACCESS_GROUP',ara.access_group_id,ag.name,ara.role_id,r.role_key,
          ara.expires_at,ara.assigned_by,ara.assigned_at
        from access_group_role_assignment ara join access_group ag on ag.id=ara.access_group_id
        join application_role r on r.id=ara.role_id
        order by subject_name,role_key
        """).query((rs, row) -> new RoleAssignmentView(rs.getString("subject_type"),
            uuid(rs, "subject_id"), rs.getString("subject_name"), uuid(rs, "role_id"),
            rs.getString("role_key"), instant(rs, "expires_at"), rs.getString("assigned_by"),
            instant(rs, "assigned_at"))).list();
  }

  @Override public void createRole(UUID id, RoleCommand command) {
    database.sql("""
        insert into application_role(id,role_key,name_fa,name_en) values(:id,:key,:fa,:en)
        """).param("id", id).param("key", command.roleKey()).param("fa", command.nameFa())
        .param("en", command.nameEn()).update();
  }

  @Override public Optional<RoleSnapshot> role(UUID id) {
    return database.sql("select role_key,version from application_role where id=:id")
        .param("id", id).query(RoleSnapshot.class).optional();
  }

  @Override public int updateRole(UUID id, long version, RoleCommand command) {
    return database.sql("""
        update application_role set name_fa=:fa,name_en=:en,version=version+1,updated_at=now()
        where id=:id and version=:version
        """).param("fa", command.nameFa()).param("en", command.nameEn()).param("id", id)
        .param("version", version).update();
  }

  @Override public int updateRoleStatus(UUID id, long version, boolean active) {
    return database.sql("""
        update application_role set status=cast(:status as lifecycle_status),version=version+1,
          updated_at=now() where id=:id and version=:version
        """).param("status", active ? "ACTIVE" : "INACTIVE").param("id", id)
        .param("version", version).update();
  }

  @Override public long upsertRoleAssignment(String type, UUID subjectId, UUID roleId,
      Instant expiresAt, String actor) {
    String safeActor = bounded(actor, 500, "unknown");
    return switch (type) {
      case "USER" -> database.sql("""
          insert into user_role_assignment(user_id,role_id,expires_at,assigned_by)
          values(:subject,:role,:expires,:actor)
          on conflict(user_id,role_id) do update set expires_at=excluded.expires_at,
            assigned_by=excluded.assigned_by,assigned_at=now(),updated_at=now(),
            version=user_role_assignment.version+1 returning version
          """).param("subject", subjectId).param("role", roleId).param("expires", expiresAt)
          .param("actor", safeActor).query(Long.class).single();
      case "DIRECTORY_GROUP" -> database.sql("""
          insert into group_role_assignment(group_id,role_id,expires_at,assigned_by)
          values(:subject,:role,:expires,:actor)
          on conflict(group_id,role_id) do update set expires_at=excluded.expires_at,
            assigned_by=excluded.assigned_by,assigned_at=now(),updated_at=now(),
            version=group_role_assignment.version+1 returning version
          """).param("subject", subjectId).param("role", roleId).param("expires", expiresAt)
          .param("actor", safeActor).query(Long.class).single();
      case "ACCESS_GROUP" -> database.sql("""
          insert into access_group_role_assignment(access_group_id,role_id,expires_at,assigned_by)
          values(:subject,:role,:expires,:actor)
          on conflict(access_group_id,role_id) do update set expires_at=excluded.expires_at,
            assigned_by=excluded.assigned_by,assigned_at=now(),updated_at=now(),
            version=access_group_role_assignment.version+1 returning version
          """).param("subject", subjectId).param("role", roleId).param("expires", expiresAt)
          .param("actor", safeActor).query(Long.class).single();
      default -> throw new IllegalArgumentException("unsupported subject type");
    };
  }

  @Override public OptionalLong roleAssignmentVersion(String type, UUID subjectId, UUID roleId) {
    Optional<Long> value = switch (type) {
      case "USER" -> database.sql("select version from user_role_assignment where user_id=:s and role_id=:r")
          .param("s", subjectId).param("r", roleId).query(Long.class).optional();
      case "DIRECTORY_GROUP" -> database.sql("select version from group_role_assignment where group_id=:s and role_id=:r")
          .param("s", subjectId).param("r", roleId).query(Long.class).optional();
      case "ACCESS_GROUP" -> database.sql("select version from access_group_role_assignment where access_group_id=:s and role_id=:r")
          .param("s", subjectId).param("r", roleId).query(Long.class).optional();
      default -> Optional.empty();
    };
    return value.isPresent() ? OptionalLong.of(value.get()) : OptionalLong.empty();
  }

  @Override public int deleteRoleAssignment(String type, UUID subjectId, UUID roleId) {
    return switch (type) {
      case "USER" -> database.sql("delete from user_role_assignment where user_id=:s and role_id=:r")
          .param("s", subjectId).param("r", roleId).update();
      case "DIRECTORY_GROUP" -> database.sql("delete from group_role_assignment where group_id=:s and role_id=:r")
          .param("s", subjectId).param("r", roleId).update();
      case "ACCESS_GROUP" -> database.sql("delete from access_group_role_assignment where access_group_id=:s and role_id=:r")
          .param("s", subjectId).param("r", roleId).update();
      default -> 0;
    };
  }

  @Override public void enqueueRoleAssignment(String type, UUID subjectId, UUID roleId,
      String event, long version) {
    database.sql("""
        insert into outbox_event(aggregate_type,aggregate_id,event_type,payload,idempotency_key)
        select 'role-assignment',r.id,:event,
          jsonb_build_object(
            'user',case :type when 'USER' then 'user:'||u.subject_key
              when 'DIRECTORY_GROUP' then 'group:'||dg.external_id||'#member'
              else 'group:'||lower(ag.code)||'#member' end,
            'relation','assignee','object','role:'||r.role_key),
          :event||':'||:type||':'||:subject||':'||:role||':'||:version
        from application_role r
        left join app_user u on :type='USER' and u.id=:subject
        left join directory_group dg on :type='DIRECTORY_GROUP' and dg.id=:subject
        left join access_group ag on :type='ACCESS_GROUP' and ag.id=:subject
        where r.id=:role and ((:type='USER' and u.id is not null)
          or (:type='DIRECTORY_GROUP' and dg.id is not null)
          or (:type='ACCESS_GROUP' and ag.id is not null))
        on conflict(idempotency_key) do nothing
        """).param("event", event).param("type", type).param("subject", subjectId)
        .param("role", roleId).param("version", version).update();
  }

  @Override public void appendAudit(String actor, String event, String type, String key) {
    database.sql("""
        insert into audit_event(actor_key,event_type,target_type,target_key,correlation_id)
        values(:actor,:event,:type,:key,:correlation)
        """).param("actor", bounded(actor, 255, "unknown")).param("event", event)
        .param("type", type).param("key", key).param("correlation", UUID.randomUUID().toString())
        .update();
  }

  private static UUID uuid(ResultSet rs, String name) throws SQLException {
    return rs.getObject(name, UUID.class);
  }
  private static Instant instant(ResultSet rs, String name) throws SQLException {
    var value = rs.getTimestamp(name);
    return value == null ? null : value.toInstant();
  }
  private static String bounded(String value, int size, String fallback) {
    String normalized = value == null || value.isBlank() ? fallback : value;
    return normalized.substring(0, Math.min(size, normalized.length()));
  }
}

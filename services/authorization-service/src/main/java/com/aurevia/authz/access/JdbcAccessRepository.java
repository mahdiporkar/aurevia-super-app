package com.aurevia.authz.access;

import static com.aurevia.authz.access.AccessModels.*;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** PostgreSQL adapter for access-control administration. */
@Repository
public class JdbcAccessRepository implements AccessRepository {
  private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};
  private final JdbcClient database;
  private final ObjectMapper json;

  public JdbcAccessRepository(JdbcClient database, ObjectMapper json) {
    this.database = database;
    this.json = json;
  }

  @Override
  public List<ResourceView> resources() {
    List<ResourceView> resources = database.sql("""
        select r.id,r.resource_key,r.type::text type,r.parent_id,r.name_fa,r.name_en,
          r.owner_domain,r.classification,r.external_system,r.external_type,r.external_id,
          r.source,r.metadata::text metadata,r.status::text status,r.version,
          count(distinct g.id) grant_count
        from resource r
        left join authorization_grant g on g.resource_id=r.id and g.status='ACTIVE'
        group by r.id order by r.resource_key
        """).query((rs, row) -> resource(rs, List.of())).list();
    Map<UUID, List<ActionSummary>> actionsByResource = new LinkedHashMap<>();
    database.sql("""
        select ra.resource_id,a.id,a.action_key,a.name_fa,a.name_en
        from resource_action ra join action a on a.id=ra.action_id
        order by ra.resource_id,a.action_key
        """).query((rs, row) -> new ResourceAction(
            uuid(rs, "resource_id"),
            new ActionSummary(uuid(rs, "id"), rs.getString("action_key"),
                rs.getString("name_fa"), rs.getString("name_en"))))
        .list().forEach(item -> actionsByResource
            .computeIfAbsent(item.resourceId(), ignored -> new ArrayList<>()).add(item.action()));
    return resources.stream().map(item -> new ResourceView(
        item.id(), item.resourceKey(), item.type(), item.parentId(), item.nameFa(), item.nameEn(),
        item.ownerDomain(), item.classification(), item.externalSystem(), item.externalType(),
        item.externalId(), item.source(), item.metadata(), item.status(), item.version(),
        item.grantCount(), List.copyOf(actionsByResource.getOrDefault(item.id(), List.of())))).toList();
  }

  @Override
  public List<ActionView> actions() {
    return database.sql("select id,action_key,name_fa,name_en from action order by action_key")
        .query((rs, row) -> new ActionView(uuid(rs, "id"), rs.getString("action_key"),
            rs.getString("name_fa"), rs.getString("name_en"))).list();
  }

  @Override
  public List<UserView> users() {
    return database.sql("""
        select u.id,u.issuer,u.external_id,u.subject_key,u.username,u.display_name,u.email,
          u.status::text status,u.version,u.membership_version,
          coalesce(array_agg(distinct o.external_path order by o.external_path)
            filter(where a.active and o.active),array[]::varchar[]) organizational_units
        from app_user u
        left join user_ou_assignment a on a.user_id=u.id
        left join directory_ou o on o.id=a.ou_id
        group by u.id order by u.username
        """).query((rs, row) -> new UserView(
            uuid(rs, "id"), rs.getString("issuer"), rs.getString("external_id"),
            rs.getString("subject_key"), rs.getString("username"), rs.getString("display_name"),
            rs.getString("email"), rs.getString("status"), rs.getLong("version"),
            rs.getLong("membership_version"), strings(rs.getArray("organizational_units")))).list();
  }

  @Override
  public List<GrantView> grants(String subjectType, UUID subjectId) {
    return database.sql("""
        select g.id,g.resource_id,r.resource_key,r.name_fa resource_name_fa,
          r.name_en resource_name_en,g.action_id,a.action_key,a.name_fa action_name_fa,
          g.relation,g.expires_at,g.status::text status,g.version
        from authorization_grant g join resource r on r.id=g.resource_id
        join action a on a.id=g.action_id
        where g.subject_type=cast(:type as subject_type) and g.subject_id=:id
          and g.status='ACTIVE'
        order by r.resource_key,a.action_key
        """).param("type", subjectType).param("id", subjectId)
        .query((rs, row) -> new GrantView(
            uuid(rs, "id"), uuid(rs, "resource_id"), rs.getString("resource_key"),
            rs.getString("resource_name_fa"), rs.getString("resource_name_en"),
            uuid(rs, "action_id"), rs.getString("action_key"), rs.getString("action_name_fa"),
            rs.getString("relation"), instant(rs, "expires_at"), rs.getString("status"),
            rs.getLong("version"))).list();
  }

  @Override
  public Optional<ResourceSnapshot> resource(UUID id) {
    return database.sql("select resource_key,parent_id from resource where id=:id")
        .param("id", id).query((rs, row) -> new ResourceSnapshot(
            rs.getString("resource_key"), uuid(rs, "parent_id"))).optional();
  }

  @Override
  public boolean resourceExists(UUID id) {
    return database.sql("select count(*) from resource where id=:id").param("id", id)
        .query(Long.class).single() > 0;
  }

  @Override
  public boolean resourceHierarchyContains(UUID ancestorId, UUID candidateId) {
    return database.sql("""
        with recursive ancestors as (
          select id,parent_id from resource where id=:ancestor
          union all
          select r.id,r.parent_id from resource r join ancestors a on r.id=a.parent_id)
        select count(*) from ancestors where id=:candidate
        """).param("ancestor", ancestorId).param("candidate", candidateId)
        .query(Long.class).single() > 0;
  }

  @Override
  public void createResource(UUID id, ResourceCommand c, String source) {
    database.sql("""
        insert into resource(id,resource_key,type,parent_id,name_fa,name_en,owner_domain,
          classification,external_system,external_type,external_id,source,metadata,status)
        values(:id,:key,cast(:type as resource_type),:parent,:fa,:en,:owner,:classification,
          :system,:externalType,:externalId,:source,cast(:metadata as jsonb),'ACTIVE')
        """).param("id", id).param("key", c.resourceKey()).param("type", c.type())
        .param("parent", c.parentId()).param("fa", c.nameFa()).param("en", c.nameEn())
        .param("owner", c.ownerDomain()).param("classification", c.classification())
        .param("system", c.externalSystem()).param("externalType", c.externalType())
        .param("externalId", c.externalId()).param("source", source)
        .param("metadata", write(c.metadata())).update();
  }

  @Override
  public int updateResource(UUID id, long version, ResourceCommand c, String source) {
    return database.sql("""
        update resource set type=cast(:type as resource_type),parent_id=:parent,name_fa=:fa,
          name_en=:en,owner_domain=:owner,classification=:classification,
          external_system=:system,external_type=:externalType,external_id=:externalId,
          source=:source,metadata=cast(:metadata as jsonb),version=version+1,updated_at=now()
        where id=:id and version=:version
        """).param("id", id).param("version", version).param("type", c.type())
        .param("parent", c.parentId()).param("fa", c.nameFa()).param("en", c.nameEn())
        .param("owner", c.ownerDomain()).param("classification", c.classification())
        .param("system", c.externalSystem()).param("externalType", c.externalType())
        .param("externalId", c.externalId()).param("source", source)
        .param("metadata", write(c.metadata())).update();
  }

  @Override public void createAction(UUID id, ActionCommand c) {
    database.sql("insert into action(id,action_key,name_fa,name_en) values(:id,:key,:fa,:en)")
        .param("id", id).param("key", c.actionKey()).param("fa", c.nameFa())
        .param("en", c.nameEn()).update();
  }
  @Override public void attachAction(UUID resourceId, UUID actionId) {
    database.sql("insert into resource_action(resource_id,action_id) values(:r,:a) on conflict do nothing")
        .param("r", resourceId).param("a", actionId).update();
  }
  @Override public void detachAction(UUID resourceId, UUID actionId) {
    database.sql("delete from resource_action where resource_id=:r and action_id=:a")
        .param("r", resourceId).param("a", actionId).update();
  }
  @Override public void createUser(UUID id, UserCommand c) {
    database.sql("""
        insert into app_user(id,issuer,external_id,username,display_name,email)
        values(:id,:issuer,:external,:username,:display,:email)
        """).param("id", id).param("issuer", c.issuer()).param("external", c.externalId())
        .param("username", c.username()).param("display", c.displayName())
        .param("email", c.email()).update();
  }

  @Override public Optional<GrantTarget> grantTarget(UUID resourceId, UUID actionId) {
    return database.sql("""
        select r.type::text resource_type,a.action_key from resource r
        join resource_action ra on ra.resource_id=r.id join action a on a.id=ra.action_id
        where r.id=:resource and a.id=:action and r.status='ACTIVE'
        """).param("resource", resourceId).param("action", actionId)
        .query(GrantTarget.class).optional();
  }
  @Override public void archiveExpiredGrant(String type, UUID subject, UUID resource, UUID action) {
    database.sql("""
        update authorization_grant set status='ARCHIVED',version=version+1
        where subject_type=cast(:type as subject_type) and subject_id=:subject
          and resource_id=:resource and action_id=:action and status='ACTIVE' and expires_at<=now()
        """).param("type", type).param("subject", subject).param("resource", resource)
        .param("action", action).update();
  }
  @Override public Optional<ExistingGrant> activeGrant(String type, UUID subject, UUID resource,
      UUID action) {
    return database.sql("""
        select id,version from authorization_grant
        where subject_type=cast(:type as subject_type) and subject_id=:subject
          and resource_id=:resource and action_id=:action and status='ACTIVE'
        """).param("type", type).param("subject", subject).param("resource", resource)
        .param("action", action).query(ExistingGrant.class).optional();
  }
  @Override public void createGrant(UUID id, String type, UUID subject, UUID resource, UUID action,
      String relation, Instant expiresAt) {
    database.sql("""
        insert into authorization_grant(id,subject_type,subject_id,resource_id,action_id,relation,expires_at)
        values(:id,cast(:type as subject_type),:subject,:resource,:action,:relation,:expires)
        """).param("id", id).param("type", type).param("subject", subject)
        .param("resource", resource).param("action", action).param("relation", relation)
        .param("expires", expiresAt).update();
  }
  @Override public boolean isActiveGrant(UUID id) {
    return database.sql("select count(*) from authorization_grant where id=:id and status='ACTIVE'")
        .param("id", id).query(Long.class).single() > 0;
  }
  @Override public void archiveGrant(UUID id) {
    database.sql("update authorization_grant set status='ARCHIVED',version=version+1 where id=:id and status='ACTIVE'")
        .param("id", id).update();
  }

  @Override public void enqueueGrant(UUID id, String event, long version) {
    database.sql("""
        insert into outbox_event(aggregate_type,aggregate_id,event_type,payload,idempotency_key)
        select 'grant',g.id,:event,
          jsonb_build_object(
            'user',case g.subject_type when 'USER' then 'user:'||u.subject_key when 'GROUP' then 'group:'||dg.external_id||'#member' when 'ACCESS_GROUP' then 'group:'||lower(ag.code)||'#member' when 'ROLE' then 'role:'||ar.role_key||'#assignee' end,
            'relation',g.relation,
            'object',case when r.type='APPLICATION' then 'application:'||regexp_replace(r.resource_key,'^application:','') when r.type='EXTERNAL_RESOURCE' then 'external_resource:'||replace(regexp_replace(r.resource_key,'^external_resource:',''),':','/') else 'resource:'||replace(r.resource_key,':','/') end),
          :event||':'||g.id||':'||cast(:version as text)
        from authorization_grant g
        left join app_user u on g.subject_type='USER' and u.id=g.subject_id
        left join directory_group dg on g.subject_type='GROUP' and dg.id=g.subject_id
        left join access_group ag on g.subject_type='ACCESS_GROUP' and ag.id=g.subject_id
        left join application_role ar on g.subject_type='ROLE' and ar.id=g.subject_id
        join resource r on r.id=g.resource_id where g.id=:id
        on conflict(idempotency_key) do nothing
        """).param("id", id).param("event", event).param("version", version).update();
  }

  @Override public void enqueueParent(UUID childId, UUID parentId, String event) {
    if (parentId == null) return;
    database.sql("""
        insert into outbox_event(aggregate_type,aggregate_id,event_type,payload,idempotency_key)
        select 'resource',c.id,:event,
          jsonb_build_object('user',case when p.type='APPLICATION' then 'application:'||regexp_replace(p.resource_key,'^application:','') when p.type='EXTERNAL_RESOURCE' then 'external_resource:'||replace(regexp_replace(p.resource_key,'^external_resource:',''),':','/') else 'resource:'||replace(p.resource_key,':','/') end,'relation','parent','object',case when c.type='APPLICATION' then 'application:'||regexp_replace(c.resource_key,'^application:','') when c.type='EXTERNAL_RESOURCE' then 'external_resource:'||replace(regexp_replace(c.resource_key,'^external_resource:',''),':','/') else 'resource:'||replace(c.resource_key,':','/') end),
          :event||':'||c.id||':'||p.id||':'||c.version
        from resource c join resource p on p.id=:parent where c.id=:child
        on conflict(idempotency_key) do nothing
        """).param("event", event).param("parent", parentId).param("child", childId).update();
  }

  @Override public void appendAudit(String actor, String event, String type, String key) {
    database.sql("""
        insert into audit_event(actor_key,event_type,target_type,target_key,correlation_id)
        values(:actor,:event,:type,:key,:correlation)
        """).param("actor", bounded(actor, 255, "unknown")).param("event", event)
        .param("type", type).param("key", key).param("correlation", UUID.randomUUID().toString())
        .update();
  }

  private ResourceView resource(ResultSet rs, List<ActionSummary> actions) throws SQLException {
    return new ResourceView(uuid(rs, "id"), rs.getString("resource_key"), rs.getString("type"),
        uuid(rs, "parent_id"), rs.getString("name_fa"), rs.getString("name_en"),
        rs.getString("owner_domain"), rs.getString("classification"),
        rs.getString("external_system"), rs.getString("external_type"),
        rs.getString("external_id"), rs.getString("source"), readMap(rs.getString("metadata")),
        rs.getString("status"), rs.getLong("version"), rs.getLong("grant_count"), actions);
  }

  private Map<String, Object> readMap(String value) {
    try { return value == null ? Map.of() : json.readValue(value, MAP_TYPE); }
    catch (Exception error) { throw new IllegalStateException("Invalid resource metadata", error); }
  }
  private String write(Map<String, Object> value) {
    try { return json.writeValueAsString(value == null ? Map.of() : value); }
    catch (Exception error) { throw new IllegalArgumentException("invalid metadata", error); }
  }
  private static UUID uuid(ResultSet rs, String name) throws SQLException {
    return rs.getObject(name, UUID.class);
  }
  private static Instant instant(ResultSet rs, String name) throws SQLException {
    var timestamp = rs.getTimestamp(name);
    return timestamp == null ? null : timestamp.toInstant();
  }
  private static List<String> strings(Array value) throws SQLException {
    if (value == null) return List.of();
    Object array = value.getArray();
    if (array instanceof String[] strings) return List.of(strings);
    Object[] objects = (Object[]) array;
    List<String> result = new ArrayList<>(objects.length);
    for (Object item : objects) result.add(String.valueOf(item));
    return List.copyOf(result);
  }
  private static String bounded(String value, int size, String fallback) {
    String normalized = value == null || value.isBlank() ? fallback : value;
    return normalized.substring(0, Math.min(size, normalized.length()));
  }
  private record ResourceAction(UUID resourceId, ActionSummary action) {}
}

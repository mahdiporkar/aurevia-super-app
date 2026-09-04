package com.aurevia.authz.superset;

import static com.aurevia.authz.superset.SupersetAssetModels.*;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcSupersetAssetRepository implements SupersetAssetRepository {
  private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {};
  private final JdbcClient database;
  private final ObjectMapper json;
  public JdbcSupersetAssetRepository(JdbcClient database, ObjectMapper json) {
    this.database = database;
    this.json = json;
  }

  @Override public List<AssetView> assets() {
    return database.sql("""
        select sa.id,sa.resource_id,sa.instance_id,si.code instance_code,sa.external_id,
          sa.asset_type,sa.title,sa.url_path,sa.owner_external_id,sa.published,
          sa.tags::text tags,r.resource_key,r.name_fa,r.name_en
        from superset_asset sa join superset_instance si on si.id=sa.instance_id
        join resource r on r.id=sa.resource_id order by sa.asset_type,sa.title
        """).query((rs, row) -> asset(rs)).list();
  }

  @Override public List<AssetView> publishedAssets(String instanceCode) {
    return database.sql("""
        select sa.id,sa.resource_id,sa.instance_id,si.code instance_code,sa.external_id,
          sa.asset_type,sa.title,sa.url_path,sa.owner_external_id,sa.published,
          sa.tags::text tags,r.resource_key,r.name_fa,r.name_en
        from superset_asset sa join superset_instance si on si.id=sa.instance_id and si.active
        join resource r on r.id=sa.resource_id
        where sa.published=true and si.code=:instance order by sa.title
        """).param("instance", instanceCode).query((rs, row) -> asset(rs)).list();
  }

  @Override public List<AssetGrantView> grants(UUID assetId) {
    return database.sql("""
        select g.id,g.subject_type::text subject_type,g.subject_id,
          coalesce(u.display_name,dg.display_name,ag.name,ar.name_fa) subject_name,
          coalesce(u.username,dg.normalized_path,ag.code,ar.role_key) subject_key,
          a.action_key,g.relation,g.expires_at
        from superset_asset sa join authorization_grant g
          on g.resource_id=sa.resource_id and g.status='ACTIVE'
        left join app_user u on g.subject_type='USER' and u.id=g.subject_id
        left join directory_group dg on g.subject_type='GROUP' and dg.id=g.subject_id
        left join access_group ag on g.subject_type='ACCESS_GROUP' and ag.id=g.subject_id
        left join application_role ar on g.subject_type='ROLE' and ar.id=g.subject_id
        join action a on a.id=g.action_id
        where sa.id=:asset and (g.expires_at is null or g.expires_at>now())
        order by subject_name,a.action_key
        """).param("asset", assetId).query((rs, row) -> new AssetGrantView(
            uuid(rs, "id"), rs.getString("subject_type"), uuid(rs, "subject_id"),
            rs.getString("subject_name"), rs.getString("subject_key"),
            rs.getString("action_key"), rs.getString("relation"), instant(rs, "expires_at"))).list();
  }

  @Override public List<SubjectOption> grantSubjects() {
    return database.sql("""
        select id,'USER' type,username key,coalesce(display_name,username) label
          from app_user where status='ACTIVE'
        union all
        select id,'GROUP',external_id,display_name from directory_group where status='ACTIVE'
        union all
        select id,'ACCESS_GROUP',code,name from access_group where active
        union all
        select id,'ROLE',role_key,name_fa from application_role where status='ACTIVE'
        order by type,label
        """).query((rs, row) -> new SubjectOption(uuid(rs, "id"), rs.getString("type"),
            rs.getString("key"), rs.getString("label"))).list();
  }

  @Override public Optional<OperationInstance> activeOperationInstance(String code) {
    return database.sql("""
        select id,code from superset_instance where code=:code and zone='OPERATION' and active
        """).param("code", code).query(OperationInstance.class).optional();
  }

  @Override public Optional<ExistingAsset> existing(UUID instanceId, String externalId,
      String assetType) {
    return database.sql("""
        select sa.id,sa.resource_id,r.resource_key from superset_asset sa
        join resource r on r.id=sa.resource_id
        where sa.instance_id=:instance and sa.external_id=:external and sa.asset_type=:type
        """).param("instance", instanceId).param("external", externalId).param("type", assetType)
        .query(ExistingAsset.class).optional();
  }

  @Override public UUID catalogParentId() {
    return database.sql("select id from resource where resource_key='external_resource:superset-public'")
        .query(UUID.class).single();
  }

  @Override public void create(UUID assetId, UUID resourceId, UUID parentResourceId,
      UUID instanceId, AssetCommand command, String resourceKey) {
    database.sql("""
        insert into resource(id,resource_key,type,parent_id,name_fa,name_en,owner_domain,
          external_system,external_type,external_id,source)
        values(:id,:key,'EXTERNAL_RESOURCE',:parent,:title,:title,'reports',:instanceCode,
          :assetType,:externalId,'EXTERNAL_SYNC')
        """).param("id", resourceId).param("key", resourceKey).param("parent", parentResourceId)
        .param("title", command.title()).param("instanceCode", command.instanceCode())
        .param("assetType", command.assetType()).param("externalId", command.externalId()).update();
    database.sql("""
        insert into resource_external_binding(resource_id,provider,external_type,external_id,metadata)
        values(:resource,:provider,:type,:external,cast(:metadata as jsonb))
        """).param("resource", resourceId).param("provider", command.instanceCode())
        .param("type", command.assetType()).param("external", command.externalId())
        .param("metadata", "{}").update();
    database.sql("""
        insert into resource_action(resource_id,action_id)
        select :resource,id from action where action_key in ('view','update','admin')
        on conflict do nothing
        """).param("resource", resourceId).update();
    database.sql("""
        insert into superset_asset(id,resource_id,instance_id,external_id,asset_type,title,
          url_path,owner_external_id,published,tags,synchronized_at)
        values(:id,:resource,:instance,:external,:type,:title,:path,:owner,:published,'[]'::jsonb,now())
        """).param("id", assetId).param("resource", resourceId).param("instance", instanceId)
        .param("external", command.externalId()).param("type", command.assetType())
        .param("title", command.title()).param("path", command.urlPath())
        .param("owner", command.ownerExternalId()).param("published", command.published()).update();
    database.sql("""
        insert into outbox_event(aggregate_type,aggregate_id,event_type,payload,idempotency_key)
        values('resource',:child,'RESOURCE_PARENT_WRITE',
          jsonb_build_object('user','external_resource:superset-public','relation','parent',
            'object','external_resource:'||replace(regexp_replace(:key,'^external_resource:',''),':','/')),
          'SUPERSET_PARENT_WRITE:'||:child)
        """).param("child", resourceId).param("key", resourceKey).update();
  }

  @Override public Optional<GrantTarget> grantTarget(UUID assetId, String actionKey) {
    return database.sql("""
        select sa.resource_id,a.id action_id from superset_asset sa
        join resource_action ra on ra.resource_id=sa.resource_id
        join action a on a.id=ra.action_id and a.action_key=:action
        where sa.id=:asset
        """).param("asset", assetId).param("action", actionKey)
        .query(GrantTarget.class).optional();
  }

  @Override public boolean grantBelongsToAsset(UUID assetId, UUID grantId) {
    return database.sql("""
        select count(*) from superset_asset sa join authorization_grant g
          on g.resource_id=sa.resource_id and g.status='ACTIVE'
        where sa.id=:asset and g.id=:grant
        """).param("asset", assetId).param("grant", grantId).query(Long.class).single() > 0;
  }

  private AssetView asset(ResultSet rs) throws SQLException {
    return new AssetView(uuid(rs, "id"), uuid(rs, "resource_id"), uuid(rs, "instance_id"),
        rs.getString("instance_code"), rs.getString("external_id"), rs.getString("asset_type"),
        rs.getString("title"), rs.getString("url_path"), rs.getString("owner_external_id"),
        rs.getBoolean("published"), tags(rs.getString("tags")), rs.getString("resource_key"),
        rs.getString("name_fa"), rs.getString("name_en"));
  }
  private List<String> tags(String value) {
    try { return value == null ? List.of() : json.readValue(value, STRING_LIST); }
    catch (Exception error) { throw new IllegalStateException("Invalid Superset tags", error); }
  }
  private static UUID uuid(ResultSet rs, String name) throws SQLException {
    return rs.getObject(name, UUID.class);
  }
  private static Instant instant(ResultSet rs, String name) throws SQLException {
    var value = rs.getTimestamp(name);
    return value == null ? null : value.toInstant();
  }
}

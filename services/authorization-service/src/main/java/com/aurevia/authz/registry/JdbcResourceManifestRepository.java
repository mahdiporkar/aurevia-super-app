package com.aurevia.authz.registry;

import static com.aurevia.authz.api.dto.ResourceManifestDtos.ResourceDefinition;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Array;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.time.Instant;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcResourceManifestRepository implements ResourceManifestRepository {
  private static final TypeReference<Map<String,Object>> MAP_TYPE=new TypeReference<>() {};
  private final JdbcClient database;
  private final ObjectMapper json;

  JdbcResourceManifestRepository(JdbcClient database,ObjectMapper json) {
    this.database=database;this.json=json;
  }

  @Override public List<ResourceDefinition> definitionTree(String rootKey) {
    return database.sql("""
        with recursive tree as (
          select * from resource where resource_key=:root
          union all select r.* from resource r join tree p on r.parent_id=p.id
        )
        select t.resource_key,t.type::text,p.resource_key parent_key,t.name_fa,t.name_en,
          t.owner_domain,t.classification,t.status::text,t.source,t.metadata::text,
          coalesce(actions.action_keys,array[]::varchar[]) actions,
          t.external_system,t.external_type,t.external_id
        from tree t left join resource p on p.id=t.parent_id
        left join lateral (
          select array_agg(a.action_key order by a.action_key) action_keys
          from resource_action ra join action a on a.id=ra.action_id
          where ra.resource_id=t.id
        ) actions on true
        order by t.resource_key
        """).param("root",rootKey).query((result,row)->new ResourceDefinition(
            result.getString("resource_key"),result.getString("type"),
            result.getString("parent_key"),result.getString("name_fa"),
            result.getString("name_en"),result.getString("owner_domain"),
            result.getString("classification"),strings(result.getArray("actions")),
            result.getString("status"),result.getString("source"),
            readMap(result.getString("metadata")),result.getString("external_system"),
            result.getString("external_type"),result.getString("external_id"))).list();
  }

  @Override public String latestVersion(String rootKey) {
    return database.sql("""
        select coalesce(max(manifest_version),'catalog')
        from resource_manifest_import where application_key=:root
        """).param("root",rootKey).query(String.class).single();
  }

  @Override public Optional<PanelManifestSettings> panelSettings(UUID panelId) {
    return database.sql("""
        select id,slug,name_fa as "nameFa",name_en as "nameEn",
          resource_definition_mode as "resourceDefinitionMode",
          resource_manifest_url as "resourceManifestUrl"
        from panel where id=:id
        """).param("id",panelId).query(PanelManifestSettings.class).optional();
  }

  @Override public Optional<PanelManifestSettings> panelSettingsBySlug(String slug) {
    return database.sql("""
        select id,slug,name_fa as "nameFa",name_en as "nameEn",
          resource_definition_mode as "resourceDefinitionMode",
          resource_manifest_url as "resourceManifestUrl"
        from panel where slug=:slug
        """).param("slug",slug).query(PanelManifestSettings.class).optional();
  }

  @Override public List<DraftRecord> drafts(UUID panelId) {
    return database.sql("""
        select id,panel_id,application_key,manifest_version,schema_version,checksum,
          imported_by,source_url,payload::text,workflow_status,diff_summary::text,
          created_at,published_at,published_by
        from resource_manifest_import where panel_id=:panel order by created_at desc
        """).param("panel",panelId).query(this::draft).list();
  }

  @Override public Optional<DraftRecord> draft(UUID panelId,UUID draftId) {
    return database.sql("""
        select id,panel_id,application_key,manifest_version,schema_version,checksum,
          imported_by,source_url,payload::text,workflow_status,diff_summary::text,
          created_at,published_at,published_by
        from resource_manifest_import where panel_id=:panel and id=:id
        """).param("panel",panelId).param("id",draftId).query(this::draft).optional();
  }

  @Override public Optional<DraftRecord> revisionByVersion(UUID panelId,String version) {
    return database.sql("""
        select id,panel_id,application_key,manifest_version,schema_version,checksum,
          imported_by,source_url,payload::text,workflow_status,diff_summary::text,
          created_at,published_at,published_by
        from resource_manifest_import
        where panel_id=:panel and manifest_version=:version
        """).param("panel",panelId).param("version",version)
        .query(this::draft).optional();
  }

  @Override public boolean insertDraft(DraftInsert value) {
    return database.sql("""
        insert into resource_manifest_import(id,panel_id,application_key,manifest_version,
          schema_version,checksum,imported_by,source_url,payload,workflow_status,diff_summary)
        values(:id,:panel,:app,:version,:schema,:checksum,:actor,:source,
          cast(:payload as jsonb),'DRAFT',cast(:diff as jsonb))
        on conflict(panel_id,manifest_version) where panel_id is not null do nothing
        """).param("id",value.id()).param("panel",value.panelId())
        .param("app",value.applicationKey()).param("version",value.manifestVersion())
        .param("schema",value.schemaVersion()).param("checksum",value.checksum())
        .param("actor",value.actor()).param("source",value.sourceUrl())
        .param("payload",value.payload()).param("diff",value.diffSummary()).update()==1;
  }

  @Override public boolean markPublished(UUID draftId,String actor) {
    return database.sql("""
        update resource_manifest_import set workflow_status='PUBLISHED',published_at=now(),
          published_by=:actor,imported_at=now() where id=:id and workflow_status='DRAFT'
        """).param("id",draftId).param("actor",actor).update()==1;
  }

  @Override public boolean resourceExists(String resourceKey) {
    return database.sql("select count(*) from resource where resource_key=:key")
        .param("key",resourceKey).query(Long.class).single()>0;
  }

  @Override public Optional<ResourceOwnership> resourceOwnership(String resourceKey) {
    return database.sql("""
        select type::text,source,panel_id as "panelId",parent_id as "parentId",name_fa as "nameFa",
          name_en as "nameEn",status::text from resource where resource_key=:key
        """).param("key",resourceKey).query(ResourceOwnership.class).optional();
  }

  @Override public boolean actionExists(String actionKey) {
    return database.sql("select count(*) from action where action_key=:action")
        .param("action",actionKey).query(Long.class).single()>0;
  }

  @Override public boolean resourceActionExists(String resourceKey,String actionKey) {
    return database.sql("""
        select count(*) from resource r join resource_action ra on ra.resource_id=r.id
        join action a on a.id=ra.action_id
        where r.resource_key=:resource and a.action_key=:action and r.status='ACTIVE'
        """).param("resource",resourceKey).param("action",actionKey)
        .query(Long.class).single()>0;
  }

  @Override public Optional<UUID> resourceId(String resourceKey) {
    return database.sql("select id from resource where resource_key=:key")
        .param("key",resourceKey).query(UUID.class).optional();
  }

  @Override public void upsertResource(ResourceDefinition value,UUID parentId,String metadata,
      UUID panelId,String manifestVersion) {
    database.sql("""
        insert into resource(resource_key,type,parent_id,name_fa,name_en,owner_domain,
          classification,status,source,panel_id,manifest_version,visibility_enabled,
          metadata,external_system,external_type,external_id)
        values(:key,cast(:type as resource_type),:parent,:fa,:en,:owner,:classification,
          'ACTIVE','MANIFEST',:panel,:manifestVersion,true,cast(:metadata as jsonb),
          :provider,:externalType,:externalId)
        on conflict(resource_key) do update set parent_id=excluded.parent_id,
          name_fa=excluded.name_fa,name_en=excluded.name_en,owner_domain=excluded.owner_domain,
          classification=excluded.classification,status='ACTIVE',metadata=excluded.metadata,
          panel_id=excluded.panel_id,manifest_version=excluded.manifest_version,
          external_system=excluded.external_system,external_type=excluded.external_type,
          external_id=excluded.external_id,version=resource.version+1,updated_at=now()
        """).param("key",value.key()).param("type",value.type()).param("parent",parentId)
        .param("fa",value.nameFa()).param("en",value.nameEn())
        .param("owner",value.ownerDomain()).param("classification",value.classification())
        .param("panel",panelId).param("manifestVersion",manifestVersion)
        .param("metadata",metadata).param("provider",value.provider())
        .param("externalType",value.externalType()).param("externalId",value.externalId()).update();
  }

  @Override public void upsertExternalBinding(ResourceDefinition value,String metadata) {
    database.sql("""
        insert into resource_external_binding(resource_id,provider,external_type,external_id,metadata)
        select id,:provider,:externalType,:externalId,cast(:metadata as jsonb)
        from resource where resource_key=:key
        on conflict(provider,external_type,external_id) do update set
          resource_id=excluded.resource_id,metadata=excluded.metadata,active=true,
          version=resource_external_binding.version+1,updated_at=now()
        """).param("provider",value.provider()).param("externalType",value.externalType())
        .param("externalId",value.externalId()).param("metadata",metadata)
        .param("key",value.key()).update();
  }

  @Override public void clearActions(UUID resourceId) {
    database.sql("delete from resource_action where resource_id=:id")
        .param("id",resourceId).update();
  }

  @Override public boolean addAction(UUID resourceId,String actionKey) {
    return database.sql("""
        insert into resource_action(resource_id,action_id)
        select :id,id from action where action_key=:action on conflict do nothing
        """).param("id",resourceId).param("action",actionKey).update()==1;
  }

  @Override public void enqueueParent(UUID childId,UUID parentId,String eventType) {
    if(parentId==null)return;
    database.sql("""
        insert into outbox_event(aggregate_type,aggregate_id,event_type,payload,idempotency_key)
        select 'resource',c.id,:event,
          jsonb_build_object(
            'user',case when p.type='APPLICATION' then 'application:'||regexp_replace(p.resource_key,'^application:','') when p.type='EXTERNAL_RESOURCE' then 'external_resource:'||replace(regexp_replace(p.resource_key,'^external_resource:',''),':','/') else 'resource:'||replace(p.resource_key,':','/') end,
            'relation','parent',
            'object',case when c.type='APPLICATION' then 'application:'||regexp_replace(c.resource_key,'^application:','') when c.type='EXTERNAL_RESOURCE' then 'external_resource:'||replace(regexp_replace(c.resource_key,'^external_resource:',''),':','/') else 'resource:'||replace(c.resource_key,':','/') end),
          :event||':'||c.id||':'||p.id||':'||c.version
        from resource c join resource p on p.id=:parent where c.id=:child
        on conflict(idempotency_key) do nothing
        """).param("event",eventType).param("parent",parentId).param("child",childId).update();
  }

  @Override public int deprecateMissing(UUID panelId,String rootKey,String[] retainedKeys) {
    return database.sql("""
        with recursive tree as (
          select id,resource_key from resource where resource_key=:root
          union all select r.id,r.resource_key from resource r join tree p on r.parent_id=p.id
        )
        update resource set status='DEPRECATED',version=version+1,updated_at=now()
        where id in(select id from tree) and source='MANIFEST' and panel_id=:panel
          and not(resource_key=any(:keys)) and status<>'DEPRECATED'
        """).param("root",rootKey).param("panel",panelId).param("keys",retainedKeys).update();
  }

  private DraftRecord draft(java.sql.ResultSet result,int row) throws java.sql.SQLException {
    return new DraftRecord(result.getObject("id",UUID.class),
        result.getObject("panel_id",UUID.class),result.getString("application_key"),
        result.getString("manifest_version"),result.getString("schema_version"),
        result.getString("checksum"),result.getString("imported_by"),
        result.getString("source_url"),result.getString("payload"),
        result.getString("workflow_status"),result.getString("diff_summary"),
        instant(result,"created_at"),instant(result,"published_at"),
        result.getString("published_by"));
  }

  private Map<String,Object> readMap(String value) {
    try { return value==null?Map.of():json.readValue(value,MAP_TYPE); }
    catch(Exception failure) { throw new IllegalStateException("Invalid resource metadata",failure); }
  }

  private static List<String> strings(Array array) {
    try { return array==null?List.of():Arrays.stream((Object[])array.getArray())
        .map(String::valueOf).toList(); }
    catch(Exception failure) { throw new IllegalStateException("Invalid action array",failure); }
  }

  private static Instant instant(java.sql.ResultSet result,String column)
      throws java.sql.SQLException {
    var value=result.getTimestamp(column);
    return value==null?null:value.toInstant();
  }
}

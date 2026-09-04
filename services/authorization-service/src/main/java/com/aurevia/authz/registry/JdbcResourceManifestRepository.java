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
          coalesce(array_agg(a.action_key order by a.action_key)
            filter(where a.id is not null),array[]::varchar[]) actions,
          t.external_system,t.external_type,t.external_id
        from tree t left join resource p on p.id=t.parent_id
        left join resource_action ra on ra.resource_id=t.id
        left join action a on a.id=ra.action_id
        group by t.id,p.resource_key order by t.resource_key
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

  @Override public boolean importExists(String applicationKey,String version,String checksum) {
    return database.sql("""
        select count(*) from resource_manifest_import
        where application_key=:app and manifest_version=:version and checksum=:checksum
        """).param("app",applicationKey).param("version",version)
        .param("checksum",checksum).query(Long.class).single()>0;
  }

  @Override public boolean resourceExists(String resourceKey) {
    return database.sql("select count(*) from resource where resource_key=:key")
        .param("key",resourceKey).query(Long.class).single()>0;
  }

  @Override public Optional<String> resourceType(String resourceKey) {
    return database.sql("select type::text from resource where resource_key=:key")
        .param("key",resourceKey).query(String.class).optional();
  }

  @Override public Optional<UUID> resourceId(String resourceKey) {
    return database.sql("select id from resource where resource_key=:key")
        .param("key",resourceKey).query(UUID.class).optional();
  }

  @Override public void upsertResource(ResourceDefinition value,UUID parentId,String metadata) {
    database.sql("""
        insert into resource(resource_key,type,parent_id,name_fa,name_en,owner_domain,
          classification,status,source,metadata,external_system,external_type,external_id)
        values(:key,cast(:type as resource_type),:parent,:fa,:en,:owner,:classification,
          'ACTIVE','APPLICATION_MANIFEST',cast(:metadata as jsonb),:provider,:externalType,:externalId)
        on conflict(resource_key) do update set parent_id=excluded.parent_id,
          name_fa=excluded.name_fa,name_en=excluded.name_en,owner_domain=excluded.owner_domain,
          classification=excluded.classification,status='ACTIVE',metadata=excluded.metadata,
          external_system=excluded.external_system,external_type=excluded.external_type,
          external_id=excluded.external_id,version=resource.version+1,updated_at=now()
        """).param("key",value.key()).param("type",value.type()).param("parent",parentId)
        .param("fa",value.nameFa()).param("en",value.nameEn())
        .param("owner",value.ownerDomain()).param("classification",value.classification())
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

  @Override public int deprecateMissing(String rootKey,String[] retainedKeys) {
    return database.sql("""
        with recursive tree as (
          select id,resource_key from resource where resource_key=:root
          union all select r.id,r.resource_key from resource r join tree p on r.parent_id=p.id
        )
        update resource set status='DEPRECATED',version=version+1,updated_at=now()
        where id in(select id from tree) and source='APPLICATION_MANIFEST'
          and not(resource_key=any(:keys)) and status<>'DEPRECATED'
        """).param("root",rootKey).param("keys",retainedKeys).update();
  }

  @Override public void insertImport(String applicationKey,String version,String checksum,
      String actor,String payload) {
    database.sql("""
        insert into resource_manifest_import(application_key,manifest_version,checksum,
          imported_by,payload)
        values(:app,:version,:checksum,:actor,cast(:payload as jsonb))
        """).param("app",applicationKey).param("version",version).param("checksum",checksum)
        .param("actor",actor).param("payload",payload).update();
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
}

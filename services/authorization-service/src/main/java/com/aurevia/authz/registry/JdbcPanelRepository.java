package com.aurevia.authz.registry;

import static com.aurevia.authz.registry.PanelModels.*;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcPanelRepository implements PanelRepository {
  private static final TypeReference<Map<String,Object>> MAP=new TypeReference<>(){};
  private final JdbcClient database;private final ObjectMapper json;
  public JdbcPanelRepository(JdbcClient database,ObjectMapper json){this.database=database;this.json=json;}
  @Override public List<PanelView> panels(){return database.sql("""
    select id,code,name_fa,name_en,description,slug,service_slug,remote_name,default_route_id,
      remote_entry_path,exposed_module,route_base_path,semantic_version,contract_version,
      integrity,active,sort_order,active_artifact_id,version from panel order by sort_order,code
    """).query((rs,row)->new PanelView(uuid(rs,"id"),rs.getString("code"),rs.getString("name_fa"),
      rs.getString("name_en"),rs.getString("description"),rs.getString("slug"),rs.getString("service_slug"),
      rs.getString("remote_name"),rs.getString("default_route_id"),rs.getString("remote_entry_path"),
      rs.getString("exposed_module"),rs.getString("route_base_path"),rs.getString("semantic_version"),
      rs.getString("contract_version"),rs.getString("integrity"),rs.getBoolean("active"),
      rs.getInt("sort_order"),uuid(rs,"active_artifact_id"),rs.getLong("version"))).list();}
  @Override public List<AuditView> audit(int limit){return database.sql("""
    select id,actor_key,event_type,target_type,target_key,correlation_id,safe_details::text details,
      occurred_at from audit_event order by occurred_at desc limit :limit
    """).param("limit",limit).query((rs,row)->new AuditView(uuid(rs,"id"),rs.getString("actor_key"),
      rs.getString("event_type"),rs.getString("target_type"),rs.getString("target_key"),
      rs.getString("correlation_id"),read(rs.getString("details")),instant(rs,"occurred_at"))).list();}
  @Override public Optional<String> routePath(UUID id){if(id==null)return Optional.empty();return database.sql("select route_base_path from panel where id=:id").param("id",id).query(String.class).optional();}
  @Override public boolean routePathExists(String path,UUID excludingId){var statement=database.sql("select count(*) from panel where route_base_path=:path"+(excludingId==null?"":" and id<>:id")).param("path",path);if(excludingId!=null)statement=statement.param("id",excludingId);return statement.query(Long.class).single()>0;}
  @Override public void create(UUID id,PanelCommand p,String service,String remoteName,String defaultRoute){database.sql("""
    insert into panel(id,code,name_fa,name_en,description,slug,service_slug,remote_name,default_route_id,
      remote_entry_path,exposed_module,route_base_path,semantic_version,contract_version,integrity,active,sort_order)
    values(:id,:code,:fa,:en,:description,:slug,:service,:remoteName,:defaultRoute,:remote,:module,
      :route,:semver,:contract,:integrity,:active,:sort)
    """).param("id",id).param("code",p.code()).param("fa",p.nameFa()).param("en",p.nameEn())
      .param("description",p.description()).param("slug",p.slug()).param("service",service)
      .param("remoteName",remoteName).param("defaultRoute",defaultRoute).param("remote",p.remoteEntry())
      .param("module",p.exposedModule()).param("route",p.routeBasePath()).param("semver",p.semanticVersion())
      .param("contract",p.contractVersion()).param("integrity",p.integrity()).param("active",p.active())
      .param("sort",p.sortOrder()).update();}
  @Override public int update(UUID id,long version,PanelCommand p,String service,String remoteName,String defaultRoute){return database.sql("""
    update panel set code=:code,name_fa=:fa,name_en=:en,description=:description,slug=:slug,
      service_slug=:service,remote_name=:remoteName,default_route_id=:defaultRoute,
      remote_entry_path=:remote,exposed_module=:module,route_base_path=:route,
      semantic_version=:semver,contract_version=:contract,integrity=:integrity,active=:active,
      sort_order=:sort,version=version+1,updated_at=now() where id=:id and version=:version
    """).param("id",id).param("version",version).param("code",p.code()).param("fa",p.nameFa())
      .param("en",p.nameEn()).param("description",p.description()).param("slug",p.slug())
      .param("service",service).param("remoteName",remoteName).param("defaultRoute",defaultRoute)
      .param("remote",p.remoteEntry()).param("module",p.exposedModule()).param("route",p.routeBasePath())
      .param("semver",p.semanticVersion()).param("contract",p.contractVersion()).param("integrity",p.integrity())
      .param("active",p.active()).param("sort",p.sortOrder()).update();}
  @Override public int archive(UUID id,long version){return database.sql("update panel set active=false,version=version+1,updated_at=now() where id=:id and version=:version").param("id",id).param("version",version).update();}
  @Override public void enqueue(UUID id,String event,String key,long version){database.sql("""
    insert into outbox_event(aggregate_type,aggregate_id,event_type,payload,idempotency_key)
    values('panel',:id,:event,jsonb_build_object('key',cast(:key as text)),:idem)
    on conflict(idempotency_key) do nothing
    """).param("id",id).param("event",event).param("key",key).param("idem",event+":"+id+":"+version).update();}
  private Map<String,Object> read(String value){try{return value==null?Map.of():json.readValue(value,MAP);}catch(Exception e){throw new IllegalStateException("Invalid audit details",e);}}
  private static UUID uuid(ResultSet rs,String name)throws SQLException{return rs.getObject(name,UUID.class);}
  private static Instant instant(ResultSet rs,String name)throws SQLException{var t=rs.getTimestamp(name);return t==null?null:t.toInstant();}
}

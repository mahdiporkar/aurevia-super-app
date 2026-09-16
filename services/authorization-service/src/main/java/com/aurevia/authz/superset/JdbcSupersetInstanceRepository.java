package com.aurevia.authz.superset;

import static com.aurevia.authz.api.dto.SupersetInstanceDtos.*;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcSupersetInstanceRepository implements SupersetInstanceRepository {
  private static final TypeReference<Map<String,Object>> MAP_TYPE=new TypeReference<>() {};
  private final JdbcClient database;
  private final ObjectMapper json;
  JdbcSupersetInstanceRepository(JdbcClient database,ObjectMapper json) {
    this.database=database;this.json=json;
  }

  @Override public List<InstanceView> instances() {
    return database.sql("""
        select id,code,name,zone::text,base_url,connection_ref,auth_mode,tls_required,
          active,proxy_mode,health_status,health_checked_at,metadata::text,version,
          created_at,updated_at
        from superset_instance order by zone,code
        """).query((result,row)->instance(result)).list();
  }

  @Override public List<IntegrationView> activeIntegrations() {
    return database.sql("""
        select p.code,p.name,'MAPPED' zone,o.auth_mode,true proxy_mode,
          case when p.health_status='UNREACHABLE' or o.health_status='UNREACHABLE'
            then 'UNREACHABLE'
            when p.health_status='ACTIVE' and o.health_status='ACTIVE' then 'ACTIVE'
            else 'UNKNOWN' end health_status
        from superset_proxy_mapping mapping
        join superset_instance p on p.id=mapping.public_instance_id
          and p.zone='PUBLIC' and p.active and p.proxy_mode
        join superset_instance o on o.id=mapping.operation_instance_id
          and o.zone='OPERATION' and o.active and o.proxy_mode
        where mapping.active order by mapping.is_default desc,p.code
        """).query((result,row)->new IntegrationView(result.getString("code"),
            result.getString("name"),result.getString("zone"),
            "/api/integrations/superset/"+result.getString("code")+"/",
            result.getString("auth_mode"),result.getBoolean("proxy_mode"),
            result.getString("health_status"))).list();
  }

  @Override public void insert(InstanceValue value,String actor) {
    database.sql("""
        insert into superset_instance(id,code,name,zone,base_url,connection_ref,auth_mode,
          tls_required,active,proxy_mode,health_status,metadata,created_by,updated_by)
        values(:id,:code,:name,cast(:zone as superset_zone),:url,:connection,:auth,
          :tls,:active,:proxy,case when :active then 'UNKNOWN' else 'DISABLED' end,
          cast(:metadata as jsonb),:actor,:actor)
        """).param("id",value.id()).param("code",value.code()).param("name",value.name())
        .param("zone",value.zone()).param("url",value.baseUrl())
        .param("connection",value.connectionRef()).param("auth",value.authMode())
        .param("tls",value.tlsRequired()).param("active",value.active())
        .param("proxy",value.proxyMode()).param("metadata",value.metadata())
        .param("actor",actor).update();
  }

  @Override public boolean update(UUID id,long version,InstanceValue value,boolean active,
      String actor) {
    return database.sql("""
        update superset_instance set name=:name,zone=cast(:zone as superset_zone),
          base_url=:url,connection_ref=:connection,auth_mode=:auth,tls_required=:tls,
          active=:active,proxy_mode=:proxy,metadata=cast(:metadata as jsonb),
          health_status=case when :active then 'UNKNOWN' else 'DISABLED' end,
          health_checked_at=null,version=version+1,updated_by=:actor,updated_at=now()
        where id=:id and code=:code and version=:version
        """).param("name",value.name()).param("zone",value.zone())
        .param("url",value.baseUrl()).param("connection",value.connectionRef())
        .param("auth",value.authMode()).param("tls",value.tlsRequired())
        .param("active",active).param("proxy",value.proxyMode())
        .param("metadata",value.metadata()).param("actor",actor).param("id",id)
        .param("code",value.code()).param("version",version).update()==1;
  }

  @Override public void ensureApplicationResource(InstanceValue value) {
    database.sql("""
        update resource set name_fa='سوپرست',name_en='Superset',status='ACTIVE',
          metadata=metadata||jsonb_build_object('provider','SUPERSET','logicalIntegration',true),
          version=version+1,updated_at=now()
        where resource_key='external_resource:superset-public'
        """).update();
    database.sql("""
        insert into resource_action(resource_id,action_id)
        select resource.id,action.id from resource join action on action.action_key in ('view','admin')
        where resource.resource_key='external_resource:superset-public' on conflict do nothing
        """).update();
  }

  @Override public void updateHealth(String code,String status) {
    database.sql("""
        update superset_instance set health_status=case when active then :status else 'DISABLED' end,
          health_checked_at=now(),updated_at=now() where code=:code
        """).param("status",status).param("code",code).update();
  }

  @Override public List<MappingView> mappings() {
    return database.sql("""
        select m.id,m.public_instance_id as "publicInstanceId",p.code as "publicCode",
          p.name as "publicName",m.operation_instance_id as "operationInstanceId",
          o.code as "operationCode",o.name as "operationName",m.public_path as "publicPath",
          m.is_default as "isDefault",m.active,m.version
        from superset_proxy_mapping m
        join superset_instance p on p.id=m.public_instance_id
        join superset_instance o on o.id=m.operation_instance_id
        order by m.is_default desc,p.code
        """).query(MappingView.class).list();
  }

  @Override public void clearDefaultMappings(String actor) {
    database.sql("""
        update superset_proxy_mapping set is_default=false,version=version+1,
          updated_at=now(),updated_by=:actor where is_default
        """).param("actor",actor).update();
  }

  @Override public UUID upsertMapping(UUID id,UUID publicId,UUID operationId,String path,
      boolean isDefault,boolean active,String actor) {
    return database.sql("""
        insert into superset_proxy_mapping(id,public_instance_id,operation_instance_id,
          public_path,is_default,active,created_by,updated_by)
        values(:id,:public,:operation,:path,:default,:active,:actor,:actor)
        on conflict(public_instance_id) do update set
          operation_instance_id=excluded.operation_instance_id,
          public_path=excluded.public_path,is_default=excluded.is_default,
          active=excluded.active,version=superset_proxy_mapping.version+1,
          updated_at=now(),updated_by=excluded.updated_by returning id
        """).param("id",id).param("public",publicId).param("operation",operationId)
        .param("path",path).param("default",isDefault).param("active",active)
        .param("actor",actor).query(UUID.class).single();
  }

  @Override public List<String> activeZones(UUID first,UUID second) {
    return database.sql("""
        select zone::text from superset_instance
        where id in (:first,:second) and active
        """).param("first",first).param("second",second).query(String.class).list();
  }

  private InstanceView instance(ResultSet result) throws SQLException {
    String zone=result.getString("zone");
    return new InstanceView(result.getObject("id",UUID.class),result.getString("code"),
        result.getString("name"),zone,zone,result.getString("base_url"),
        result.getString("connection_ref"),result.getString("auth_mode"),
        result.getBoolean("tls_required"),result.getBoolean("active"),
        result.getBoolean("active"),result.getBoolean("proxy_mode"),
        result.getString("health_status"),instant(result,"health_checked_at"),
        metadata(result.getString("metadata")),result.getLong("version"),
        instant(result,"created_at"),instant(result,"updated_at"));
  }

  private Map<String,Object> metadata(String value) {
    try { return value==null?Map.of():json.readValue(value,MAP_TYPE); }
    catch(Exception invalid) { throw new IllegalStateException("Invalid Superset metadata",invalid); }
  }

  private static Instant instant(ResultSet result,String column) throws SQLException {
    var value=result.getTimestamp(column);return value==null?null:value.toInstant();
  }
}

package com.aurevia.authz.superset;

import static com.aurevia.authz.api.dto.SupersetInstanceDtos.*;

import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcSupersetInstanceRepository implements SupersetInstanceRepository {
  private final JdbcClient database;
  JdbcSupersetInstanceRepository(JdbcClient database) { this.database=database; }

  @Override public List<InstanceView> instances() {
    return database.sql("""
        select id,code,name,zone::text,base_url as "baseUrl",
          connection_ref as "connectionRef",auth_mode as "authMode",
          tls_required as "tlsRequired",active,version,created_at as "createdAt",
          updated_at as "updatedAt"
        from superset_instance order by zone,code
        """).query(InstanceView.class).list();
  }

  @Override public void insert(InstanceValue value,String actor) {
    database.sql("""
        insert into superset_instance(id,code,name,zone,base_url,connection_ref,auth_mode,
          tls_required,active,created_by,updated_by)
        values(:id,:code,:name,cast(:zone as superset_zone),:url,:connection,:auth,
          :tls,:active,:actor,:actor)
        """).param("id",value.id()).param("code",value.code()).param("name",value.name())
        .param("zone",value.zone()).param("url",value.baseUrl())
        .param("connection",value.connectionRef()).param("auth",value.authMode())
        .param("tls",value.tlsRequired()).param("active",value.active())
        .param("actor",actor).update();
  }

  @Override public boolean update(UUID id,long version,InstanceValue value,boolean active,
      String actor) {
    return database.sql("""
        update superset_instance set name=:name,zone=cast(:zone as superset_zone),
          base_url=:url,connection_ref=:connection,auth_mode=:auth,tls_required=:tls,
          active=:active,version=version+1,updated_by=:actor,updated_at=now()
        where id=:id and code=:code and version=:version
        """).param("name",value.name()).param("zone",value.zone())
        .param("url",value.baseUrl()).param("connection",value.connectionRef())
        .param("auth",value.authMode()).param("tls",value.tlsRequired())
        .param("active",active).param("actor",actor).param("id",id)
        .param("code",value.code()).param("version",version).update()==1;
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
}

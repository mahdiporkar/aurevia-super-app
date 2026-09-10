package com.aurevia.authz.superset;

import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcSupersetProxyRepository implements SupersetProxyRepository {
  private final JdbcClient database;

  JdbcSupersetProxyRepository(JdbcClient database) { this.database=database; }

  @Override
  public Optional<Map<String,Object>> activeMapping(String publicInstanceCode) {
    String selection=publicInstanceCode==null||publicInstanceCode.isBlank()
        ?"and m.is_default":"and p.code=:code";
    var query=database.sql("""
        select p.code public_code,m.public_path,o.code operation_code,o.base_url,
               o.connection_ref,o.auth_mode,o.tls_required,o.proxy_mode,o.health_status,
               m.version mapping_version,o.version instance_version
        from superset_proxy_mapping m
        join superset_instance p on p.id=m.public_instance_id
          and p.zone='PUBLIC' and p.active and p.proxy_mode
        join superset_instance o on o.id=m.operation_instance_id
          and o.zone='OPERATION' and o.active and o.proxy_mode
        where m.active
        """+selection);
    if(publicInstanceCode!=null&&!publicInstanceCode.isBlank()) {
      query=query.param("code",publicInstanceCode);
    }
    return query.query().listOfRows().stream().findFirst();
  }

  @Override
  public Optional<Map<String,Object>> activeInstance(String instanceCode) {
    return database.sql("""
        select code public_code,code operation_code,base_url,connection_ref,auth_mode,
          tls_required,proxy_mode,health_status,version instance_version
        from superset_instance where code=:code and active and proxy_mode
        """).param("code",instanceCode).query().listOfRows().stream().findFirst();
  }
}

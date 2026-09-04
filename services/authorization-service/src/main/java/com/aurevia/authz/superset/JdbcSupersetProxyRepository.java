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
    return database.sql("""
        select p.code public_code,m.public_path,o.code operation_code,o.base_url,
               o.connection_ref,o.auth_mode,o.tls_required,m.version mapping_version,
               o.version instance_version
        from superset_proxy_mapping m
        join superset_instance p on p.id=m.public_instance_id
          and p.zone='PUBLIC' and p.active
        join superset_instance o on o.id=m.operation_instance_id
          and o.zone='OPERATION' and o.active
        where p.code=:code and m.active
        """).param("code",publicInstanceCode).query().listOfRows().stream().findFirst();
  }
}

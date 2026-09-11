package com.aurevia.authz.identityprovider;

import static com.aurevia.authz.identityprovider.IdentityProviderModels.*;
import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcIdentityProviderRepository implements IdentityProviderRepository {
  private static final String COLUMNS="""
      id,code,name,provider_type,issuer_url,authorization_endpoint,token_endpoint,jwks_uri,
      user_info_endpoint,client_id,client_secret_reference,enabled,tenant_id,domains,scopes,
      audiences,subject_claim,username_claim,groups_claim,connection_status,
      last_health_check_at,last_health_error,version
      """;
  private final JdbcClient database;
  JdbcIdentityProviderRepository(JdbcClient database){this.database=database;}

  @Override public List<ProviderView> findAll(){return database.sql(
      "select "+COLUMNS+" from identity_provider order by tenant_id nulls first,name")
      .query(JdbcIdentityProviderRepository::view).list();}
  @Override public List<ProviderView> findEnabled(){return database.sql(
      "select "+COLUMNS+" from identity_provider where enabled and connection_status<>'INVALID' order by name")
      .query(JdbcIdentityProviderRepository::view).list();}
  @Override public Optional<ProviderView> findEnabledByCode(String code){return database.sql(
      "select "+COLUMNS+" from identity_provider where code=:code and enabled and connection_status<>'INVALID'")
      .param("code",code).query(JdbcIdentityProviderRepository::view).optional();}
  @Override public Optional<ProviderSnapshot> snapshot(UUID id){return database.sql("""
      select id,code,issuer_url as "issuerUrl",client_id as "clientId",tenant_id as "tenantId",
        enabled,version from identity_provider where id=:id
      """).param("id",id).query(ProviderSnapshot.class).optional();}
  @Override public void insert(UUID id,ProviderCommand c,String actor){database.sql("""
      insert into identity_provider(id,code,name,provider_type,issuer_url,authorization_endpoint,
        token_endpoint,jwks_uri,user_info_endpoint,client_id,client_secret_reference,enabled,
        tenant_id,domains,scopes,audiences,subject_claim,username_claim,groups_claim,created_by,updated_by)
      values(:id,:code,:name,:type,:issuer,:authorization,:token,:jwks,:userinfo,:client,
        :secret,:enabled,:tenant,:domains,:scopes,:audiences,:subject,:username,:groups,:actor,:actor)
      """).param("id",id).param("code",c.code()).param("name",c.name()).param("type",c.type())
      .param("issuer",c.issuerUrl()).param("authorization",c.authorizationEndpoint())
      .param("token",c.tokenEndpoint()).param("jwks",c.jwksUri())
      .param("userinfo",c.userInfoEndpoint()).param("client",c.clientId())
      .param("secret",c.clientSecretReference()).param("enabled",c.enabled())
      .param("tenant",c.tenantId()).param("domains",c.domains().toArray(String[]::new))
      .param("scopes",c.scopes().toArray(String[]::new))
      .param("audiences",c.audiences().toArray(String[]::new)).param("subject",c.subjectClaim())
      .param("username",c.usernameClaim()).param("groups",c.groupsClaim()).param("actor",actor).update();}
  @Override public int update(UUID id,long version,ProviderCommand c,String actor){return database.sql("""
      update identity_provider set name=:name,provider_type=:type,issuer_url=:issuer,
        authorization_endpoint=:authorization,token_endpoint=:token,jwks_uri=:jwks,
        user_info_endpoint=:userinfo,client_id=:client,client_secret_reference=:secret,
        enabled=:enabled,tenant_id=:tenant,domains=:domains,scopes=:scopes,audiences=:audiences,
        subject_claim=:subject,username_claim=:username,groups_claim=:groups,
        connection_status='UNKNOWN',last_health_error=null,version=version+1,updated_by=:actor,
        updated_at=now() where id=:id and version=:version
      """).param("name",c.name()).param("type",c.type()).param("issuer",c.issuerUrl())
      .param("authorization",c.authorizationEndpoint()).param("token",c.tokenEndpoint())
      .param("jwks",c.jwksUri()).param("userinfo",c.userInfoEndpoint()).param("client",c.clientId())
      .param("secret",c.clientSecretReference()).param("enabled",c.enabled())
      .param("tenant",c.tenantId()).param("domains",c.domains().toArray(String[]::new))
      .param("scopes",c.scopes().toArray(String[]::new))
      .param("audiences",c.audiences().toArray(String[]::new)).param("subject",c.subjectClaim())
      .param("username",c.usernameClaim()).param("groups",c.groupsClaim()).param("actor",actor)
      .param("id",id).param("version",version).update();}
  @Override public int updateEnabled(UUID id,long version,boolean enabled,String actor){return database.sql("""
      update identity_provider set enabled=:enabled,version=version+1,updated_by=:actor,
        updated_at=now() where id=:id and version=:version
      """).param("enabled",enabled).param("actor",actor).param("id",id)
      .param("version",version).update();}
  @Override public void updateHealth(UUID id,String status,Instant checkedAt,String error){database.sql("""
      update identity_provider set connection_status=:status,last_health_check_at=:checked,
        last_health_error=:error,updated_at=now() where id=:id
      """).param("status",status).param("checked",checkedAt).param("error",error)
      .param("id",id).update();}

  private static ProviderView view(ResultSet rs,int row) throws SQLException{return new ProviderView(
      rs.getObject("id",UUID.class),rs.getString("code"),rs.getString("name"),
      rs.getString("provider_type"),rs.getString("issuer_url"),rs.getString("authorization_endpoint"),
      rs.getString("token_endpoint"),rs.getString("jwks_uri"),rs.getString("user_info_endpoint"),
      rs.getString("client_id"),rs.getString("client_secret_reference"),rs.getBoolean("enabled"),
      rs.getString("tenant_id"),strings(rs.getArray("domains")),strings(rs.getArray("scopes")),
      strings(rs.getArray("audiences")),rs.getString("subject_claim"),rs.getString("username_claim"),
      rs.getString("groups_claim"),rs.getString("connection_status"),instant(rs,"last_health_check_at"),
      rs.getString("last_health_error"),rs.getLong("version"));}
  private static List<String> strings(Array value)throws SQLException{
    return value==null?List.of():Arrays.asList((String[])value.getArray());}
  private static Instant instant(ResultSet rs,String name)throws SQLException{
    var value=rs.getTimestamp(name);return value==null?null:value.toInstant();}
}

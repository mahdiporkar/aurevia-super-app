package com.aurevia.authz.outbound;

import static com.aurevia.authz.outbound.OutboundModels.*;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcOutboundRegistryRepository implements OutboundRegistryRepository {
  private final JdbcClient database;
  public JdbcOutboundRegistryRepository(JdbcClient database){this.database=database;}

  @Override public List<ConnectionView> connections(){return database.sql("""
    select id,connection_ref,name,kind::text kind,base_url,tls_required,active,version,updated_at
    from outbound_connection order by name
    """).query((rs,row)->connection(rs)).list();}
  @Override public Optional<RuntimeConnectionView> runtimeConnection(String reference){return database.sql("""
    select connection_ref,name,kind::text kind,base_url,tls_required,version
    from outbound_connection where connection_ref=:ref and active
    """).param("ref",reference).query((rs,row)->new RuntimeConnectionView(rs.getString("connection_ref"),
      rs.getString("name"),rs.getString("kind"),rs.getString("base_url"),
      rs.getBoolean("tls_required"),rs.getLong("version"))).optional();}
  @Override public void createConnection(UUID id,ConnectionCommand p,String baseUrl,String actor){database.sql("""
    insert into outbound_connection(id,connection_ref,name,kind,base_url,tls_required,active,created_by,updated_by)
    values(:id,:ref,:name,'LEGACY_TOKEN',:url,:tls,:active,:actor,:actor)
    """).param("id",id).param("ref",p.connectionRef()).param("name",p.name()).param("url",baseUrl)
    .param("tls",p.tlsRequired()).param("active",p.active()).param("actor",actor).update();}
  @Override public int updateConnection(UUID id,ConnectionCommand p,String baseUrl,String actor){return database.sql("""
    update outbound_connection set name=:name,base_url=:url,tls_required=:tls,active=:active,
      version=version+1,updated_by=:actor,updated_at=now()
    where id=:id and connection_ref=:ref and version=:version
    """).param("name",p.name()).param("url",baseUrl).param("tls",p.tlsRequired()).param("active",p.active())
    .param("actor",actor).param("id",id).param("ref",p.connectionRef()).param("version",p.version()).update();}

  @Override public List<ProfileView> profiles(String search){return database.sql("""
    select p.*,(select count(*) from service_target st where st.outbound_auth_profile_id=p.id) usage_count
    from outbound_auth_profile p
    where (:search='' or lower(p.code||' '||p.name) like lower('%'||:search||'%')) order by p.code
    """).param("search",search).query((rs,row)->profile(rs)).list();}
  @Override public Optional<ProfileView> profile(UUID id){return database.sql("""
    select p.*,(select count(*) from service_target st where st.outbound_auth_profile_id=p.id) usage_count
    from outbound_auth_profile p where p.id=:id
    """).param("id",id).query((rs,row)->profile(rs)).optional();}
  @Override public Optional<RuntimeProfileView> runtimeProfile(UUID id){return database.sql("""
    select id,auth_mode,token_connection_ref,token_endpoint_path,request_format,credential_secret_ref,
      scope,audience,token_response_pointer,expires_in_response_pointer,token_type_response_pointer,
      authorization_scheme,credential_transport,expiry_skew_seconds,connect_timeout_ms,
      response_timeout_ms,max_token_response_size,version
    from outbound_auth_profile where id=:id and active
    """).param("id",id).query((rs,row)->new RuntimeProfileView(uuid(rs,"id"),rs.getString("auth_mode"),
      rs.getString("token_connection_ref"),rs.getString("token_endpoint_path"),rs.getString("request_format"),
      rs.getString("credential_secret_ref"),rs.getString("scope"),rs.getString("audience"),
      rs.getString("token_response_pointer"),rs.getString("expires_in_response_pointer"),
      rs.getString("token_type_response_pointer"),rs.getString("authorization_scheme"),
      rs.getString("credential_transport"),rs.getInt("expiry_skew_seconds"),rs.getInt("connect_timeout_ms"),
      rs.getInt("response_timeout_ms"),rs.getLong("max_token_response_size"),rs.getLong("version"))).optional();}
  @Override public void createProfile(UUID id,ProfileCommand p,String actor){database.sql("""
    insert into outbound_auth_profile(id,code,name,description,auth_mode,token_connection_ref,
      token_endpoint_path,request_format,credential_secret_ref,scope,audience,token_response_pointer,
      expires_in_response_pointer,token_type_response_pointer,authorization_scheme,credential_transport,
      expiry_skew_seconds,connect_timeout_ms,response_timeout_ms,max_token_response_size,active,created_by,updated_by)
    values(:id,:code,:name,nullif(:description,''),:mode,nullif(:connection,''),nullif(:endpoint,''),
      :format,nullif(:secret,''),nullif(:scope,''),nullif(:audience,''),:token,:expiry,:type,:scheme,
      :transport,:skew,:connect,:response,:size,:active,:actor,:actor)
    """).param("id",id).param("actor",actor).params(parameters(p)).update();}
  @Override public int updateProfile(UUID id,long version,ProfileCommand p,String actor){return database.sql("""
    update outbound_auth_profile set code=:code,name=:name,description=nullif(:description,''),auth_mode=:mode,
      token_connection_ref=nullif(:connection,''),token_endpoint_path=nullif(:endpoint,''),request_format=:format,
      credential_secret_ref=nullif(:secret,''),scope=nullif(:scope,''),audience=nullif(:audience,''),
      token_response_pointer=:token,expires_in_response_pointer=:expiry,token_type_response_pointer=:type,
      authorization_scheme=:scheme,credential_transport=:transport,expiry_skew_seconds=:skew,
      connect_timeout_ms=:connect,response_timeout_ms=:response,max_token_response_size=:size,
      active=:active,version=version+1,updated_at=now(),updated_by=:actor where id=:id and version=:version
    """).param("id",id).param("version",version).param("actor",actor).params(parameters(p)).update();}
  @Override public int updateProfileStatus(UUID id,long version,boolean active,String actor){return database.sql("""
    update outbound_auth_profile set active=:active,version=version+1,updated_at=now(),updated_by=:actor
    where id=:id and version=:version
    """).param("active",active).param("actor",actor).param("id",id).param("version",version).update();}
  @Override public List<UsageView> usage(UUID id){return database.sql("""
    select id,code,name,active from service_target where outbound_auth_profile_id=:id order by code
    """).param("id",id).query((rs,row)->new UsageView(uuid(rs,"id"),rs.getString("code"),
      rs.getString("name"),rs.getBoolean("active"))).list();}

  private static java.util.Map<String,Object> parameters(ProfileCommand p){var v=new java.util.HashMap<String,Object>();
    v.put("code",p.code());v.put("name",p.name());v.put("description",p.description());v.put("mode",p.authMode());
    v.put("connection",p.tokenConnectionRef());v.put("endpoint",p.tokenEndpointPath());v.put("format",p.requestFormat());
    v.put("secret",p.credentialSecretRef());v.put("scope",p.scope());v.put("audience",p.audience());
    v.put("token",p.tokenResponsePointer());v.put("expiry",p.expiresInResponsePointer());v.put("type",p.tokenTypeResponsePointer());
    v.put("scheme",p.authorizationScheme());v.put("transport",p.credentialTransport());v.put("skew",p.expirySkewSeconds());
    v.put("connect",p.connectTimeoutMs());v.put("response",p.responseTimeoutMs());v.put("size",p.maxTokenResponseSize());
    v.put("active",p.active());return v;}
  private static ConnectionView connection(ResultSet rs)throws SQLException{return new ConnectionView(uuid(rs,"id"),
    rs.getString("connection_ref"),rs.getString("name"),rs.getString("kind"),rs.getString("base_url"),
    rs.getBoolean("tls_required"),rs.getBoolean("active"),rs.getLong("version"),instant(rs,"updated_at"));}
  private static ProfileView profile(ResultSet rs)throws SQLException{return new ProfileView(uuid(rs,"id"),rs.getString("code"),
    rs.getString("name"),rs.getString("description"),rs.getString("auth_mode"),rs.getString("token_connection_ref"),
    rs.getString("token_endpoint_path"),rs.getString("request_format"),rs.getString("credential_secret_ref"),
    rs.getString("scope"),rs.getString("audience"),rs.getString("token_response_pointer"),
    rs.getString("expires_in_response_pointer"),rs.getString("token_type_response_pointer"),
    rs.getString("authorization_scheme"),rs.getString("credential_transport"),rs.getInt("expiry_skew_seconds"),
    rs.getInt("connect_timeout_ms"),rs.getInt("response_timeout_ms"),rs.getLong("max_token_response_size"),
    rs.getBoolean("active"),rs.getLong("version"),rs.getLong("usage_count"),instant(rs,"created_at"),instant(rs,"updated_at"));}
  private static UUID uuid(ResultSet rs,String name)throws SQLException{return rs.getObject(name,UUID.class);}
  private static Instant instant(ResultSet rs,String name)throws SQLException{return rs.getTimestamp(name).toInstant();}
}

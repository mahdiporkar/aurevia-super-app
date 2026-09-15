package com.aurevia.authz.identity;

import com.aurevia.authz.identity.ExternalIdentityAdministrationService.ExternalIdentityView;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcCanonicalIdentityRepository implements CanonicalIdentityRepository {
  private final JdbcClient database;
  JdbcCanonicalIdentityRepository(JdbcClient database){this.database=database;}
  @Override public Optional<String> canonicalUserId(String issuer,String subject){return database.sql("""
      select u.canonical_user_id from external_identity e join app_user u on u.id=e.user_id
      where e.issuer=:issuer and e.subject=:subject and u.status='ACTIVE'
      """).param("issuer",issuer).param("subject",subject).query(String.class).optional();}
  @Override public List<ExternalIdentityView> externalIdentities(UUID userId){return database.sql("""
      select e.id,e.user_id,e.identity_provider_id,p.code provider_code,e.issuer,e.subject,
        u.canonical_user_id,e.last_login_at,e.created_at
      from external_identity e join app_user u on u.id=e.user_id
      left join identity_provider p on p.id=e.identity_provider_id
      where e.user_id=:user order by e.created_at
      """).param("user",userId).query((rs,row)->new ExternalIdentityView(
        rs.getObject("id",UUID.class),rs.getObject("user_id",UUID.class),
        rs.getObject("identity_provider_id",UUID.class),rs.getString("provider_code"),
        rs.getString("issuer"),rs.getString("subject"),rs.getString("canonical_user_id"),
        rs.getTimestamp("last_login_at")==null?null:rs.getTimestamp("last_login_at").toInstant(),
        rs.getTimestamp("created_at").toInstant())).list();}
  @Override public Optional<ProviderIdentity> provider(String code){return database.sql("""
      select id,code,issuer_url as "issuer" from identity_provider where code=:code and enabled
      """).param("code",code).query(ProviderIdentity.class).optional();}
  @Override public Optional<String> canonicalUserId(UUID userId){return database.sql(
      "select canonical_user_id from app_user where id=:id").param("id",userId)
      .query(String.class).optional();}
  @Override public UUID link(UUID userId,UUID providerId,String issuer,String subject,String actor){
    return database.sql("""
      insert into external_identity(user_id,identity_provider_id,issuer,subject,created_by)
      values(:user,:provider,:issuer,:subject,:actor) returning id
      """).param("user",userId).param("provider",providerId).param("issuer",issuer)
      .param("subject",subject).param("actor",actor).query(UUID.class).single();}
  @Override public long identityCount(UUID userId){return database.sql(
      "select count(*) from external_identity where user_id=:user").param("user",userId)
      .query(Long.class).single();}
  @Override public int unlink(UUID userId,UUID identityId){return database.sql(
      "delete from external_identity where id=:id and user_id=:user").param("id",identityId)
      .param("user",userId).update();}
}

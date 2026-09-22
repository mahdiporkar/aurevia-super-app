package com.aurevia.authz.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.aurevia.authz.support.PostgresFixture;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.simple.JdbcClient;

/** Real migrations: the identity is created once by stable sub and the marker lives in schema_version. */
@EnabledIfEnvironmentVariable(named="AUREVIA_TEST_JDBC_URL",matches=".+")
class JdbcFirstAdministratorRepositoryIntegrationTest {
  private static final String ISSUER="https://idp.bootstrap.test/realms/main";
  private static PostgresFixture fixture;
  private static JdbcClient database;
  private static JdbcFirstAdministratorRepository repository;

  @BeforeAll static void migrate() throws Exception {
    fixture=PostgresFixture.migrated();
    database=fixture.database();
    repository=new JdbcFirstAdministratorRepository(database);
  }
  @AfterAll static void cleanup() throws Exception { fixture.close(); }

  @Test void resolvesTheSameSubjectIdempotentlyWithoutDuplicateIdentities(){
    String sub=UUID.randomUUID().toString();
    repository.lock();
    UUID first=repository.resolveUser(ISSUER,sub);
    UUID second=repository.resolveUser(ISSUER,sub);
    assertThat(second).isEqualTo(first);
    assertThat(count("app_user","issuer=:issuer and external_id=:sub",sub)).isEqualTo(1);
    assertThat(count("external_identity","issuer=:issuer and subject=:sub",sub)).isEqualTo(1);
    String username=database.sql("select username from app_user where id=:id").param("id",first).query(String.class).single();
    assertThat(username).isEqualTo(sub);
  }

  @Test void reusesAnIdentityAlreadyLinkedByLoginSyncAndRefusesInactiveUsers(){
    String sub=UUID.randomUUID().toString();
    UUID existing=database.sql("""
        insert into app_user(issuer,external_id,username,display_name) values(:issuer,:sub,'already.logged.in','Existing')
        returning id""").param("issuer",ISSUER).param("sub",sub).query(UUID.class).single();
    database.sql("insert into external_identity(user_id,issuer,subject,created_by) values(:u,:issuer,:sub,'LOGIN_SYNC')")
        .param("u",existing).param("issuer",ISSUER).param("sub",sub).update();
    assertThat(repository.resolveUser(ISSUER,sub)).isEqualTo(existing);
    database.sql("update app_user set status='INACTIVE' where id=:id").param("id",existing).update();
    assertThatThrownBy(()->repository.resolveUser(ISSUER,sub)).hasMessageContaining("inactive application user");
  }

  @Test void completionMarkerIsDurableAndTheAdminTargetIsTheCanonicalPlatformPermission(){
    assertThat(repository.completed()).isFalse();
    var target=repository.adminTarget();
    List<String> keys=database.sql("""
        select r.resource_key||'/'||a.action_key from resource r join action a on a.id=:action where r.id=:resource
        """).param("resource",target.resourceId()).param("action",target.actionId()).query(String.class).list();
    assertThat(keys).containsExactly("application:aurevia/admin");
    repository.markCompleted();
    assertThat(repository.completed()).isTrue();
    assertThatThrownBy(repository::markCompleted).as("a second marker cannot be inserted").isInstanceOf(RuntimeException.class);
  }

  @Test void noAdministratorPasswordColumnExistsInTheAuthorizationSchema(){
    List<String> columns=database.sql("""
        select table_name||'.'||column_name from information_schema.columns
        where table_schema=current_schema() and column_name ilike '%password%'
        """).query(String.class).list();
    assertThat(columns).isEmpty();
  }

  private static long count(String table,String where,String sub){
    return database.sql("select count(*) from "+table+" where "+where).param("issuer",ISSUER).param("sub",sub)
        .query(Long.class).single();
  }
}

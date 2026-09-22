package com.aurevia.authz.bootstrap;

import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcFirstAdministratorRepository implements FirstAdministratorRepository {
  private final JdbcClient database;
  JdbcFirstAdministratorRepository(JdbcClient database) { this.database = database; }

  @Override public void lock() {
    // Transaction-scoped and shared by every replica, including when no marker row exists yet.
    database.sql("select pg_advisory_xact_lock(704318429137::bigint)")
        .query((rs, row) -> true).single();
  }

  @Override public boolean completed() {
    return database.sql("select count(*) from schema_version where component='first-administrator'")
        .query(Long.class).single() > 0;
  }

  @Override public UUID resolveUser(String issuer, String subject) {
    Optional<UUID> linked = linkedUser(issuer, subject);
    if (linked.isPresent()) return requireActive(linked.get());
    database.sql("""
        insert into app_user(issuer,external_id,username,display_name)
        values(:issuer,:subject,:subject,'Initial administrator')
        on conflict(issuer,external_id) do nothing
        """).param("issuer", issuer).param("subject", subject).update();
    UUID user = database.sql("""
        select id from app_user where issuer=:issuer and external_id=:subject for update
        """).param("issuer", issuer).param("subject", subject).query(UUID.class).single();
    database.sql("""
        insert into external_identity(user_id,issuer,subject,created_by)
        values(:user,:issuer,:subject,'FIRST_ADMIN_BOOTSTRAP')
        on conflict(issuer,subject) do nothing
        """).param("user", user).param("issuer", issuer).param("subject", subject).update();
    return requireActive(linkedUser(issuer, subject).orElseThrow());
  }

  private Optional<UUID> linkedUser(String issuer, String subject) {
    return database.sql("select user_id from external_identity where issuer=:issuer and subject=:subject")
        .param("issuer", issuer).param("subject", subject).query(UUID.class).optional();
  }

  private UUID requireActive(UUID user) {
    String status = database.sql("select status::text from app_user where id=:user for update")
        .param("user", user).query(String.class).single();
    if (!"ACTIVE".equals(status)) throw new IllegalStateException(
        "Bootstrap administrator is linked to an inactive application user");
    return user;
  }

  @Override public AdminTarget adminTarget() {
    return database.sql("""
        select r.id as resource_id,a.id as action_id from resource r
        join resource_action ra on ra.resource_id=r.id join action a on a.id=ra.action_id
        where r.resource_key='application:aurevia' and r.status='ACTIVE' and a.action_key='admin'
        """).query(AdminTarget.class).optional().orElseThrow(() -> new IllegalStateException(
            "The application:aurevia admin permission is missing from the resource catalog"));
  }

  @Override public void markCompleted() {
    database.sql("insert into schema_version(component,version) values('first-administrator','1')").update();
  }
}

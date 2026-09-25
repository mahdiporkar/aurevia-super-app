package com.aurevia.authz.sync;

import static org.assertj.core.api.Assertions.assertThat;

import com.aurevia.authz.support.PostgresFixture;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(named="AUREVIA_TEST_JDBC_URL",matches=".+")
class NormalPermissionMigrationIntegrationTest {
  @Test void remapsLegacyOrdinaryGrantsAndPendingEventsButPreservesExplicitAdminAndHistory()
      throws Exception {
    try(var fixture=PostgresFixture.migratedTo("79")) {
      var db=fixture.database();
      UUID subject=UUID.randomUUID();
      Map<String,String> expected=Map.ofEntries(
          Map.entry("create","creator"),Map.entry("import","creator"),Map.entry("upload","creator"),
          Map.entry("update","editor"),Map.entry("approve","editor"),Map.entry("reject","editor"),
          Map.entry("delete","deleter"),Map.entry("share","sharer"),Map.entry("export","exporter"),
          Map.entry("download","exporter"),Map.entry("admin","manager"));
      for(String action:expected.keySet()) {
        UUID grant=db.sql("""
            insert into authorization_grant(subject_type,subject_id,resource_id,action_id,relation)
            select 'USER',:subject,r.id,a.id,'manager' from resource r cross join action a
            where r.resource_key='application:aurevia' and a.action_key=:action returning id
            """).param("subject",subject).param("action",action).query(UUID.class).single();
        for(String event:java.util.List.of("GRANT_WRITE","GRANT_DELETE","PROCESSED")) {
          db.sql("""
              insert into outbox_event(aggregate_type,aggregate_id,event_type,payload,idempotency_key,processed_at)
              values('grant',:grant,:event,
                jsonb_build_object('user','user:migration','relation','manager','object','application:aurevia'),
                :key,case when :event='PROCESSED' then now() else null end)
              """).param("grant",grant).param("event",event)
              .param("key",grant+":"+event).update();
        }
      }
      fixture.migrate();
      var grants=db.sql("""
          select a.action_key,g.relation,g.version from authorization_grant g
          join action a on a.id=g.action_id where g.subject_id=:subject
          """).param("subject",subject).query().listOfRows();
      assertThat(grants).hasSize(expected.size());
      for(var grant:grants) {
        String action=(String)grant.get("action_key");
        assertThat(grant.get("relation")).isEqualTo(expected.get(action));
        assertThat(((Number)grant.get("version")).longValue()).isEqualTo(action.equals("admin")?0:1);
      }
      assertThat(db.sql("""
          select count(*) from outbox_event e join authorization_grant g on g.id=e.aggregate_id
          where g.subject_id=:subject and e.processed_at is null
            and e.payload->>'relation'<>g.relation
          """).param("subject",subject).query(Long.class).single()).isZero();
      assertThat(db.sql("""
          select distinct e.payload->>'relation' from outbox_event e
          join authorization_grant g on g.id=e.aggregate_id
          where g.subject_id=:subject and e.processed_at is not null
          """).param("subject",subject).query(String.class).list()).containsExactly("manager");
    }
  }
}

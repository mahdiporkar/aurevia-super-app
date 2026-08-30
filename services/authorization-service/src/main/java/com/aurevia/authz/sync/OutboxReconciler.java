package com.aurevia.authz.sync;

import com.aurevia.authz.openfga.RelationshipAuthorizationPort;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Value;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

/** Idempotently projects transactional grant events into OpenFGA. */
@Component
public class OutboxReconciler {
  private final JdbcClient database;
  private final RelationshipAuthorizationPort relationships;
  private final int maxAttempts;
  private final Timer projectionLatency;

  public OutboxReconciler(JdbcClient database, RelationshipAuthorizationPort relationships,
      @Value("${aurevia.outbox.max-attempts:12}") int maxAttempts, MeterRegistry metrics) {
    this.database = database;
    this.relationships = relationships;
    this.maxAttempts = maxAttempts;
    this.projectionLatency = metrics.timer("aurevia.openfga.projection.latency");
  }

  @Scheduled(fixedDelayString = "${aurevia.outbox.interval-ms:5000}")
  @Transactional
  public void reconcile() {
    var rows = database.sql("""
        select id, event_type, payload::text payload
        from outbox_event
        where processed_at is null and dead_lettered_at is null and available_at <= now()
        order by created_at for update skip locked limit 50
        """).query().listOfRows();
    for (var row : rows) process(row);
  }

  private void process(Map<String, Object> row) {
    UUID id = (UUID) row.get("id");
    String event = (String) row.get("event_type");
    try {
      if (event.startsWith("PANEL_")) {
        markApplied(id);
        return;
      }
      boolean write = event.equals("GRANT_WRITE") || event.equals("ROLE_ASSIGNMENT_WRITE") || event.equals("RESOURCE_PARENT_WRITE") || event.equals("GROUP_MEMBERSHIP_WRITE");
      boolean delete = event.equals("GRANT_DELETE") || event.equals("ROLE_ASSIGNMENT_DELETE") || event.equals("RESOURCE_PARENT_DELETE") || event.equals("GROUP_MEMBERSHIP_DELETE");
      if (!write && !delete) {
        retry(id, "No projection adapter for event " + event);
        return;
      }
      Tuple tuple = database.sql("""
          select payload->>'user' as "user", payload->>'relation' as relation,
                 payload->>'object' as object
          from outbox_event where id=:id
          """).param("id", id).query(Tuple.class).single();
      if (write) {
        projectionLatency.record(() -> relationships.write(tuple.user(), tuple.relation(), tuple.object()));
      } else {
        projectionLatency.record(() -> relationships.delete(tuple.user(), tuple.relation(), tuple.object()));
      }
      markApplied(id);
    } catch (RuntimeException failure) {
      retry(id, safeMessage(failure));
    }
  }

  private void markApplied(UUID id) {
    database.sql("""
        update outbox_event set processed_at=now(), attempts=attempts+1, last_error=null
        where id=:id
        """).param("id", id).update();
  }

  private void retry(UUID id, String error) {
    database.sql("""
        update outbox_event set attempts=attempts+1,
          available_at=now() + make_interval(secs => least(300,
            cast(power(2, least(attempts, 10)) as integer))),
          dead_lettered_at=case when attempts+1 >= :max then now() else null end,
          last_error=:error where id=:id
        """).param("id", id).param("max", maxAttempts).param("error", error).update();
  }

  private static String safeMessage(RuntimeException failure) {
    String message = failure.getClass().getSimpleName() + ": " + failure.getMessage();
    return message.length() > 900 ? message.substring(0, 900) : message;
  }

  record Tuple(String user, String relation, String object) {}
}

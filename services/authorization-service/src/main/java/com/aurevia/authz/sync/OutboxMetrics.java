package com.aurevia.authz.sync;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

@Component
class OutboxMetrics {
  OutboxMetrics(JdbcClient database, MeterRegistry registry) {
    Gauge.builder("aurevia.outbox.pending", database,
        db -> count(db, "processed_at is null and dead_lettered_at is null"))
        .register(registry);
    Gauge.builder("aurevia.outbox.dead_letter", database,
        db -> count(db, "dead_lettered_at is not null")).register(registry);
    Gauge.builder("aurevia.outbox.retry", database,
        db -> count(db, "processed_at is null and attempts > 0 and dead_lettered_at is null"))
        .register(registry);
    Gauge.builder("aurevia.outbox.oldest_pending_seconds", database, db -> scalar(db, """
        select coalesce(extract(epoch from now()-min(created_at)),0) from outbox_event
        where processed_at is null and dead_lettered_at is null
        """)).register(registry);
  }

  private static double count(JdbcClient db,String predicate) {
    return scalar(db, "select count(*)::double precision from outbox_event where " + predicate);
  }
  private static double scalar(JdbcClient db,String sql) {
    try { return db.sql(sql).query(Double.class).single(); }
    catch (RuntimeException unavailable) { return Double.NaN; }
  }
}

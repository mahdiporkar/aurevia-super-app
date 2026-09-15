package com.aurevia.authz.sync;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcOutboxMetricsRepository implements OutboxMetricsRepository {
  private final JdbcClient database;
  JdbcOutboxMetricsRepository(JdbcClient database) { this.database=database; }
  @Override public double pending() { return scalar("""
      select count(*)::double precision from outbox_event
      where processed_at is null and dead_lettered_at is null
      """); }
  @Override public double deadLettered() { return scalar("""
      select count(*)::double precision from outbox_event where dead_lettered_at is not null
      """); }
  @Override public double retrying() { return scalar("""
      select count(*)::double precision from outbox_event
      where processed_at is null and attempts > 0 and dead_lettered_at is null
      """); }
  @Override public double oldestPendingSeconds() { return scalar("""
      select coalesce(extract(epoch from now()-min(created_at)),0) from outbox_event
      where processed_at is null and dead_lettered_at is null
      """); }
  private double scalar(String sql) {
    try { return database.sql(sql).query(Double.class).single(); }
    catch(RuntimeException unavailable) { return Double.NaN; }
  }
}

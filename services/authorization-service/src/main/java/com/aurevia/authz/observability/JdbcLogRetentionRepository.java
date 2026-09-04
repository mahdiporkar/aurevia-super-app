package com.aurevia.authz.observability;

import java.sql.Timestamp;
import java.time.Instant;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcLogRetentionRepository implements LogRetentionRepository {
  private final JdbcClient database;
  JdbcLogRetentionRepository(JdbcClient database) { this.database=database; }
  @Override public void deleteApiBatchBefore(Instant cutoff,int limit) {
    delete("api_log",cutoff,limit);
  }
  @Override public void deleteAuditBatchBefore(Instant cutoff,int limit) {
    delete("audit_log",cutoff,limit);
  }
  private void delete(String table,Instant cutoff,int limit) {
    database.sql("delete from "+table+" where id in (select id from "+table
        +" where event_time < :cutoff order by event_time limit :limit)")
        .param("cutoff",Timestamp.from(cutoff)).param("limit",limit).update();
  }
}

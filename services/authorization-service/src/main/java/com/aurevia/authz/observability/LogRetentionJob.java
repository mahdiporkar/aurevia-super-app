package com.aurevia.authz.observability;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class LogRetentionJob {
  private final JdbcClient database;private final int apiDays;private final int auditDays;
  public LogRetentionJob(JdbcClient database,@Value("${logging.retention.api-days:30}") int apiDays,@Value("${logging.retention.audit-days:365}") int auditDays){if(apiDays<1||auditDays<1)throw new IllegalArgumentException("Log retention must be positive");this.database=database;this.apiDays=apiDays;this.auditDays=auditDays;}
  @Scheduled(cron="${logging.retention.cron:0 17 2 * * *}")
  @Transactional
  public void clean(){deleteBatch("api_log",Instant.now().minus(apiDays,ChronoUnit.DAYS));deleteBatch("audit_log",Instant.now().minus(auditDays,ChronoUnit.DAYS));}
  private void deleteBatch(String table,Instant cutoff){database.sql("delete from "+table+" where id in (select id from "+table+" where event_time < :cutoff order by event_time limit 5000)").param("cutoff",cutoff).update();}
}

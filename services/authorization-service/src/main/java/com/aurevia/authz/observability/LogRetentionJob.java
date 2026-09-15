package com.aurevia.authz.observability;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class LogRetentionJob {
  private final LogRetentionRepository logs;private final int apiDays;private final int auditDays;
  public LogRetentionJob(LogRetentionRepository logs,@Value("${logging.retention.api-days:30}") int apiDays,@Value("${logging.retention.audit-days:365}") int auditDays){if(apiDays<1||auditDays<1)throw new IllegalArgumentException("Log retention must be positive");this.logs=logs;this.apiDays=apiDays;this.auditDays=auditDays;}
  @Scheduled(cron="${logging.retention.cron:0 17 2 * * *}")
  @Transactional
  public void clean(){logs.deleteApiBatchBefore(Instant.now().minus(apiDays,ChronoUnit.DAYS),5000);logs.deleteAuditBatchBefore(Instant.now().minus(auditDays,ChronoUnit.DAYS),5000);}
}

package com.aurevia.authz.observability;

import java.time.Instant;

interface LogRetentionRepository {
  void deleteApiBatchBefore(Instant cutoff,int limit);
  void deleteAuditBatchBefore(Instant cutoff,int limit);
}

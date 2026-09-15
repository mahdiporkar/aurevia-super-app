package com.aurevia.authz.observability;

import java.util.UUID;

interface PublicZoneLogRepository {
  UUID insertApi(PublicZoneLogWriter.ApiEntry entry);
  UUID insertAudit(PublicZoneLogWriter.AuditEntry entry);
}

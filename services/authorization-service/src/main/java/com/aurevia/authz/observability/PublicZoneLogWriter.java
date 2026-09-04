package com.aurevia.authz.observability;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class PublicZoneLogWriter {
  private final PublicZoneLogRepository logs;
  public PublicZoneLogWriter(PublicZoneLogRepository logs) { this.logs=logs; }
  public UUID api(ApiEntry entry) { return logs.insertApi(entry); }
  public UUID audit(AuditEntry entry) { return logs.insertAudit(entry); }

  public record ApiEntry(Instant eventTime,String userId,String actorType,String serviceName,
      String httpMethod,String routeTemplate,int statusCode,long durationMs,String sourceIp,
      String userAgent,String correlationId,Long requestSizeBytes,Long responseSizeBytes,
      String authorizationResult,String resourceType,String resourceId,String businessAction,
      Long openfgaDurationMs,Long databaseDurationMs,Long redisDurationMs,
      Long downstreamDurationMs,String errorCode,String errorType,String errorResponseBody,
      boolean errorResponseRedacted,boolean errorResponseTruncated) {}
  public record AuditEntry(Instant eventTime,String actorType,String actorId,String eventCategory,
      String eventType,String subjectType,String subjectId,String targetType,String targetId,
      String targetNameSnapshot,String action,String result,Map<String,Object> beforeState,
      Map<String,Object> afterState,String sourceIp,String userAgent,String serviceName,
      String correlationId,Map<String,Object> metadata) {}
}

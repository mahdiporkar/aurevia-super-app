package com.aurevia.authz.observability;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Timestamp;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcPublicZoneLogRepository implements PublicZoneLogRepository {
  private final JdbcClient database;
  private final ObjectMapper json;
  JdbcPublicZoneLogRepository(JdbcClient database,ObjectMapper json) {
    this.database=database;this.json=json;
  }
  @Override public UUID insertApi(PublicZoneLogWriter.ApiEntry value) {
    UUID id=UUID.randomUUID();
    database.sql("""
      insert into api_log(id,event_time,user_id,actor_type,service_name,http_method,route_template,
        status_code,duration_ms,source_ip,user_agent,correlation_id,request_size_bytes,
        response_size_bytes,authorization_result,resource_type,resource_id,business_action,
        openfga_duration_ms,database_duration_ms,redis_duration_ms,downstream_duration_ms,
        error_code,error_type,error_response_body,error_response_redacted,error_response_truncated)
      values(:id,:time,:user,:actor,:service,:method,:route,:status,:duration,:ip,:agent,
        :correlation,:requestSize,:responseSize,:authorization,:resourceType,:resourceId,
        :action,:openfga,:database,:redis,:downstream,:errorCode,:errorType,:errorBody,
        :redacted,:truncated)
      """).param("id",id).param("time",Timestamp.from(value.eventTime()))
      .param("user",value.userId()).param("actor",value.actorType())
      .param("service",value.serviceName()).param("method",value.httpMethod())
      .param("route",value.routeTemplate()).param("status",value.statusCode())
      .param("duration",value.durationMs()).param("ip",value.sourceIp())
      .param("agent",value.userAgent()).param("correlation",value.correlationId())
      .param("requestSize",value.requestSizeBytes()).param("responseSize",value.responseSizeBytes())
      .param("authorization",value.authorizationResult()).param("resourceType",value.resourceType())
      .param("resourceId",value.resourceId()).param("action",value.businessAction())
      .param("openfga",value.openfgaDurationMs()).param("database",value.databaseDurationMs())
      .param("redis",value.redisDurationMs()).param("downstream",value.downstreamDurationMs())
      .param("errorCode",value.errorCode()).param("errorType",value.errorType())
      .param("errorBody",value.errorResponseBody()).param("redacted",value.errorResponseRedacted())
      .param("truncated",value.errorResponseTruncated()).update();
    return id;
  }
  @Override public UUID insertAudit(PublicZoneLogWriter.AuditEntry value) {
    UUID id=UUID.randomUUID();
    try {
      database.sql("""
        insert into audit_log(id,event_time,event_version,actor_type,actor_id,event_category,
          event_type,subject_type,subject_id,target_type,target_id,target_name_snapshot,action,
          result,before_state,after_state,source_ip,user_agent,service_name,correlation_id,metadata)
        values(:id,:time,1,:actorType,:actor,:category,:event,:subjectType,:subjectId,
          :targetType,:targetId,:targetName,:action,:result,cast(:before as jsonb),
          cast(:after as jsonb),:ip,:agent,:service,:correlation,cast(:metadata as jsonb))
        """).param("id",id).param("time",Timestamp.from(value.eventTime()))
        .param("actorType",value.actorType()).param("actor",value.actorId())
        .param("category",value.eventCategory()).param("event",value.eventType())
        .param("subjectType",value.subjectType()).param("subjectId",value.subjectId())
        .param("targetType",value.targetType()).param("targetId",value.targetId())
        .param("targetName",value.targetNameSnapshot()).param("action",value.action())
        .param("result",value.result()).param("before",safeJson(value.beforeState()))
        .param("after",safeJson(value.afterState())).param("ip",value.sourceIp())
        .param("agent",value.userAgent()).param("service",value.serviceName())
        .param("correlation",value.correlationId()).param("metadata",safeJson(value.metadata()))
        .update();
      return id;
    } catch(Exception failure) { throw new IllegalStateException("Audit persistence failed",failure); }
  }
  private String safeJson(Map<String,Object> value)throws Exception {
    return value==null?null:json.writeValueAsString(value);
  }
}

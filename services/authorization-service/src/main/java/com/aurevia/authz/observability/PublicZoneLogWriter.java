package com.aurevia.authz.observability;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

@Service
public class PublicZoneLogWriter {
  private final JdbcClient database;private final ObjectMapper json;
  public PublicZoneLogWriter(JdbcClient database,ObjectMapper json){this.database=database;this.json=json;}
  public UUID api(ApiEntry e){UUID id=UUID.randomUUID();database.sql("""
    insert into api_log(id,event_time,user_id,actor_type,service_name,http_method,route_template,
      status_code,duration_ms,source_ip,user_agent,correlation_id,request_size_bytes,
      response_size_bytes,authorization_result,resource_type,resource_id,business_action,
      openfga_duration_ms,database_duration_ms,redis_duration_ms,downstream_duration_ms,
      error_code,error_type,error_response_body,error_response_redacted,error_response_truncated)
    values(:id,:time,:user,:actor,:service,:method,:route,:status,:duration,:ip,:agent,:correlation,
      :requestSize,:responseSize,:authorization,:resourceType,:resourceId,:action,:openfga,:database,
      :redis,:downstream,:errorCode,:errorType,:errorBody,:redacted,:truncated)
    """).param("id",id).param("time",e.eventTime()).param("user",e.userId())
    .param("actor",e.actorType()).param("service",e.serviceName()).param("method",e.httpMethod())
    .param("route",e.routeTemplate()).param("status",e.statusCode()).param("duration",e.durationMs())
    .param("ip",e.sourceIp()).param("agent",e.userAgent()).param("correlation",e.correlationId())
    .param("requestSize",e.requestSizeBytes()).param("responseSize",e.responseSizeBytes())
    .param("authorization",e.authorizationResult()).param("resourceType",e.resourceType())
    .param("resourceId",e.resourceId()).param("action",e.businessAction())
    .param("openfga",e.openfgaDurationMs()).param("database",e.databaseDurationMs())
    .param("redis",e.redisDurationMs()).param("downstream",e.downstreamDurationMs())
    .param("errorCode",e.errorCode()).param("errorType",e.errorType())
    .param("errorBody",e.errorResponseBody()).param("redacted",e.errorResponseRedacted())
    .param("truncated",e.errorResponseTruncated()).update();return id;}
  public UUID audit(AuditEntry e){UUID id=UUID.randomUUID();try{database.sql("""
    insert into audit_log(id,event_time,event_version,actor_type,actor_id,event_category,event_type,
      subject_type,subject_id,target_type,target_id,target_name_snapshot,action,result,before_state,
      after_state,source_ip,user_agent,service_name,correlation_id,metadata)
    values(:id,:time,1,:actorType,:actor,:category,:event,:subjectType,:subjectId,:targetType,
      :targetId,:targetName,:action,:result,cast(:before as jsonb),cast(:after as jsonb),:ip,:agent,
      :service,:correlation,cast(:metadata as jsonb))
    """).param("id",id).param("time",e.eventTime()).param("actorType",e.actorType())
    .param("actor",e.actorId()).param("category",e.eventCategory()).param("event",e.eventType())
    .param("subjectType",e.subjectType()).param("subjectId",e.subjectId())
    .param("targetType",e.targetType()).param("targetId",e.targetId()).param("targetName",e.targetNameSnapshot())
    .param("action",e.action()).param("result",e.result()).param("before",safeJson(e.beforeState()))
    .param("after",safeJson(e.afterState())).param("ip",e.sourceIp()).param("agent",e.userAgent())
    .param("service",e.serviceName()).param("correlation",e.correlationId())
    .param("metadata",safeJson(e.metadata())).update();return id;}catch(Exception failure){throw new IllegalStateException("Audit persistence failed",failure);}}
  private String safeJson(Map<String,Object> value)throws Exception{return value==null?null:json.writeValueAsString(value);}
  public record ApiEntry(Instant eventTime,String userId,String actorType,String serviceName,String httpMethod,
    String routeTemplate,int statusCode,long durationMs,String sourceIp,String userAgent,String correlationId,
    Long requestSizeBytes,Long responseSizeBytes,String authorizationResult,String resourceType,String resourceId,
    String businessAction,Long openfgaDurationMs,Long databaseDurationMs,Long redisDurationMs,Long downstreamDurationMs,
    String errorCode,String errorType,String errorResponseBody,boolean errorResponseRedacted,boolean errorResponseTruncated){}
  public record AuditEntry(Instant eventTime,String actorType,String actorId,String eventCategory,String eventType,
    String subjectType,String subjectId,String targetType,String targetId,String targetNameSnapshot,String action,
    String result,Map<String,Object> beforeState,Map<String,Object> afterState,String sourceIp,String userAgent,
    String serviceName,String correlationId,Map<String,Object> metadata){}
}

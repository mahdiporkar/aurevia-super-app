package com.aurevia.authz.api;
import com.aurevia.authz.observability.PublicZoneLogWriter;
import java.time.Instant;
import com.aurevia.authz.observability.SafeErrorBodySerializer;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/internal/v1/logging")
public class LogIngestionController{
 private final PublicZoneLogWriter logs;private final SafeErrorBodySerializer errors;
 public LogIngestionController(PublicZoneLogWriter logs,SafeErrorBodySerializer errors){this.logs=logs;this.errors=errors;}
 @PostMapping("/api") @ResponseStatus(HttpStatus.NO_CONTENT) void api(@RequestBody ApiIngest e){
  var safe=errors.serialize(e.statusCode(),e.errorContentType(),e.errorResponseBody()==null?null:e.errorResponseBody().getBytes(java.nio.charset.StandardCharsets.UTF_8));
  logs.api(new PublicZoneLogWriter.ApiEntry(e.eventTime()==null?Instant.now():e.eventTime(),e.userId(),e.actorType(),e.serviceName(),e.httpMethod(),e.routeTemplate(),e.statusCode(),e.durationMs(),e.sourceIp(),e.userAgent(),com.aurevia.authz.observability.CorrelationIds.normalize(e.correlationId()),e.requestSizeBytes(),e.responseSizeBytes(),e.authorizationResult(),e.resourceType(),e.resourceId(),e.businessAction(),e.openfgaDurationMs(),e.databaseDurationMs(),e.redisDurationMs(),e.downstreamDurationMs(),e.errorCode(),e.errorType(),safe.body(),safe.redacted(),safe.truncated()));
 }
 public record ApiIngest(Instant eventTime,String userId,String actorType,String serviceName,String httpMethod,String routeTemplate,int statusCode,long durationMs,String sourceIp,String userAgent,String correlationId,Long requestSizeBytes,Long responseSizeBytes,String authorizationResult,String resourceType,String resourceId,String businessAction,Long openfgaDurationMs,Long databaseDurationMs,Long redisDurationMs,Long downstreamDurationMs,String errorCode,String errorType,String errorContentType,String errorResponseBody){}
}

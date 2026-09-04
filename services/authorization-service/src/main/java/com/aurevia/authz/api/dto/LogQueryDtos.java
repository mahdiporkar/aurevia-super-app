package com.aurevia.authz.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.List;
import java.util.Map;

public final class LogQueryDtos {
  private LogQueryDtos() {}

  public record ApiFilter(Instant from,Instant to,String userId,String serviceName,
      Integer statusCode,String route,String correlationId,String authorizationResult) {}
  public record AuditFilter(Instant from,Instant to,String actorId,String eventType,
      String targetType,String targetId,String result,String correlationId) {}
  public record PageResponse(List<Map<String,Object>> items,int page,int size,long total) {}
  public record CorrelationResponse(String correlationId,List<Map<String,Object>> items,
      boolean truncated) {}
  public record SummaryResponse(long total,long errors,
      @JsonProperty("average_duration_ms") long averageDurationMs,
      @JsonProperty("max_duration_ms") long maxDurationMs) {}
}

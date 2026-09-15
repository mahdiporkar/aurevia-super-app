package com.aurevia.authz.api;

import static com.aurevia.authz.api.dto.LogQueryDtos.*;

import com.aurevia.authz.observability.LogQueryService;
import java.time.Instant;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/v1/registry/logs")
public final class LogQueryController {
  private final LogQueryService logs;
  public LogQueryController(LogQueryService logs) { this.logs=logs; }

  @GetMapping("/api")
  public PageResponse api(@RequestHeader("X-Actor-Issuer") String issuer,
      @RequestHeader("X-Actor-Subject") String subject,
      @RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="50") int size,
      @RequestParam(required=false) Instant from,@RequestParam(required=false) Instant to,
      @RequestParam(required=false) String userId,@RequestParam(required=false) String serviceName,
      @RequestParam(required=false) Integer statusCode,@RequestParam(required=false) String route,
      @RequestParam(required=false) String correlationId,
      @RequestParam(required=false) String authorizationResult) {
    return logs.api(issuer,subject,page,size,new ApiFilter(from,to,userId,serviceName,statusCode,
        route,correlationId,authorizationResult));
  }

  @GetMapping("/api/{id}")
  public Map<String,Object> apiDetail(@RequestHeader("X-Actor-Issuer") String issuer,
      @RequestHeader("X-Actor-Subject") String subject,@PathVariable String id) {
    return logs.apiDetail(issuer,subject,id);
  }

  @GetMapping("/audit")
  public PageResponse audit(@RequestHeader("X-Actor-Issuer") String issuer,
      @RequestHeader("X-Actor-Subject") String subject,
      @RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="50") int size,
      @RequestParam(required=false) Instant from,@RequestParam(required=false) Instant to,
      @RequestParam(required=false) String actorId,@RequestParam(required=false) String eventType,
      @RequestParam(required=false) String targetType,@RequestParam(required=false) String targetId,
      @RequestParam(required=false) String result,
      @RequestParam(required=false) String correlationId) {
    return logs.audit(issuer,subject,page,size,new AuditFilter(from,to,actorId,eventType,
        targetType,targetId,result,correlationId));
  }

  @GetMapping("/audit/{id}")
  public Map<String,Object> auditDetail(@RequestHeader("X-Actor-Issuer") String issuer,
      @RequestHeader("X-Actor-Subject") String subject,@PathVariable String id) {
    return logs.auditDetail(issuer,subject,id);
  }

  @GetMapping("/correlation/{correlationId}")
  public CorrelationResponse correlation(@RequestHeader("X-Actor-Issuer") String issuer,
      @RequestHeader("X-Actor-Subject") String subject,@PathVariable String correlationId) {
    return logs.correlation(issuer,subject,correlationId);
  }

  @GetMapping("/api/summary")
  public SummaryResponse summary(@RequestHeader("X-Actor-Issuer") String issuer,
      @RequestHeader("X-Actor-Subject") String subject,
      @RequestParam(required=false) Instant from,@RequestParam(required=false) Instant to) {
    return logs.summary(issuer,subject,from,to);
  }
}

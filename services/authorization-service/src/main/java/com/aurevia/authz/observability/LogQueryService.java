package com.aurevia.authz.observability;

import static com.aurevia.authz.api.dto.LogQueryDtos.*;

import com.aurevia.authz.identity.SubjectKey;
import com.aurevia.authz.openfga.RelationshipAuthorizationPort;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class LogQueryService {
  private static final String LOG_RESOURCE="resource:business_resource/public-zone-logs";
  private final LogQueryRepository logs;
  private final RelationshipAuthorizationPort relationships;

  public LogQueryService(LogQueryRepository logs,RelationshipAuthorizationPort relationships) {
    this.logs=logs;this.relationships=relationships;
  }

  public PageResponse api(String issuer,String subject,int page,int size,ApiFilter raw) {
    require(issuer,subject,"can_view");
    return logs.apiLogs(apiFilter(raw),page(page,size).page(),size);
  }
  public Map<String,Object> apiDetail(String issuer,String subject,String id) {
    require(issuer,subject,"can_view");return detail(LogQueryRepository.LogKind.API,id);
  }
  public PageResponse audit(String issuer,String subject,int page,int size,AuditFilter raw) {
    require(issuer,subject,"can_manage");
    Page bounds=page(page,size);
    return logs.auditLogs(new AuditFilter(raw.from(),raw.to(),bounded(raw.actorId()),
        bounded(raw.eventType()),bounded(raw.targetType()),bounded(raw.targetId()),
        bounded(raw.result()),bounded(raw.correlationId())),bounds.page(),bounds.size());
  }
  public Map<String,Object> auditDetail(String issuer,String subject,String id) {
    require(issuer,subject,"can_manage");return detail(LogQueryRepository.LogKind.AUDIT,id);
  }
  public CorrelationResponse correlation(String issuer,String subject,String correlationId) {
    require(issuer,subject,"can_manage");
    String value=bounded(correlationId);
    List<Map<String,Object>> rows=new ArrayList<>();
    rows.addAll(logs.correlation(LogQueryRepository.LogKind.API,value,500));
    rows.addAll(logs.correlation(LogQueryRepository.LogKind.AUDIT,value,500));
    rows.sort((first,second)->eventInstant(first.get("event_time"))
        .compareTo(eventInstant(second.get("event_time"))));
    return new CorrelationResponse(value,rows,rows.size()>=1000);
  }
  public SummaryResponse summary(String issuer,String subject,Instant from,Instant to) {
    require(issuer,subject,"can_view");
    return logs.summary(new ApiFilter(from,to,null,null,null,null,null,null));
  }

  private Map<String,Object> detail(LogQueryRepository.LogKind kind,String id) {
    try { UUID.fromString(id); }
    catch(IllegalArgumentException failure) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Invalid log id");
    }
    return logs.detail(kind,id).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND));
  }
  private void require(String issuer,String subject,String relation) {
    if(issuer==null||subject==null||!relationships.check(
        new SubjectKey(issuer,subject).openFgaUser(),relation,LOG_RESOURCE)) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Log permission required");
    }
  }
  private static ApiFilter apiFilter(ApiFilter value) {
    return new ApiFilter(value.from(),value.to(),bounded(value.userId()),
        bounded(value.serviceName()),value.statusCode(),bounded(value.route()),
        bounded(value.correlationId()),bounded(value.authorizationResult()));
  }
  private static Page page(int page,int size) {
    if(page<0||size<1||size>200) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
          "page must be >= 0 and size between 1 and 200");
    }
    return new Page(page,size);
  }
  private static String bounded(String value) {
    if(value==null||value.isBlank()) return null;
    if(value.length()>500) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Filter is too long");
    }
    return value;
  }
  private static Instant eventInstant(Object value) {
    if(value instanceof Instant instant) return instant;
    if(value instanceof OffsetDateTime offset) return offset.toInstant();
    if(value instanceof Timestamp timestamp) return timestamp.toInstant();
    throw new IllegalStateException("Unsupported event_time value");
  }
  private record Page(int page,int size) {}
}

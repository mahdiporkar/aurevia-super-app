package com.aurevia.authz.observability;

import static com.aurevia.authz.api.dto.LogQueryDtos.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface LogQueryRepository {
  PageResponse apiLogs(ApiFilter filter,int page,int size);
  PageResponse auditLogs(AuditFilter filter,int page,int size);
  Optional<Map<String,Object>> detail(LogKind kind,String id);
  List<Map<String,Object>> correlation(LogKind kind,String correlationId,int limit);
  SummaryResponse summary(ApiFilter filter);
  enum LogKind { API,AUDIT }
}

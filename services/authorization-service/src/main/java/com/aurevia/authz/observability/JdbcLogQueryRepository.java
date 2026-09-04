package com.aurevia.authz.observability;

import static com.aurevia.authz.api.dto.LogQueryDtos.*;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcLogQueryRepository implements LogQueryRepository {
  private final JdbcClient database;
  JdbcLogQueryRepository(JdbcClient database) { this.database=database; }

  @Override public PageResponse apiLogs(ApiFilter filter,int page,int size) {
    Query query=apiQuery(filter);
    return page("api_log",query,page,size);
  }
  @Override public PageResponse auditLogs(AuditFilter filter,int page,int size) {
    Query query=new Query();
    query.add("event_time >= :from","from",time(filter.from()));
    query.add("event_time < :to","to",time(filter.to()));
    query.add("actor_id = :actor","actor",filter.actorId());
    query.add("event_type = :event","event",filter.eventType());
    query.add("target_type = :targetType","targetType",filter.targetType());
    query.add("target_id = :targetId","targetId",filter.targetId());
    query.add("result = :result","result",filter.result());
    query.add("correlation_id = :correlation","correlation",filter.correlationId());
    return page("audit_log",query,page,size);
  }
  @Override public Optional<Map<String,Object>> detail(LogKind kind,String id) {
    return database.sql("select * from "+table(kind)+" where id=cast(:id as uuid)")
        .param("id",id).query().listOfRows().stream().findFirst();
  }
  @Override public List<Map<String,Object>> correlation(LogKind kind,String id,int limit) {
    return database.sql("select '"+kind.name()+"' log_kind,* from "+table(kind)
        +" where correlation_id=:id order by event_time limit :limit")
        .param("id",id).param("limit",limit).query().listOfRows();
  }
  @Override public SummaryResponse summary(ApiFilter filter) {
    Query query=apiQuery(filter);
    var statement=bind(database.sql("""
        select count(*) total,count(*) filter(where status_code>=400) errors,
          coalesce(round(avg(duration_ms)),0) average_duration_ms,
          coalesce(max(duration_ms),0) max_duration_ms from api_log
        """+query.where()),query.params);
    Map<String,Object> row=statement.query().singleRow();
    return new SummaryResponse(number(row.get("total")),number(row.get("errors")),
        number(row.get("average_duration_ms")),number(row.get("max_duration_ms")));
  }

  private PageResponse page(String table,Query query,int page,int size) {
    var rows=bind(database.sql("select * from "+table+query.where()
        +" order by event_time desc,id desc limit :limit offset :offset"),query.params)
        .param("limit",size).param("offset",page*size).query().listOfRows();
    long total=bind(database.sql("select count(*) from "+table+query.where()),query.params)
        .query(Long.class).single();
    return new PageResponse(rows,page,size,total);
  }

  private static Query apiQuery(ApiFilter filter) {
    Query query=new Query();
    query.add("event_time >= :from","from",time(filter.from()));
    query.add("event_time < :to","to",time(filter.to()));
    query.add("user_id = :user","user",filter.userId());
    query.add("service_name = :service","service",filter.serviceName());
    query.add("status_code = :status","status",filter.statusCode());
    if(filter.route()!=null) query.add("route_template ilike :route","route","%"+filter.route()+"%");
    query.add("correlation_id = :correlation","correlation",filter.correlationId());
    query.add("authorization_result = :authorization","authorization",filter.authorizationResult());
    return query;
  }
  private static String table(LogKind kind) { return kind==LogKind.API?"api_log":"audit_log"; }
  private static Timestamp time(java.time.Instant value) { return value==null?null:Timestamp.from(value); }
  private static long number(Object value) { return value==null?0:((Number)value).longValue(); }
  private static JdbcClient.StatementSpec bind(JdbcClient.StatementSpec statement,
      Map<String,Object> params) {
    for(var item:params.entrySet()) statement=statement.param(item.getKey(),item.getValue());
    return statement;
  }
  private static final class Query {
    private final List<String> clauses=new ArrayList<>();
    private final Map<String,Object> params=new LinkedHashMap<>();
    void add(String clause,String name,Object value) {
      if(value!=null) { clauses.add(clause);params.put(name,value); }
    }
    String where() { return clauses.isEmpty()?"":" where "+String.join(" and ",clauses); }
  }
}

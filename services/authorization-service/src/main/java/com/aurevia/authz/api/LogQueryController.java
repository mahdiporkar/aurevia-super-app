package com.aurevia.authz.api;

import com.aurevia.authz.openfga.RelationshipAuthorizationPort;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/internal/v1/registry/logs")
public class LogQueryController {
  private static final String LOG_RESOURCE = "resource:business_resource:public-zone-logs";
  private final JdbcClient database;
  private final RelationshipAuthorizationPort relationships;

  public LogQueryController(JdbcClient database, RelationshipAuthorizationPort relationships) {
    this.database = database;
    this.relationships = relationships;
  }

  @GetMapping("/api")
  public Map<String,Object> api(HttpServletRequest request,
      @RequestParam(defaultValue="0") int page, @RequestParam(defaultValue="50") int size,
      @RequestParam(required=false) Instant from, @RequestParam(required=false) Instant to,
      @RequestParam(required=false) String userId, @RequestParam(required=false) String serviceName,
      @RequestParam(required=false) Integer statusCode, @RequestParam(required=false) String route,
      @RequestParam(required=false) String correlationId,
      @RequestParam(required=false) String authorizationResult) {
    require(request, "can_view"); Page bounds=Page.of(page,size);
    Query query=apiQuery(from,to,userId,serviceName,statusCode,route,correlationId,authorizationResult);
    return page("api_log", query, bounds);
  }

  @GetMapping("/api/{id}")
  public Map<String,Object> apiDetail(HttpServletRequest request,@PathVariable String id) {
    require(request,"can_view");return one("api_log",id);
  }

  @GetMapping("/audit")
  public Map<String,Object> audit(HttpServletRequest request,
      @RequestParam(defaultValue="0") int page, @RequestParam(defaultValue="50") int size,
      @RequestParam(required=false) Instant from, @RequestParam(required=false) Instant to,
      @RequestParam(required=false) String actorId, @RequestParam(required=false) String eventType,
      @RequestParam(required=false) String targetType, @RequestParam(required=false) String targetId,
      @RequestParam(required=false) String result, @RequestParam(required=false) String correlationId) {
    require(request,"can_manage");Page bounds=Page.of(page,size);
    Query query=new Query();query.add("event_time >= :from", "from",from);query.add("event_time < :to","to",to);
    query.add("actor_id = :actor","actor",bounded(actorId));query.add("event_type = :event","event",bounded(eventType));
    query.add("target_type = :targetType","targetType",bounded(targetType));query.add("target_id = :targetId","targetId",bounded(targetId));
    query.add("result = :result","result",bounded(result));query.add("correlation_id = :correlation","correlation",bounded(correlationId));
    return page("audit_log",query,bounds);
  }

  @GetMapping("/audit/{id}")
  public Map<String,Object> auditDetail(HttpServletRequest request,@PathVariable String id) {
    require(request,"can_manage");return one("audit_log",id);
  }

  @GetMapping("/correlation/{correlationId}")
  public Map<String,Object> correlation(HttpServletRequest request,@PathVariable String correlationId) {
    require(request,"can_manage");String value=bounded(correlationId);
    List<Map<String,Object>> rows=new ArrayList<>();
    rows.addAll(database.sql("select 'API' log_kind,* from api_log where correlation_id=:id order by event_time limit 500").param("id",value).query().listOfRows());
    rows.addAll(database.sql("select 'AUDIT' log_kind,* from audit_log where correlation_id=:id order by event_time limit 500").param("id",value).query().listOfRows());
    rows.sort((a,b)->((Instant)a.get("event_time")).compareTo((Instant)b.get("event_time")));
    return Map.of("correlationId",value,"items",rows,"truncated",rows.size()>=1000);
  }

  @GetMapping("/api/summary")
  public Map<String,Object> summary(HttpServletRequest request,
      @RequestParam(required=false) Instant from,@RequestParam(required=false) Instant to) {
    require(request,"can_view");Query q=apiQuery(from,to,null,null,null,null,null,null);
    String where=q.where();JdbcClient.StatementSpec statement=database.sql("select count(*) total,count(*) filter(where status_code>=400) errors,coalesce(round(avg(duration_ms)),0) average_duration_ms,coalesce(max(duration_ms),0) max_duration_ms from api_log"+where);
    statement=bind(statement,q.params);return statement.query().singleRow();
  }

  private Query apiQuery(Instant from,Instant to,String userId,String service,Integer status,String route,String correlation,String authorization) {
    Query q=new Query();q.add("event_time >= :from","from",from);q.add("event_time < :to","to",to);
    q.add("user_id = :user","user",bounded(userId));q.add("service_name = :service","service",bounded(service));q.add("status_code = :status","status",status);
    if(route!=null&&!route.isBlank())q.add("route_template ilike :route","route","%"+bounded(route)+"%");
    q.add("correlation_id = :correlation","correlation",bounded(correlation));q.add("authorization_result = :authorization","authorization",bounded(authorization));return q;
  }

  private Map<String,Object> page(String table,Query query,Page page) {
    String where=query.where();JdbcClient.StatementSpec rows=database.sql("select * from "+table+where+" order by event_time desc,id desc limit :limit offset :offset");
    rows=bind(rows,query.params).param("limit",page.size).param("offset",page.page*page.size);
    JdbcClient.StatementSpec count=bind(database.sql("select count(*) from "+table+where),query.params);
    return Map.of("items",rows.query().listOfRows(),"page",page.page,"size",page.size,"total",count.query(Long.class).single());
  }
  private Map<String,Object> one(String table,String id) {
    try{List<Map<String,Object>> rows=database.sql("select * from "+table+" where id=cast(:id as uuid)").param("id",bounded(id)).query().listOfRows();if(rows.isEmpty())throw new ResponseStatusException(HttpStatus.NOT_FOUND);return rows.getFirst();}
    catch(IllegalArgumentException badId){throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Invalid log id");}
  }
  private void require(HttpServletRequest request,String relation) {
    String actor=request.getHeader("X-Actor");
    if(actor==null||!relationships.check("user:"+actor,relation,LOG_RESOURCE))throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Log permission required");
  }
  private static JdbcClient.StatementSpec bind(JdbcClient.StatementSpec statement,Map<String,Object> params){for(var item:params.entrySet())statement=statement.param(item.getKey(),item.getValue());return statement;}
  private static String bounded(String value){if(value==null||value.isBlank())return null;if(value.length()>500)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Filter is too long");return value;}
  private record Page(int page,int size){static Page of(int page,int size){if(page<0||size<1||size>200)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"page must be >= 0 and size between 1 and 200");return new Page(page,size);}}
  private static final class Query {private final List<String> clauses=new ArrayList<>();private final Map<String,Object> params=new LinkedHashMap<>();void add(String clause,String name,Object value){if(value!=null){clauses.add(clause);params.put(name,value);}}String where(){return clauses.isEmpty()?"":" where "+String.join(" and ",clauses);}}
}

package com.aurevia.authz.routing;

import java.sql.SQLException;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcProxyRouteRepository implements ProxyRouteRepository {
  private final JdbcClient database;
  JdbcProxyRouteRepository(JdbcClient database) { this.database=database; }

  @Override public List<Map<String,Object>> targets(String search) { return database.sql("""
      select st.*,(select count(*) from proxy_route pr where pr.service_target_id=st.id) route_count
      from service_target st
      where (:search='' or lower(st.code||' '||st.name) like lower('%'||:search||'%'))
      order by st.code
      """).param("search",search).query().listOfRows(); }
  @Override public Optional<Map<String,Object>> target(UUID id) { return rows(
      "select * from service_target where id=:id",id).stream().findFirst(); }
  @Override public void insertTarget(TargetValue value) { database.sql("""
      insert into service_target(id,code,name,description,gateway_base_url,upstream_base_path,
        environment,tls_profile_ref,secret_ref,health_check_path,connect_timeout_ms,
        response_timeout_ms,max_response_size,outbound_auth_profile_id,active,created_by,updated_by)
      values(:id,:code,:name,nullif(:description,''),:url,:upstream,:environment,
        nullif(:tls,''),nullif(:secret,''),:health,:connect,:response,:size,
        coalesce(cast(:authProfile as uuid),(select id from outbound_auth_profile where code='public-iam-forward')),
        :active,:actor,:actor)
      """).param("id",value.id()).param("code",value.code()).param("name",value.name())
      .param("description",value.description()).param("url",value.gatewayBaseUrl())
      .param("upstream",value.upstreamBasePath()).param("environment",value.environment())
      .param("tls",value.tlsProfileRef()).param("secret",value.secretRef())
      .param("health",value.healthCheckPath()).param("connect",value.connectTimeoutMs())
      .param("response",value.responseTimeoutMs()).param("size",value.maxResponseSize())
      .param("authProfile",value.outboundAuthProfileId()==null?null:value.outboundAuthProfileId().toString())
      .param("active",value.active()).param("actor",value.actor()).update(); }
  @Override public boolean updateTarget(UUID id,long version,TargetValue value) { return database.sql("""
      update service_target set code=:code,name=:name,description=nullif(:description,''),
        gateway_base_url=:url,upstream_base_path=:upstream,environment=:environment,
        tls_profile_ref=nullif(:tls,''),secret_ref=nullif(:secret,''),health_check_path=:health,
        connect_timeout_ms=:connect,response_timeout_ms=:response,max_response_size=:size,
        outbound_auth_profile_id=coalesce(cast(:authProfile as uuid),outbound_auth_profile_id),
        active=:active,version=version+1,updated_at=now(),updated_by=:actor
      where id=:id and version=:version
      """).param("code",value.code()).param("name",value.name())
      .param("description",value.description()).param("url",value.gatewayBaseUrl())
      .param("upstream",value.upstreamBasePath()).param("environment",value.environment())
      .param("tls",value.tlsProfileRef()).param("secret",value.secretRef())
      .param("health",value.healthCheckPath()).param("connect",value.connectTimeoutMs())
      .param("response",value.responseTimeoutMs()).param("size",value.maxResponseSize())
      .param("authProfile",value.outboundAuthProfileId()==null?null:value.outboundAuthProfileId().toString())
      .param("active",value.active()).param("actor",value.actor()).param("id",id)
      .param("version",version).update()==1; }
  @Override public boolean updateTargetStatus(UUID id,long version,boolean active,String actor) {
    return database.sql("""
      update service_target set active=:active,version=version+1,updated_at=now(),updated_by=:actor
      where id=:id and version=:version
      """).param("active",active).param("actor",actor).param("id",id)
      .param("version",version).update()==1; }
  @Override public List<Map<String,Object>> routes(String search,UUID panelId,UUID targetId,
      Boolean active) { return database.sql("""
      select pr.*,p.slug panel_slug,p.name_fa panel_name,st.code target_code
      from proxy_route pr join panel p on p.id=pr.panel_id
      join service_target st on st.id=pr.service_target_id
      where (:search='' or lower(pr.code||' '||pr.path_prefix) like lower('%'||:search||'%'))
        and (nullif(:panel,'') is null or pr.panel_id=cast(nullif(:panel,'') as uuid))
        and (nullif(:target,'') is null or pr.service_target_id=cast(nullif(:target,'') as uuid))
        and (nullif(:active,'') is null or pr.active=cast(nullif(:active,'') as boolean))
      order by length(pr.normalized_path_prefix) desc,pr.priority desc,pr.code
      """).param("search",search).param("panel",panelId==null?"":panelId.toString())
      .param("target",targetId==null?"":targetId.toString())
      .param("active",active==null?"":active.toString()).query().listOfRows().stream()
      .map(JdbcProxyRouteRepository::serializableRow).toList(); }
  @Override public Optional<Map<String,Object>> route(UUID id) { return rows(
      "select * from proxy_route where id=:id",id).stream().findFirst()
      .map(JdbcProxyRouteRepository::serializableRow); }
  @Override public void insertRoute(RouteValue value) { database.sql("""
      insert into proxy_route(id,code,panel_id,service_target_id,service_slug,path_prefix,
        normalized_path_prefix,strip_prefix,rewrite_pattern,rewrite_replacement,priority,
        allowed_methods,preserve_host,retry_enabled,max_retries,active,created_by,updated_by)
      values(:id,:code,:panel,:target,:serviceSlug,:path,:normalized,:strip,
        nullif(:pattern,''),nullif(:replacement,''),:priority,:methods,:host,:retry,
        :retries,:active,:actor,:actor)
      """).param("id",value.id()).param("code",value.code()).param("panel",value.panelId())
      .param("target",value.serviceTargetId()).param("serviceSlug",value.serviceSlug())
      .param("path",value.pathPrefix()).param("normalized",value.normalizedPathPrefix())
      .param("strip",value.stripPrefix()).param("pattern",value.rewritePattern())
      .param("replacement",value.rewriteReplacement()).param("priority",value.priority())
      .param("methods",value.allowedMethods()).param("host",value.preserveHost())
      .param("retry",value.retryEnabled()).param("retries",value.maxRetries())
      .param("active",value.active()).param("actor",value.actor()).update(); }
  @Override public boolean updateRoute(UUID id,long version,RouteValue value) { return database.sql("""
      update proxy_route set code=:code,panel_id=:panel,service_target_id=:target,
        service_slug=:serviceSlug,path_prefix=:path,normalized_path_prefix=:normalized,
        strip_prefix=:strip,rewrite_pattern=nullif(:pattern,''),
        rewrite_replacement=nullif(:replacement,''),priority=:priority,
        allowed_methods=:methods,preserve_host=:host,retry_enabled=:retry,
        max_retries=:retries,active=:active,version=version+1,updated_at=now(),updated_by=:actor
      where id=:id and version=:version
      """).param("code",value.code()).param("panel",value.panelId())
      .param("target",value.serviceTargetId()).param("serviceSlug",value.serviceSlug())
      .param("path",value.pathPrefix()).param("normalized",value.normalizedPathPrefix())
      .param("strip",value.stripPrefix()).param("pattern",value.rewritePattern())
      .param("replacement",value.rewriteReplacement()).param("priority",value.priority())
      .param("methods",value.allowedMethods()).param("host",value.preserveHost())
      .param("retry",value.retryEnabled()).param("retries",value.maxRetries())
      .param("active",value.active()).param("actor",value.actor()).param("id",id)
      .param("version",version).update()==1; }
  @Override public boolean updateRouteStatus(UUID id,long version,boolean active,String actor) {
    return database.sql("""
      update proxy_route set active=:active,version=version+1,updated_at=now(),updated_by=:actor
      where id=:id and version=:version
      """).param("active",active).param("actor",actor).param("id",id)
      .param("version",version).update()==1; }
  @Override public List<Map<String,Object>> operations(UUID routeId) { return database.sql("""
      select * from route_operation where proxy_route_id=:id
      order by http_method,normalized_path_pattern
      """).param("id",routeId).query().listOfRows(); }
  @Override public Optional<Map<String,Object>> operation(UUID id) { return rows(
      "select * from route_operation where id=:id",id).stream().findFirst(); }
  @Override public void insertOperation(OperationValue value) { database.sql("""
      insert into route_operation(id,proxy_route_id,http_method,path_pattern,
        normalized_path_pattern,resource_id,action_id,resource_key,action_key,
        authorization_required,data_policy_key,active,max_body_bytes,created_by,updated_by)
      values(:id,:route,:method,:pattern,:pattern,:resource,
        (select id from action where action_key=:action),:resourceKey,:action,
        :required,nullif(:policy,''),:active,:body,:actor,:actor)
      """).param("id",value.id()).param("route",value.routeId())
      .param("method",value.httpMethod()).param("pattern",value.pathPattern())
      .param("resource",value.resourceId()).param("action",value.actionKey())
      .param("resourceKey",value.resourceKey()).param("required",value.authorizationRequired())
      .param("policy",value.dataPolicyKey()).param("active",value.active())
      .param("body",value.maxBodyBytes()).param("actor",value.actor()).update(); }
  @Override public boolean updateOperation(UUID id,UUID routeId,long version,OperationValue value) {
    return database.sql("""
      update route_operation set http_method=:method,path_pattern=:pattern,
        normalized_path_pattern=:pattern,resource_id=:resource,
        action_id=(select id from action where action_key=:action),resource_key=:resourceKey,
        action_key=:action,authorization_required=:required,data_policy_key=nullif(:policy,''),
        active=:active,max_body_bytes=:body,version=version+1,updated_at=now(),updated_by=:actor
      where id=:id and proxy_route_id=:route and version=:version
      """).param("method",value.httpMethod()).param("pattern",value.pathPattern())
      .param("resource",value.resourceId()).param("action",value.actionKey())
      .param("resourceKey",value.resourceKey()).param("required",value.authorizationRequired())
      .param("policy",value.dataPolicyKey()).param("active",value.active())
      .param("body",value.maxBodyBytes()).param("actor",value.actor()).param("id",id)
      .param("route",routeId).param("version",version).update()==1; }
  @Override public boolean disableOperation(UUID id,UUID routeId,long version) { return database.sql("""
      update route_operation set active=false,version=version+1,updated_at=now()
      where id=:id and proxy_route_id=:route and version=:version
      """).param("id",id).param("route",routeId).param("version",version).update()==1; }
  @Override public Optional<UUID> resourceAction(String resourceKey,String actionKey) {
    return database.sql("""
      select r.id from resource r join resource_action ra on ra.resource_id=r.id
      join action a on a.id=ra.action_id
      where r.resource_key=:resource and r.status='ACTIVE' and a.action_key=:action
      """).param("resource",resourceKey).param("action",actionKey).query(UUID.class).optional(); }
  @Override public long operationConflict(UUID routeId,String method,String pattern,UUID self) {
    return database.sql("""
      select count(*) from route_operation where proxy_route_id=:route
        and http_method=:method and normalized_path_pattern=:pattern and id<>:self
      """).param("route",routeId).param("method",method).param("pattern",pattern)
      .param("self",self).query(Long.class).single(); }
  @Override public Optional<String> panelSlug(UUID panelId) { return database.sql(
      "select slug from panel where id=:id").param("id",panelId)
      .query(String.class).optional(); }
  @Override public boolean targetExists(UUID id) { return database.sql(
      "select count(*) from service_target where id=:id").param("id",id)
      .query(Long.class).single()>0; }
  @Override public long routeConflict(String prefix,int priority,UUID self) { return database.sql("""
      select count(*) from proxy_route where normalized_path_prefix=:prefix
        and priority=:priority and id<>:self
      """).param("prefix",prefix).param("priority",priority).param("self",self)
      .query(Long.class).single(); }

  private List<Map<String,Object>> rows(String sql,UUID id) {
    return database.sql(sql).param("id",id).query().listOfRows();
  }
  private static Map<String,Object> serializableRow(Map<String,Object> row) {
    Map<String,Object> result=new LinkedHashMap<>(row);
    result.replaceAll((key,value)->{
      if(!(value instanceof java.sql.Array array)) return value;
      try { return Arrays.asList((Object[])array.getArray()); }
      catch(SQLException failure) { throw new IllegalStateException("Cannot read SQL array",failure); }
    });
    return result;
  }
}

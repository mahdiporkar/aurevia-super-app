package com.aurevia.authz.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.aurevia.authz.openfga.RelationshipAuthorizationPort;
import com.aurevia.authz.semantics.AuthorizationSemanticsRegistry;
import com.aurevia.authz.policy.RuntimePolicyService;
import com.aurevia.authz.audit.AuthorizationDecisionAuditor;
import org.springframework.jdbc.core.simple.JdbcClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;

@RestController
@RequestMapping("/internal/v1")
public class AuthorizationController {
  private final RelationshipAuthorizationPort relationships;
  private final JdbcClient db;
  private final AuthorizationSemanticsRegistry semantics;
  private final RuntimePolicyService policies;
  private final AuthorizationDecisionAuditor auditor;
  private final ObjectMapper json;
  public AuthorizationController(RelationshipAuthorizationPort relationships, JdbcClient db,
      AuthorizationSemanticsRegistry semantics, RuntimePolicyService policies,
      AuthorizationDecisionAuditor auditor,ObjectMapper json) {
    this.relationships = relationships;
    this.db = db;
    this.semantics = semantics;
    this.policies = policies;
    this.auditor = auditor;
    this.json=json;
  }
  @PostMapping("/authorize/check")
  public Decision check(@Valid @RequestBody CheckRequest request) {
    long started = System.nanoTime();
    String decisionId = UUID.randomUUID().toString();
    String permission = semantics.resolveObject(request.resource(), request.action()).permission();
    long openFgaStarted = System.nanoTime();
    boolean relationshipAllowed = relationships.check("user:" + request.subjectId(),
        permission, request.resource());
    long openFgaDurationMs = (System.nanoTime() - openFgaStarted) / 1_000_000;
    RuntimePolicyService.Evaluation policy = relationshipAllowed
        ? policies.evaluate(request.subjectId(), request.resource(), request.action())
        : new RuntimePolicyService.Evaluation(false, "NOT_EVALUATED", Map.of(), List.of());
    boolean allowed = relationshipAllowed && policy.allowed();
    String reason = !relationshipAllowed ? "NO_RELATIONSHIP" : policy.reasonCode();
    long latencyMs = (System.nanoTime() - started) / 1_000_000;
    auditor.record(new AuthorizationDecisionAuditor.Record(decisionId, request.subjectId(),
        request.resource(), request.action(), permission, relationshipAllowed, policy.allowed(),
        allowed, reason, latencyMs, request.correlationId(), policy.policies()));
    try {
      var servlet=((org.springframework.web.context.request.ServletRequestAttributes)
          org.springframework.web.context.request.RequestContextHolder.currentRequestAttributes()).getRequest();
      servlet.setAttribute("authorizationResult",allowed?"ALLOW":"DENY");
      servlet.setAttribute("resourceType",request.resource().substring(0,request.resource().indexOf(':')));
      servlet.setAttribute("resourceId",request.resource().substring(request.resource().indexOf(':')+1));
      servlet.setAttribute("businessAction",request.action());servlet.setAttribute("openfgaDurationMs",openFgaDurationMs);
    } catch (IllegalStateException ignored) { /* Unit/non-servlet invocation. */ }
    return new Decision(allowed ? "ALLOW" : "DENY", reason, "configured-model", decisionId,
        allowed ? policy.obligations() : Map.of());
  }


  @PostMapping("/authorize/check-batch")
  public List<Decision> checkBatch(@RequestBody List<@Valid CheckRequest> requests) {
    return requests.stream().map(this::check).toList();
  }

  @GetMapping("/subjects/{id}/manifest")
  public ResponseEntity<Manifest> manifest(@PathVariable("id") String id) {
    var panels=db.sql("select p.id,p.code,p.slug,p.name_fa as \"nameFa\",p.name_en as \"nameEn\",p.remote_entry_path as \"remoteEntry\",p.exposed_module as \"exposedModule\",p.route_base_path as \"routeBasePath\",p.semantic_version as \"semanticVersion\",p.contract_version as \"contractVersion\",p.integrity,p.description,p.service_slug,p.remote_name,p.default_route_id,p.sort_order,a.id artifact_id,a.artifact_version,a.remote_entry_url,a.remote_name artifact_remote_name,a.exposed_module artifact_exposed_module,a.contract_version artifact_contract_version,a.integrity artifact_integrity,a.manifest_snapshot::text manifest_json from panel p join ui_module_artifact a on a.id=p.active_artifact_id and a.validation_status='VALID' where p.active order by p.sort_order,p.code").query().listOfRows().stream()
        .filter(panel -> relationships.check("user:" + id, "can_view",
            "application:aurevia/" + panel.get("slug"))).toList();
    var rows=db.sql("""
        with effective_roles as (
          select ura.role_id from app_user u join user_role_assignment ura on ura.user_id=u.id
          where (u.external_id=:id or u.username=:id) and (ura.expires_at is null or ura.expires_at>now())
          union
          select gra.role_id from app_user u join user_group_membership ugm on ugm.user_id=u.id
          join group_role_assignment gra on gra.group_id=ugm.group_id
          where (u.external_id=:id or u.username=:id) and (gra.expires_at is null or gra.expires_at>now())
        ), effective_grants as (
          select g.resource_id,g.action_id from effective_roles er join authorization_grant g
            on g.subject_type='ROLE' and g.subject_id=er.role_id
          where g.status='ACTIVE' and (g.expires_at is null or g.expires_at>now())
          union
          select g.resource_id,g.action_id from app_user u join authorization_grant g
            on g.subject_type='USER' and g.subject_id=u.id
          where (u.external_id=:id or u.username=:id) and g.status='ACTIVE'
            and (g.expires_at is null or g.expires_at>now())
          union
          select g.resource_id,g.action_id from app_user u join user_group_membership ugm on ugm.user_id=u.id
          join authorization_grant g on g.subject_type='GROUP' and g.subject_id=ugm.group_id
          where (u.external_id=:id or u.username=:id) and g.status='ACTIVE'
            and (g.expires_at is null or g.expires_at>now())
        )
        select r.resource_key,a.action_key from effective_grants eg
        join resource r on r.id=eg.resource_id join action a on a.id=eg.action_id
        """).param("id",id).query().listOfRows();
    Map<String,List<String>> permissions=new java.util.LinkedHashMap<>(); rows.forEach(r->permissions.computeIfAbsent((String)r.get("resource_key"),k->new java.util.ArrayList<>()).add((String)r.get("action_key")));
    var catalog=db.sql("select id,parent_id,resource_key,type,name_fa,name_en,owner_domain,classification from resource where status='ACTIVE' order by resource_key").query().listOfRows();
    var byId=new java.util.HashMap<UUID,Map<String,Object>>();catalog.forEach(resource->byId.put((UUID)resource.get("id"),resource));
    var included=new java.util.LinkedHashSet<UUID>();
    catalog.stream().filter(resource->permissions.containsKey(resource.get("resource_key"))).forEach(resource->{
      UUID cursor=(UUID)resource.get("id");
      while(cursor!=null&&included.add(cursor)){var node=byId.get(cursor);cursor=node==null?null:(UUID)node.get("parent_id");}
    });
    var resourceTree=catalog.stream().filter(resource->included.contains(resource.get("id"))).map(resource->{
      Map<String,Object> node=new java.util.LinkedHashMap<>();node.putAll(resource);
      node.put("actions",permissions.getOrDefault((String)resource.get("resource_key"),List.of()));return node;
    }).toList();
    var modules=panels.stream().map(panel->uiModule(panel,permissions)).filter(java.util.Objects::nonNull).toList();
    String version="manifest-"+Integer.toHexString((panels.toString()+permissions+resourceTree+modules).hashCode());
    return ResponseEntity.ok().eTag("\""+version+"\"").cacheControl(org.springframework.http.CacheControl.noCache()).body(new Manifest(
        "EFFECTIVE_USER_MANIFEST",Map.of("type","user","id",id),version,
        Instant.now().plusSeconds(60), List.copyOf(panels), permissions,resourceTree,new UiCatalog(version,Instant.now(),"1.0",modules)));
  }

  private Map<String,Object> uiModule(Map<String,Object> panel,Map<String,List<String>> permissions){try{
    JsonNode manifest=json.readTree((String)panel.get("manifest_json"));var routes=new java.util.ArrayList<Map<String,Object>>();var allowedRouteIds=new java.util.HashSet<String>();
    for(JsonNode route:manifest.path("routes")){String resource=route.path("resource").asText(null),action=route.path("action").asText("view");if(resource!=null&&!permissions.getOrDefault(resource,List.of()).contains(action))continue;routes.add(json.convertValue(route,Map.class));allowedRouteIds.add(route.path("id").asText());}
    var overrides=db.sql("select menu_id,title,icon,sort_order,hidden from ui_menu_override where panel_id=:panel").param("panel",panel.get("id")).query().listOfRows().stream().collect(java.util.stream.Collectors.toMap(x->String.valueOf(x.get("menu_id")),x->x));
    var menus=new java.util.ArrayList<Map<String,Object>>();for(JsonNode item:manifest.path("menus")){if(!allowedRouteIds.contains(item.path("routeId").asText()))continue;var override=overrides.get(item.path("id").asText());if(override!=null&&Boolean.TRUE.equals(override.get("hidden")))continue;Map<String,Object> menu=json.convertValue(item,Map.class);if(override!=null){if(override.get("title")!=null)menu.put("title",override.get("title"));if(override.get("icon")!=null)menu.put("icon",override.get("icon"));if(override.get("sort_order")!=null)menu.put("order",override.get("sort_order"));}menus.add(menu);}menus.sort(java.util.Comparator.comparingInt(x->((Number)x.getOrDefault("order",0)).intValue()));
    Map<String,Object> remote=new java.util.LinkedHashMap<>();remote.put("remoteEntryUrl",panel.get("remote_entry_url"));remote.put("remoteName",panel.get("artifact_remote_name"));remote.put("exposedModule",panel.get("artifact_exposed_module"));remote.put("contractVersion",panel.get("artifact_contract_version"));remote.put("artifactVersion",panel.get("artifact_version"));if(panel.get("artifact_integrity")!=null)remote.put("integrity",panel.get("artifact_integrity"));
    Map<String,Object> module=new java.util.LinkedHashMap<>();module.put("registrationId",panel.get("id"));module.put("moduleKey",panel.get("slug"));module.put("displayName",panel.get("nameFa"));module.put("displayNameEn",panel.get("nameEn"));module.put("description",panel.get("description"));module.put("order",panel.get("sort_order"));module.put("routePrefix",String.valueOf(panel.get("routeBasePath")).replaceFirst("^/",""));module.put("defaultRouteId",panel.get("default_route_id"));module.put("remote",remote);module.put("runtime",Map.of("apiBasePath","/api/proxy/"+panel.get("service_slug")));module.put("routes",routes);module.put("menus",menus);return module;
  }catch(Exception e){throw new IllegalStateException("stored UI manifest is invalid",e);}}

  public record CheckRequest(@NotBlank String subjectId, @NotBlank String issuer,
      @NotBlank String resource, @NotBlank String action, Map<String,Object> context, @NotBlank String correlationId) {}
  public record Decision(String result, String reasonCode, String modelVersion, String decisionId, Map<String,Object> obligations) {}
  public record UiCatalog(String catalogVersion,Instant generatedAt,String contractVersion,List<Map<String,Object>> modules){}
  public record Manifest(String manifestType,Map<String,String> subject,String version, Instant expiresAt,
      List<Object> panels, Map<String,List<String>> permissions,List<Map<String,Object>> resourceTree,UiCatalog uiCatalog) {}
}

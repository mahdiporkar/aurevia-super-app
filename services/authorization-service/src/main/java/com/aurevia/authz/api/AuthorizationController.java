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
import org.springframework.jdbc.core.simple.JdbcClient;

@RestController
@RequestMapping("/internal/v1")
public class AuthorizationController {
  private final RelationshipAuthorizationPort relationships;
  private final JdbcClient db;
  public AuthorizationController(RelationshipAuthorizationPort relationships, JdbcClient db) { this.relationships = relationships; this.db=db; }
  @PostMapping("/authorize/check")
  public Decision check(@Valid @RequestBody CheckRequest request) {
    boolean allowed = relationships.check("user:" + request.subjectId(),
        permission(request.action()), request.resource());
    return new Decision(allowed ? "ALLOW" : "DENY", allowed ? "RELATIONSHIP_ALLOWED" : "NO_RELATIONSHIP", "configured-model", UUID.randomUUID().toString(), Map.of());
  }

  private static String permission(String action) {
    return switch (action) {
      case "list", "view" -> "can_view";
      case "create" -> "can_create";
      case "update", "approve", "reject" -> "can_edit";
      case "delete" -> "can_delete";
      case "admin", "manage" -> "can_manage";
      default -> action.startsWith("can_") ? action : "unsupported_action";
    };
  }

  @PostMapping("/authorize/check-batch")
  public List<Decision> checkBatch(@RequestBody List<@Valid CheckRequest> requests) {
    return requests.stream().map(this::check).toList();
  }

  @GetMapping("/subjects/{id}/manifest")
  public ResponseEntity<Manifest> manifest(@PathVariable("id") String id) {
    var panels=db.sql("select id,code,slug,name_fa as \"nameFa\",name_en as \"nameEn\",remote_entry_path as \"remoteEntry\",exposed_module as \"exposedModule\",route_base_path as \"routeBasePath\",semantic_version as \"semanticVersion\",contract_version as \"contractVersion\",integrity from panel where active order by sort_order,code").query().listOfRows().stream()
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
    String version="manifest-"+Integer.toHexString((panels.toString()+permissions+resourceTree).hashCode());
    return ResponseEntity.ok().eTag("\""+version+"\"").cacheControl(org.springframework.http.CacheControl.noCache()).body(new Manifest(version, Instant.now().plusSeconds(60), List.copyOf(panels), permissions,resourceTree));
  }

  public record CheckRequest(@NotBlank String subjectId, @NotBlank String issuer,
      @NotBlank String resource, @NotBlank String action, Map<String,Object> context, @NotBlank String correlationId) {}
  public record Decision(String result, String reasonCode, String modelVersion, String decisionId, Map<String,Object> obligations) {}
  public record Manifest(String version, Instant expiresAt, List<Object> panels, Map<String,List<String>> permissions,List<Map<String,Object>> resourceTree) {}
}

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
    boolean allowed = relationships.check("user:" + request.subjectId(), request.action(), request.resource());
    return new Decision(allowed ? "ALLOW" : "DENY", allowed ? "RELATIONSHIP_ALLOWED" : "NO_RELATIONSHIP", "configured-model", UUID.randomUUID().toString(), Map.of());
  }

  @PostMapping("/authorize/check-batch")
  public List<Decision> checkBatch(@RequestBody List<@Valid CheckRequest> requests) {
    return requests.stream().map(this::check).toList();
  }

  @GetMapping("/subjects/{id}/manifest")
  public ResponseEntity<Manifest> manifest(@PathVariable String id) {
    var panels=db.sql("select id,code,slug,name_fa as \"nameFa\",name_en as \"nameEn\",remote_entry_path as \"remoteEntry\",exposed_module as \"exposedModule\",route_base_path as \"routeBasePath\",semantic_version as \"semanticVersion\",contract_version as \"contractVersion\",integrity from panel where active order by sort_order,code").query().listOfRows();
    var rows=db.sql("select r.resource_key,a.action_key from app_user u join user_role_assignment ura on ura.user_id=u.id join authorization_grant g on g.subject_type='ROLE' and g.subject_id=ura.role_id and g.status='ACTIVE' join resource r on r.id=g.resource_id join action a on a.id=g.action_id where u.external_id=:id and (ura.expires_at is null or ura.expires_at>now()) and (g.expires_at is null or g.expires_at>now()) union select r.resource_key,a.action_key from app_user u join authorization_grant g on g.subject_type='USER' and g.subject_id=u.id and g.status='ACTIVE' join resource r on r.id=g.resource_id join action a on a.id=g.action_id where u.external_id=:id and (g.expires_at is null or g.expires_at>now())").param("id",id).query().listOfRows();
    Map<String,List<String>> permissions=new java.util.LinkedHashMap<>(); rows.forEach(r->permissions.computeIfAbsent((String)r.get("resource_key"),k->new java.util.ArrayList<>()).add((String)r.get("action_key")));
    String version="manifest-"+Integer.toHexString((panels.toString()+permissions).hashCode());
    return ResponseEntity.ok().eTag("\""+version+"\"").cacheControl(org.springframework.http.CacheControl.noCache()).body(new Manifest(version, Instant.now().plusSeconds(60), List.copyOf(panels), permissions));
  }

  public record CheckRequest(@NotBlank String subjectId, @NotBlank String issuer,
      @NotBlank String resource, @NotBlank String action, Map<String,Object> context, @NotBlank String correlationId) {}
  public record Decision(String result, String reasonCode, String modelVersion, String decisionId, Map<String,Object> obligations) {}
  public record Manifest(String version, Instant expiresAt, List<Object> panels, Map<String,List<String>> permissions) {}
}

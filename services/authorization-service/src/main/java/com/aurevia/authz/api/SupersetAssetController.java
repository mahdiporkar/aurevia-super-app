package com.aurevia.authz.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestParam;
import com.aurevia.authz.openfga.RelationshipAuthorizationPort;
import com.aurevia.authz.observability.AuditTrail;

@RestController
@RequestMapping("/internal/v1/registry")
public class SupersetAssetController {
  private final JdbcClient database;
  private final RelationshipAuthorizationPort relationships;
  private final AuditTrail auditTrail;

  public SupersetAssetController(JdbcClient database, RelationshipAuthorizationPort relationships,AuditTrail auditTrail) {
    this.database = database;
    this.relationships = relationships;
    this.auditTrail = auditTrail;
  }

  @GetMapping("/superset-assets")
  public List<Map<String, Object>> assets() {
    return database.sql("""
        select sa.id, sa.resource_id, sa.external_id, sa.asset_type, sa.title,
               sa.url_path, sa.owner_external_id, sa.published, sa.tags::text tags_json,
               r.resource_key, r.name_fa, r.name_en
        from superset_asset sa
        join resource r on r.id = sa.resource_id
        order by sa.asset_type, sa.title
        """).query().listOfRows();
  }

  @GetMapping("/subjects/{subject}/superset-assets")
  public List<Map<String, Object>> assetsForSubject(
      @PathVariable("subject") String subject) {
    return publishedAssets().stream().filter(asset -> canView(subject, asset)).toList();
  }

  @GetMapping("/subjects/{subject}/superset-access")
  public Map<String, Object> accessForSubject(@PathVariable("subject") String subject,
      @RequestParam("path") String path,
      @RequestParam(value = "method", required = false, defaultValue = "GET") String method,
      @RequestParam(value = "query", required = false, defaultValue = "") String query) {
    boolean administrator = relationships.check(
        "user:" + subject, "can_manage", "application:aurevia");
    if (administrator) return Map.of("result", "ALLOW", "reasonCode", "SUPERSET_ADMIN_ALLOWED");
    List<Map<String, Object>> allowed = publishedAssets().stream()
        .filter(asset -> canView(subject, asset)).toList();
    boolean assetSpecific = isAssetSpecific(path, query);
    boolean safeRuntime = Set.of("GET", "HEAD", "OPTIONS").contains(method.toUpperCase())
        || path.startsWith("/api/v1/chart/data") || path.startsWith("/superset/log");
    boolean catalog = path.equals("/dashboard/list/") || path.equals("/dashboard/list")
        || path.matches("/api/v1/(?:dashboard|chart)/?");
    boolean granted = safeRuntime && !catalog && (assetSpecific
        ? allowed.stream().anyMatch(asset -> matches(asset, path, query))
        : !allowed.isEmpty());
    return Map.of("result", granted ? "ALLOW" : "DENY",
        "reasonCode", granted ? "SUPERSET_ASSET_ALLOWED" : "SUPERSET_ASSET_DENIED");
  }

  private List<Map<String, Object>> publishedAssets() {
    return database.sql("""
        select sa.id,sa.resource_id,sa.external_id,sa.asset_type,sa.title,sa.url_path,
               sa.owner_external_id,sa.tags::text tags_json,r.resource_key
        from superset_asset sa join resource r on r.id=sa.resource_id
        where sa.published=true order by sa.title
        """).query().listOfRows();
  }

  private boolean canView(String subject, Map<String, Object> asset) {
    String key = String.valueOf(asset.get("resource_key"));
    String object = "external_resource:"
        + key.replaceFirst("^external_resource:", "").replace(':', '/');
    return relationships.check("user:" + subject, "can_view", object);
  }

  private static boolean isAssetSpecific(String path, String query) {
    return path.matches(".*/(?:dashboard|chart)/[0-9]+/?$")
        || path.startsWith("/explore") || query.matches(".*(?:slice_id|dashboard_id)=[0-9]+.*");
  }

  private static boolean matches(Map<String, Object> asset, String path, String query) {
    String external = String.valueOf(asset.get("external_id"));
    String url = String.valueOf(asset.get("url_path"));
    if (normalize(path).equals(normalize(url))) return true;
    String id = external.contains(":") ? external.substring(external.lastIndexOf(':') + 1) : external;
    String type = String.valueOf(asset.get("asset_type"));
    if ("DASHBOARD".equalsIgnoreCase(type)) {
      return path.matches(".*/dashboard/" + java.util.regex.Pattern.quote(id) + "/?$")
          || query.matches(".*dashboard_id=" + java.util.regex.Pattern.quote(id) + "(?:&.*)?");
    }
    return path.matches(".*/chart/" + java.util.regex.Pattern.quote(id) + "/?$")
        || query.matches(".*slice_id=" + java.util.regex.Pattern.quote(id) + "(?:&.*)?");
  }

  private static String normalize(String path) {
    if (path == null || path.isBlank()) return "/";
    return path.length() > 1 && path.endsWith("/") ? path.substring(0, path.length() - 1) : path;
  }

  @GetMapping("/superset-assets/{assetId}/grants")
  public List<Map<String, Object>> assetGrants(@PathVariable("assetId") UUID assetId) {
    return database.sql("""
        select g.id,g.subject_type::text subject_type,g.subject_id,
               coalesce(u.display_name,dg.display_name,ar.name_fa) subject_name,
               coalesce(u.username,dg.normalized_path,ar.role_key) subject_key,
               a.action_key, g.relation, g.expires_at
        from superset_asset sa
        join authorization_grant g
          on g.resource_id = sa.resource_id
         and g.status = 'ACTIVE'
        left join app_user u on g.subject_type='USER' and u.id=g.subject_id
        left join directory_group dg on g.subject_type='GROUP' and dg.id=g.subject_id
        left join application_role ar on g.subject_type='ROLE' and ar.id=g.subject_id
        join action a on a.id = g.action_id
        where sa.id = :assetId
          and (g.expires_at is null or g.expires_at > now())
        order by u.username, a.action_key
        """).param("assetId", assetId).query().listOfRows();
  }

  @PostMapping("/superset-assets")
  @ResponseStatus(HttpStatus.CREATED)
  @Transactional
  public Map<String, Object> create(@Valid @RequestBody AssetWrite request) {
    List<Map<String,Object>> existing=database.sql("select sa.id,sa.resource_id,r.resource_key from superset_asset sa join resource r on r.id=sa.resource_id where sa.external_id=:external and sa.asset_type=:type").param("external",request.externalId()).param("type",request.assetType()).query().listOfRows();
    if(!existing.isEmpty())return Map.of("id",existing.getFirst().get("id"),"resourceId",existing.getFirst().get("resource_id"),"resourceKey",existing.getFirst().get("resource_key"),"existing",true);
    UUID resourceId = UUID.randomUUID();
    UUID assetId = UUID.randomUUID();
    UUID parentId = database.sql("""
        select id from resource where resource_key = 'external_resource:superset-public'
        """).query(UUID.class).single();
    String resourceKey = "external_resource:superset-public:" + request.assetType().toLowerCase()
        + ":" + request.externalId();

    database.sql("""
        insert into resource(
          id, resource_key, type, parent_id, name_fa, name_en,
          owner_domain, external_system, external_type, external_id
        ) values (
          :id, :key, 'EXTERNAL_RESOURCE', :parent, :title, :title,
          'reports', 'superset-public', :assetType, :externalId
        )
        """)
        .param("id", resourceId)
        .param("key", resourceKey)
        .param("parent", parentId)
        .param("title", request.title())
        .param("assetType", request.assetType())
        .param("externalId", request.externalId())
        .update();

    database.sql("""
        insert into resource_action(resource_id, action_id)
        select :resource, id from action where action_key in ('view', 'update', 'admin')
        on conflict do nothing
        """)
        .param("resource", resourceId)
        .update();

    database.sql("""
        insert into superset_asset(
          id, resource_id, external_id, asset_type, title, url_path,
          owner_external_id, published, tags, synchronized_at
        ) values (
          :id, :resource, :externalId, :assetType, :title, :urlPath,
          :owner, :published, cast(:tags as jsonb), now()
        )
        """)
        .param("id", assetId)
        .param("resource", resourceId)
        .param("externalId", request.externalId())
        .param("assetType", request.assetType())
        .param("title", request.title())
        .param("urlPath", request.urlPath())
        .param("owner", request.ownerExternalId())
        .param("published", request.published())
        .param("tags", "[]")
        .update();

    database.sql("""
        insert into outbox_event(aggregate_type,aggregate_id,event_type,payload,idempotency_key)
        values('resource',:child,'RESOURCE_PARENT_WRITE',
          jsonb_build_object('user','external_resource:superset-public','relation','parent',
            'object',:object), 'SUPERSET_PARENT_WRITE:'||:child)
        """).param("child", resourceId)
        .param("object", "external_resource:superset-public/"
            + request.assetType().toLowerCase() + "/" + request.externalId()).update();

    auditTrail.success("SUPERSET","superset.resource.register",null,null,"superset_asset",
        assetId.toString(),request.title(),"REGISTER",null,Map.of("externalId",request.externalId(),"assetType",request.assetType(),"published",request.published()));

    return Map.of("id", assetId, "resourceId", resourceId, "resourceKey", resourceKey,"existing",false);
  }

  public record AssetWrite(
      @NotBlank String externalId,
      @NotBlank String assetType,
      @NotBlank String title,
      @NotBlank String urlPath,
      String ownerExternalId,
      boolean published) {}
}

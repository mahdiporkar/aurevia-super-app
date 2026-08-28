package com.aurevia.authz.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import java.util.Map;
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

@RestController
@RequestMapping("/internal/v1/registry")
public class SupersetAssetController {
  private final JdbcClient database;

  public SupersetAssetController(JdbcClient database) {
    this.database = database;
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
    return database.sql("""
        select distinct sa.id, sa.external_id, sa.asset_type, sa.title, sa.url_path,
               sa.owner_external_id, sa.tags::text tags_json, r.resource_key
        from app_user u
        join authorization_grant g
          on g.subject_type = 'USER' and g.subject_id = u.id and g.status = 'ACTIVE'
        join resource r on r.id = g.resource_id
        join action a on a.id = g.action_id and a.action_key in ('view', 'update', 'admin')
        join superset_asset sa on sa.resource_id = r.id and sa.published = true
        where u.external_id = :subject
          and (g.expires_at is null or g.expires_at > now())
        order by sa.title
        """).param("subject", subject).query().listOfRows();
  }

  @GetMapping("/superset-assets/{assetId}/grants")
  public List<Map<String, Object>> assetGrants(@PathVariable("assetId") UUID assetId) {
    return database.sql("""
        select g.id, g.subject_id as user_id, u.username, u.display_name,
               a.action_key, g.relation, g.expires_at
        from superset_asset sa
        join authorization_grant g
          on g.resource_id = sa.resource_id
         and g.subject_type = 'USER'
         and g.status = 'ACTIVE'
        join app_user u on u.id = g.subject_id
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

    return Map.of("id", assetId, "resourceId", resourceId, "resourceKey", resourceKey);
  }

  public record AssetWrite(
      @NotBlank String externalId,
      @NotBlank String assetType,
      @NotBlank String title,
      @NotBlank String urlPath,
      String ownerExternalId,
      boolean published) {}
}

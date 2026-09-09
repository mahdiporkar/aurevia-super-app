package com.aurevia.authz.authorization;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcAuthorizationQueryRepository implements AuthorizationQueryRepository {
  private final JdbcClient database;
  private final DemoDataPolicy demoData;
  JdbcAuthorizationQueryRepository(JdbcClient database,DemoDataPolicy demoData) {
    this.database=database;this.demoData=demoData;
  }

  @Override public List<PanelRecord> activePanels() {
    return database.sql("""
        select p.id,p.code,p.slug,p.name_fa as "nameFa",p.name_en as "nameEn",
          p.route_base_path as "routeBasePath",p.description,p.icon,p.service_slug as "serviceSlug",
          p.default_route_id as "defaultRouteId",p.sort_order as "sortOrder",
          a.artifact_version as "artifactVersion",a.remote_entry_url as "remoteEntryUrl",
          a.remote_name as "artifactRemoteName",a.exposed_module as "artifactExposedModule",
          a.contract_version as "artifactContractVersion",a.integrity as "artifactIntegrity",
          p.classification,p.resource_definition_mode as "resourceDefinitionMode",
          a.manifest_snapshot::text as "manifestJson"
        from panel p join ui_module_artifact a on a.id=p.active_artifact_id
          and a.validation_status='VALID'
        where p.active and (:demoEnabled or p.classification='REAL')
        order by p.sort_order,p.code
        """).param("demoEnabled",demoData.enabled()).query(PanelRecord.class).list();
  }

  @Override public List<PermissionCandidate> permissionCandidates() {
    return database.sql("""
        select r.resource_key as "resourceKey",r.type::text as "resourceType",
          a.action_key as "actionKey"
        from resource r join resource_action ra on ra.resource_id=r.id
        join action a on a.id=ra.action_id
        left join panel p on p.id=r.panel_id
        where r.status='ACTIVE' and r.visibility_enabled
          and (:demoEnabled or p.id is null or p.classification='REAL')
        order by r.resource_key,a.action_key
        """).param("demoEnabled",demoData.enabled()).query(PermissionCandidate.class).list();
  }

  @Override public List<ResourceRecord> activeResources() {
    return database.sql("""
        select r.id,r.parent_id as "parentId",r.resource_key as "resourceKey",r.type::text,
          r.name_fa as "nameFa",r.name_en as "nameEn",r.owner_domain as "ownerDomain",
          r.classification
        from resource r left join panel p on p.id=r.panel_id
        where r.status='ACTIVE' and r.visibility_enabled
          and (:demoEnabled or p.id is null or p.classification='REAL')
        order by r.resource_key
        """).param("demoEnabled",demoData.enabled()).query(ResourceRecord.class).list();
  }

  @Override public List<MenuOverride> menuOverrides(UUID panelId) {
    return database.sql("""
        select menu_id as "menuId",title,icon,sort_order as "sortOrder",hidden,
          source,node_type as "nodeType",parent_key as "parentKey",page_key as "pageKey",
          external_url as "externalUrl"
        from ui_menu_override where panel_id=:panel and status='ACTIVE'
        """).param("panel",panelId).query(MenuOverride.class).list();
  }

  @Override public Optional<Boolean> runtimeResourceActionEnabled(String canonicalObject,
      String actionKey) {
    return database.sql("""
        select (r.status='ACTIVE' and (:demoEnabled or p.id is null
          or p.classification='REAL') and exists(
            select 1 from resource_action ra join action a on a.id=ra.action_id
            where ra.resource_id=r.id and a.action_key=:action)) as enabled
        from resource r left join panel p on p.id=r.panel_id
        where r.resource_key=:object or case
          when r.type='APPLICATION' then 'application:' || replace(
            regexp_replace(r.resource_key,'^application:',''),':','/')
          when r.type='EXTERNAL_RESOURCE' then 'external_resource:' || replace(
            regexp_replace(r.resource_key,'^(external:|external_resource:)',''),':','/')
          else 'resource:' || replace(r.resource_key,':','/')
        end=:object
        order by (r.resource_key=:object) desc
        limit 1
        """).param("demoEnabled",demoData.enabled()).param("object",canonicalObject)
        .param("action",actionKey)
        .query(Boolean.class).optional();
  }
}

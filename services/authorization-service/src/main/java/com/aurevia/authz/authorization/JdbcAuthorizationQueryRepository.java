package com.aurevia.authz.authorization;

import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcAuthorizationQueryRepository implements AuthorizationQueryRepository {
  private final JdbcClient database;
  JdbcAuthorizationQueryRepository(JdbcClient database) { this.database=database; }

  @Override public List<PanelRecord> activePanels() {
    return database.sql("""
        select p.id,p.code,p.slug,p.name_fa as "nameFa",p.name_en as "nameEn",
          p.route_base_path as "routeBasePath",p.description,p.service_slug as "serviceSlug",
          p.default_route_id as "defaultRouteId",p.sort_order as "sortOrder",
          a.artifact_version as "artifactVersion",a.remote_entry_url as "remoteEntryUrl",
          a.remote_name as "artifactRemoteName",a.exposed_module as "artifactExposedModule",
          a.contract_version as "artifactContractVersion",a.integrity as "artifactIntegrity",
          a.manifest_snapshot::text as "manifestJson"
        from panel p join ui_module_artifact a on a.id=p.active_artifact_id
          and a.validation_status='VALID'
        where p.active order by p.sort_order,p.code
        """).query(PanelRecord.class).list();
  }

  @Override public List<PermissionCandidate> permissionCandidates() {
    return database.sql("""
        select r.resource_key as "resourceKey",r.type::text as "resourceType",
          a.action_key as "actionKey"
        from resource r join resource_action ra on ra.resource_id=r.id
        join action a on a.id=ra.action_id where r.status='ACTIVE'
        order by r.resource_key,a.action_key
        """).query(PermissionCandidate.class).list();
  }

  @Override public List<ResourceRecord> activeResources() {
    return database.sql("""
        select id,parent_id as "parentId",resource_key as "resourceKey",type::text,
          name_fa as "nameFa",name_en as "nameEn",owner_domain as "ownerDomain",classification
        from resource where status='ACTIVE' order by resource_key
        """).query(ResourceRecord.class).list();
  }

  @Override public List<MenuOverride> menuOverrides(UUID panelId) {
    return database.sql("""
        select menu_id as "menuId",title,icon,sort_order as "sortOrder",hidden
        from ui_menu_override where panel_id=:panel
        """).param("panel",panelId).query(MenuOverride.class).list();
  }
}

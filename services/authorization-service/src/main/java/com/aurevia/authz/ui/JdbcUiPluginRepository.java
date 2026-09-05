package com.aurevia.authz.ui;

import com.aurevia.authz.api.dto.UiPluginDtos.ArtifactView;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcUiPluginRepository implements UiPluginRepository {
  private final JdbcClient database;

  JdbcUiPluginRepository(JdbcClient database) { this.database=database; }

  @Override public List<ArtifactView> artifacts(UUID panelId) {
    return database.sql("""
        select id,panel_id as "panelId",artifact_version as "artifactVersion",
          remote_entry_url as "remoteEntryUrl",remote_name as "remoteName",
          exposed_module as "exposedModule",contract_version as "contractVersion",
          schema_version as "schemaVersion",integrity,manifest_snapshot::text as "manifestSnapshot",
          validation_status as "validationStatus",validation_error as "validationError",
          immutable,created_at as "createdAt",created_by as "createdBy",
          (id=(select active_artifact_id from panel where id=:panel)) active,
          (select version from panel where id=:panel) as "panelVersion"
        from ui_module_artifact where panel_id=:panel order by created_at desc
        """).param("panel",panelId).query(ArtifactView.class).list();
  }

  @Override public Optional<String> activePanelSlug(UUID panelId) {
    return database.sql("select slug from panel where id=:id and active")
        .param("id",panelId).query(String.class).optional();
  }

  @Override public boolean remoteNameBelongsToOtherPanel(String remoteName,UUID panelId) {
    Long count=database.sql("""
        select count(*) from ui_module_artifact
        where remote_name=:name and panel_id<>:panel
        """).param("name",remoteName).param("panel",panelId).query(Long.class).single();
    return count>0;
  }

  @Override public boolean resourceActionExists(String resourceKey,String actionKey) {
    Long count=database.sql("""
        select count(*) from resource r
        join resource_action ra on ra.resource_id=r.id
        join action a on a.id=ra.action_id
        where r.resource_key=:resource and a.action_key=:action and r.status='ACTIVE'
        """).param("resource",resourceKey).param("action",actionKey)
        .query(Long.class).single();
    return count>0;
  }

  @Override public void insertArtifact(ArtifactInsert value) {
    database.sql("""
        insert into ui_module_artifact(id,panel_id,artifact_version,remote_entry_url,
          remote_name,exposed_module,contract_version,schema_version,integrity,
          manifest_snapshot,validation_status,created_by)
        values(:id,:panel,:version,:url,:name,:module,:contract,:schema,:integrity,
          cast(:manifest as jsonb),'VALID',:actor)
        """).param("id",value.id()).param("panel",value.panelId())
        .param("version",value.artifactVersion()).param("url",value.remoteEntryUrl())
        .param("name",value.remoteName()).param("module",value.exposedModule())
        .param("contract",value.contractVersion()).param("schema",value.schemaVersion())
        .param("integrity",value.integrity()).param("manifest",value.manifest())
        .param("actor",value.actor()).update();
  }

  @Override public Optional<ArtifactTarget> validArtifact(UUID panelId,UUID artifactId) {
    return database.sql("""
        select remote_entry_url as "remoteEntryUrl",integrity
        from ui_module_artifact
        where id=:id and panel_id=:panel and validation_status='VALID'
        """).param("id",artifactId).param("panel",panelId)
        .query(ArtifactTarget.class).optional();
  }

  @Override public PanelState panelState(UUID panelId) {
    return database.sql("""
        select active_artifact_id as "activeArtifactId",version
        from panel where id=:id
        """).param("id",panelId).query(PanelState.class).single();
  }

  @Override public boolean activate(UUID panelId,UUID artifactId,long expectedVersion) {
    return database.sql("""
        update panel set active_artifact_id=:artifact,version=version+1,updated_at=now()
        where id=:panel and version=:version
        """).param("artifact",artifactId).param("panel",panelId)
        .param("version",expectedVersion).update()==1;
  }

  @Override public String activeManifest(UUID panelId) {
    return database.sql("""
        select manifest_snapshot::text from ui_module_artifact
        where id=(select active_artifact_id from panel where id=:panel)
        """).param("panel",panelId).query(String.class).single();
  }

  @Override public void upsertMenu(UUID panelId,String menuId,String title,String icon,
      Integer order,boolean hidden,String actor) {
    database.sql("""
        insert into ui_menu_override(panel_id,menu_id,title,icon,sort_order,hidden,updated_by)
        values(:panel,:menu,:title,:icon,:sort,:hidden,:actor)
        on conflict(panel_id,menu_id) do update set title=excluded.title,icon=excluded.icon,
          sort_order=excluded.sort_order,hidden=excluded.hidden,
          version=ui_menu_override.version+1,updated_at=now(),updated_by=excluded.updated_by
        """).param("panel",panelId).param("menu",menuId).param("title",title)
        .param("icon",icon).param("sort",order).param("hidden",hidden)
        .param("actor",actor).update();
  }

  @Override public List<ArtifactTarget> activeArtifactTargets() {
    return database.sql("""
        select a.remote_entry_url as "remoteEntryUrl",a.integrity
        from panel p join ui_module_artifact a on a.id=p.active_artifact_id
        where p.active
        """).query(ArtifactTarget.class).list();
  }
}

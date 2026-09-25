package com.aurevia.authz.registry;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Serializes both lifecycles on the panel row; associations are immutable once assigned. */
@Repository
public class JdbcManifestReleaseRepository {
  private final JdbcClient db;
  public JdbcManifestReleaseRepository(JdbcClient db) { this.db=db; }

  public State lock(UUID panel) {
    return db.sql("""
        select active_artifact_id as "artifactId",active_resource_manifest_id as "revisionId",version
        from panel where id=:panel for update
        """).param("panel",panel).query(State.class).single();
  }

  public Optional<UUID> activeRevision(UUID panel) {
    return db.sql("select active_resource_manifest_id from panel where id=:panel")
        .param("panel",panel).query(UUID.class).optional();
  }

  public boolean hasHistory(UUID panel) {
    return db.sql("select count(*) from resource_manifest_import where panel_id=:panel")
        .param("panel",panel).query(Long.class).single()>0;
  }

  public Artifact artifact(UUID panel,UUID artifact) {
    return db.sql("""
        select id,resource_manifest_id as "revisionId",manifest_snapshot::text as manifest,
          remote_entry_url as "remoteEntryUrl",integrity
        from ui_module_artifact where panel_id=:panel and id=:id and validation_status='VALID'
        """).param("panel",panel).param("id",artifact).query(Artifact.class).optional()
        .orElseThrow(()->new IllegalArgumentException("valid artifact not found for panel"));
  }

  public List<UUID> artifactsForRevision(UUID panel,UUID revision) {
    return db.sql("""
        select id from ui_module_artifact where panel_id=:panel and resource_manifest_id=:revision
          and validation_status='VALID' order by id
        """).param("panel",panel).param("revision",revision).query(UUID.class).list();
  }

  public void bind(UUID panel,UUID artifact,UUID revision) {
    if(revision==null)return;
    if(db.sql("""
        update ui_module_artifact set resource_manifest_id=:revision
        where panel_id=:panel and id=:artifact
          and (resource_manifest_id is null or resource_manifest_id=:revision)
        """).param("panel",panel).param("artifact",artifact).param("revision",revision).update()!=1)
      throw new IllegalArgumentException("artifact already belongs to another resource revision");
  }

  public void setActiveRevision(UUID panel,UUID revision) {
    db.sql("update panel set active_resource_manifest_id=:revision where id=:panel")
        .param("panel",panel).param("revision",revision).update();
  }

  public void activateArtifact(UUID panel,UUID artifact) {
    db.sql("""
        update panel set active_artifact_id=:artifact,
          semantic_version=coalesce((select artifact_version from ui_module_artifact where id=:artifact),semantic_version),
          version=version+1,updated_at=now() where id=:panel
        """).param("panel",panel).param("artifact",artifact,java.sql.Types.OTHER).update();
  }

  public record State(UUID artifactId,UUID revisionId,long version) {}
  public record Artifact(UUID id,UUID revisionId,String manifest,String remoteEntryUrl,String integrity) {}
}

package com.aurevia.authz.sync;

import com.aurevia.authz.openfga.RelationshipAuthorizationPort;
import com.aurevia.authz.registry.JdbcManifestCatalogProjection;
import com.aurevia.authz.registry.JdbcManifestReleaseRepository;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Old queued writes must not resurrect a grant/parent removed by a newer activation. */
@Component
public class JdbcCatalogTupleProjector {
  private final JdbcClient db;
  private final JdbcManifestReleaseRepository releases;
  private final JdbcManifestCatalogProjection catalog;
  private final RelationshipAuthorizationPort relationships;
  public JdbcCatalogTupleProjector(JdbcClient db,JdbcManifestReleaseRepository releases,
      JdbcManifestCatalogProjection catalog,RelationshipAuthorizationPort relationships) {
    this.db=db;this.releases=releases;this.catalog=catalog;this.relationships=relationships;
  }

  @Transactional
  public boolean project(OutboxRepository.Event event) {
    if(!event.eventType().startsWith("GRANT_")&&!event.eventType().startsWith("RESOURCE_PARENT_")
        &&!event.eventType().startsWith("APPLICATION_GROUP_GRANT_"))return false;
    var panel=db.sql("""
        select panel_id from resource where source='MANIFEST' and panel_id is not null
          and case type when 'APPLICATION' then 'application:'||regexp_replace(resource_key,'^application:','')
            when 'EXTERNAL_RESOURCE' then 'external_resource:'||replace(regexp_replace(resource_key,'^external_resource:',''),':','/')
            else 'resource:'||replace(resource_key,':','/') end=:object
        """).param("object",event.object()).query(UUID.class).optional();
    if(panel.isEmpty())return false;
    // This lock also serializes against materialization and other outbox workers. The
    // external write occurs while holding it, so a delayed event cannot overtake rollback.
    releases.lock(panel.orElseThrow());
    boolean expected=catalog.snapshot(panel.orElseThrow()).contains(
        new JdbcManifestCatalogProjection.Tuple(event.user(),event.relation(),event.object()));
    if(expected)relationships.write(event.user(),event.relation(),event.object());
    else relationships.delete(event.user(),event.relation(),event.object());
    return true;
  }
}

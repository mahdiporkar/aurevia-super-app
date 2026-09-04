package com.aurevia.authz.ui;

import com.aurevia.authz.api.dto.UiPluginDtos.ArtifactView;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UiPluginRepository {
  List<ArtifactView> artifacts(UUID panelId);
  Optional<String> activePanelSlug(UUID panelId);
  boolean resourceActionExists(String resourceKey,String actionKey);
  void insertArtifact(ArtifactInsert artifact);
  Optional<ArtifactTarget> validArtifact(UUID panelId,UUID artifactId);
  PanelState panelState(UUID panelId);
  boolean activate(UUID panelId,UUID artifactId,long expectedVersion);
  String activeManifest(UUID panelId);
  void upsertMenu(UUID panelId,String menuId,String title,String icon,Integer order,
      boolean hidden,String actor);
  List<ArtifactTarget> activeArtifactTargets();

  record ArtifactInsert(UUID id,UUID panelId,String artifactVersion,String remoteEntryUrl,
      String remoteName,String exposedModule,String contractVersion,String schemaVersion,
      String integrity,String manifest,String actor) {}
  record ArtifactTarget(String remoteEntryUrl,String integrity) {}
  record PanelState(UUID activeArtifactId,long version) {}
}

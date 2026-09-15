package com.aurevia.authz.ui;

import com.aurevia.authz.api.dto.UiPluginDtos.ArtifactView;
import com.aurevia.authz.api.dto.UiPluginDtos.NavigationOverrideView;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UiPluginRepository {
  List<ArtifactView> artifacts(UUID panelId);
  Optional<String> activePanelSlug(UUID panelId);
  boolean remoteNameBelongsToOtherPanel(String remoteName,UUID panelId);
  boolean resourceActionExists(String resourceKey,String actionKey);
  void insertArtifact(ArtifactInsert artifact);
  Optional<ArtifactRevision> artifactByVersion(UUID panelId,String artifactVersion);
  Optional<ArtifactTarget> validArtifact(UUID panelId,UUID artifactId);
  Optional<PanelFrontendSettings> lockFrontendSettings(UUID panelId);
  PanelState panelState(UUID panelId);
  boolean activate(UUID panelId,UUID artifactId,long expectedVersion);
  String activeManifest(UUID panelId);
  Optional<String> activeManifestOptional(UUID panelId);
  void upsertMenu(UUID panelId,String menuId,String title,String icon,Integer order,
      boolean hidden,String source,String nodeType,String parentKey,String pageKey,
      String externalUrl,String actor);
  List<NavigationOverrideView> navigationOverrides(UUID panelId);
  boolean navigationHasChildren(UUID panelId,String key);
  void deleteNavigationOverride(UUID panelId,String key);
  List<ArtifactTarget> activeArtifactTargets();

  record ArtifactInsert(UUID id,UUID panelId,String artifactVersion,String remoteEntryUrl,
      String remoteName,String exposedModule,String contractVersion,String schemaVersion,
      String integrity,String manifest,String checksum,String sourceUrl,String actor) {}
  record ArtifactTarget(String remoteEntryUrl,String integrity,String classification) {}
  record ArtifactRevision(UUID id,String checksum,String manifest,boolean active,
      String remoteEntryUrl,String remoteName,String exposedModule,String contractVersion,
      String integrity) {}
  record PanelFrontendSettings(UUID id,String slug,String mfManifestUrl,String remoteEntryPath,
      String remoteName,String exposedModule,String contractVersion,String integrity,
      UUID activeArtifactId,long version,boolean active) {}
  record PanelState(UUID activeArtifactId,long version) {}
}

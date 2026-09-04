package com.aurevia.authz.authorization;

import java.util.List;
import java.util.UUID;

public interface AuthorizationQueryRepository {
  List<PanelRecord> activePanels();
  List<PermissionCandidate> permissionCandidates();
  List<ResourceRecord> activeResources();
  List<MenuOverride> menuOverrides(UUID panelId);

  record PanelRecord(UUID id,String code,String slug,String nameFa,String nameEn,
      String routeBasePath,String description,String serviceSlug,String defaultRouteId,
      int sortOrder,String artifactVersion,String remoteEntryUrl,String artifactRemoteName,
      String artifactExposedModule,String artifactContractVersion,String artifactIntegrity,
      String manifestJson) {}
  record PermissionCandidate(String resourceKey,String resourceType,String actionKey) {}
  record ResourceRecord(UUID id,UUID parentId,String resourceKey,String type,String nameFa,
      String nameEn,String ownerDomain,String classification) {}
  record MenuOverride(String menuId,String title,String icon,Integer sortOrder,boolean hidden) {}
}

package com.aurevia.authz.registry;

import static com.aurevia.authz.registry.PanelModels.*;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PanelRepository {
  List<PanelView> panels();
  List<AuditView> audit(int limit);
  Optional<String> routePath(UUID id);
  boolean routePathExists(String path,UUID excludingId);
  void create(UUID id,PanelCommand command,String serviceSlug,String remoteName,String defaultRouteId);
  int update(UUID id,long version,PanelCommand command,String serviceSlug,String remoteName,
      String defaultRouteId);
  int archive(UUID id,long version);
  void enqueue(UUID id,String event,String key,long aggregateVersion);
}

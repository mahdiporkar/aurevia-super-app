package com.aurevia.authz.registry;

import static com.aurevia.authz.api.dto.ResourceManifestDtos.ResourceDefinition;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ResourceManifestRepository {
  List<ResourceDefinition> definitionTree(String rootKey);
  String latestVersion(String rootKey);
  boolean importExists(String applicationKey,String manifestVersion,String checksum);
  boolean resourceExists(String resourceKey);
  Optional<String> resourceType(String resourceKey);
  Optional<UUID> resourceId(String resourceKey);
  void upsertResource(ResourceDefinition resource,UUID parentId,String metadataJson);
  void upsertExternalBinding(ResourceDefinition resource,String metadataJson);
  void clearActions(UUID resourceId);
  boolean addAction(UUID resourceId,String actionKey);
  int deprecateMissing(String rootKey,String[] retainedKeys);
  void insertImport(String applicationKey,String manifestVersion,String checksum,
      String actor,String payloadJson);
}

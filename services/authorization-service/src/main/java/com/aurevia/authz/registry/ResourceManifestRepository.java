package com.aurevia.authz.registry;

import static com.aurevia.authz.api.dto.ResourceManifestDtos.ResourceDefinition;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ResourceManifestRepository {
  List<ResourceDefinition> definitionTree(String rootKey);
  String latestVersion(String rootKey);
  Optional<PanelManifestSettings> panelSettings(UUID panelId);
  Optional<PanelManifestSettings> panelSettingsBySlug(String slug);
  List<DraftRecord> drafts(UUID panelId);
  Optional<DraftRecord> draft(UUID panelId,UUID draftId);
  Optional<DraftRecord> revisionByVersion(UUID panelId,String manifestVersion);
  boolean insertDraft(DraftInsert draft);
  boolean markPublished(UUID draftId,String actor);
  boolean resourceExists(String resourceKey);
  Optional<ResourceOwnership> resourceOwnership(String resourceKey);
  boolean actionExists(String actionKey);
  boolean resourceActionExists(String resourceKey,String actionKey);
  Optional<UUID> resourceId(String resourceKey);
  void upsertResource(ResourceDefinition resource,UUID parentId,String metadataJson,
      UUID panelId,String manifestVersion);
  void upsertExternalBinding(ResourceDefinition resource,String metadataJson);
  void clearActions(UUID resourceId);
  boolean addAction(UUID resourceId,String actionKey);
  void enqueueParent(UUID childId,UUID parentId,String eventType);
  int deprecateMissing(UUID panelId,String rootKey,String[] retainedKeys);

  record PanelManifestSettings(UUID id,String slug,String nameFa,String nameEn,
      String resourceDefinitionMode,String resourceManifestUrl) {}
  record ResourceOwnership(String type,String source,UUID panelId,UUID parentId,String nameFa,
      String nameEn,String status) {}
  record DraftInsert(UUID id,UUID panelId,String applicationKey,String manifestVersion,
      String schemaVersion,String checksum,String actor,String sourceUrl,String payload,
      String diffSummary) {}
  record DraftRecord(UUID id,UUID panelId,String applicationKey,String manifestVersion,
      String schemaVersion,String checksum,String importedBy,String sourceUrl,String payload,
      String workflowStatus,String diffSummary,java.time.Instant createdAt,
      java.time.Instant publishedAt,String publishedBy) {}
}

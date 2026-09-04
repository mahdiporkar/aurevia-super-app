package com.aurevia.authz.registry;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public final class PanelModels {
  private PanelModels() {}
  @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
  public record PanelView(UUID id,String code,String nameFa,String nameEn,String description,
      String slug,String serviceSlug,String remoteName,String defaultRouteId,String remoteEntryPath,
      String exposedModule,String routeBasePath,String semanticVersion,String contractVersion,
      String integrity,boolean active,int sortOrder,UUID activeArtifactId,long version) {}
  @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
  public record AuditView(UUID id,String actorKey,String eventType,String targetType,String targetKey,
      String correlationId,Map<String,Object> safeDetails,Instant occurredAt) {}
  public record PanelCommand(String code,String nameFa,String nameEn,String description,String slug,
      String serviceSlug,String remoteName,String defaultRouteId,String remoteEntry,
      String exposedModule,String routeBasePath,String semanticVersion,String contractVersion,
      String integrity,boolean active,int sortOrder) {}
  public record MutationResult(UUID id,long version) {}
}

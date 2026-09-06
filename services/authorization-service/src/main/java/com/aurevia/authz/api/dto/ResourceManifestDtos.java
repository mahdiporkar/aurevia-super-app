package com.aurevia.authz.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class ResourceManifestDtos {
  private ResourceManifestDtos() {}

  public record DefinitionManifest(@NotBlank String application,
      @NotBlank String manifestVersion,
      @NotEmpty List<@Valid ResourceDefinition> resources) {}

  public record ResourceDefinition(@NotBlank String key,@NotBlank String type,String parent,
      @NotBlank String nameFa,@NotBlank String nameEn,String ownerDomain,String classification,
      List<String> actions,String status,String source,Map<String,Object> metadata,
      String provider,String externalType,String externalId) {
    public ResourceDefinition {
      actions=actions==null?List.of():List.copyOf(actions);
      metadata=metadata==null?Map.of():Map.copyOf(metadata);
    }
  }

  public record SyncResult(int created,int updated,int deprecated,boolean idempotent,
      String checksum) {}

  /** Preferred versioned resource-manifest.json contract published by a Micro Frontend. */
  public record MicroFrontendManifest(@NotBlank String schemaVersion,
      @NotNull @Valid ModuleMetadata module,List<@Valid ManifestRoute> routes,
      List<@Valid ManifestResource> resources,List<@Valid NavigationNode> navigation) {
    public MicroFrontendManifest {
      routes=routes==null?List.of():List.copyOf(routes);
      resources=resources==null?List.of():List.copyOf(resources);
      navigation=navigation==null?List.of():List.copyOf(navigation);
    }
  }

  public record ModuleMetadata(@NotBlank String key,@NotBlank String name,
      String nameFa,String nameEn,@NotBlank String version) {}

  public record ManifestRoute(String key,String id,String path,String component,
      String resourceKey,String resource,String action,String title) {
    public String effectiveKey() { return first(key,id); }
    public String effectiveResourceKey() { return first(resourceKey,resource); }
    public String effectiveAction() { return first(action,"view"); }
  }

  public record ManifestResource(@NotBlank String key,@NotBlank String type,String parentKey,
      String parent,String name,String nameFa,String nameEn,String ownerDomain,
      String classification,List<String> actions,Map<String,Object> metadata,
      String provider,String externalType,String externalId) {
    public ManifestResource {
      actions=actions==null?List.of():List.copyOf(actions);
      metadata=metadata==null?Map.of():Map.copyOf(metadata);
    }
    public String effectiveParentKey() { return first(parentKey,parent); }
    public String effectiveNameFa() { return first(nameFa,name,key); }
    public String effectiveNameEn() { return first(nameEn,name,key); }
  }

  public record NavigationNode(String key,String id,@NotBlank String type,String parentKey,
      String parentId,String pageKey,String routeId,@NotBlank String title,String icon,
      Integer order,String externalUrl) {
    public String effectiveKey() { return first(key,id); }
    public String effectiveParentKey() { return first(parentKey,parentId); }
    public String effectivePageKey() { return first(pageKey,routeId); }
  }

  public record ManifestChange(String resourceKey,String changeType,String beforeType,
      String afterType,String description) {}

  public record ManifestDraftView(UUID id,UUID panelId,String moduleKey,String manifestVersion,
      String schemaVersion,String checksum,String workflowStatus,String sourceUrl,
      List<ManifestChange> changes,Instant createdAt,String createdBy,Instant publishedAt,
      String publishedBy) {}

  public record PublishResult(UUID draftId,String workflowStatus,int created,int updated,
      int deprecated,String checksum) {}

  private static String first(String... values) {
    for(String value:values)if(value!=null&&!value.isBlank())return value;
    return null;
  }
}

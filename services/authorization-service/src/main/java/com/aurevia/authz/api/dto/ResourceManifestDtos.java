package com.aurevia.authz.api.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
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

  /** Authorization-only resource-manifest.json contract published by a Micro Frontend. */
  @JsonIgnoreProperties(ignoreUnknown=false)
  public record ResourceManifest(@NotBlank String schemaVersion,
      @NotNull @Valid ModuleMetadata module,List<@Valid ManifestResource> resources) {
    public ResourceManifest {
      resources=resources==null?List.of():List.copyOf(resources);
    }
  }

  public record ModuleMetadata(@NotBlank String key,@NotBlank String name,
      String nameFa,String nameEn,@NotBlank String version) {}

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

package com.aurevia.authz.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;
import java.util.Map;

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
}

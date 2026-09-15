package com.aurevia.authz.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class OuAccessDtos {
  private OuAccessDtos() {}
  public record GroupRequest(@NotBlank String code,@NotBlank String name,String description,
      @NotBlank String ruleCombiner,boolean active) {}
  public record RuleRequest(@NotNull UUID ouId,@NotBlank String matchMode) {}
  public record GrantRequest(@NotNull UUID applicationId,@NotNull UUID accessGroupId) {}
  public record VersionResponse(UUID id,long version) {}
  public record PendingGrantResponse(UUID id,String status,long version) {}
  public record PreviewResponse(List<Map<String,Object>> members,int memberCount,long wouldAdd,
      long wouldRemove,String combiner) {}
  public record ExplanationResponse(Map<String,Object> user,Map<String,Object> ou,
      List<Map<String,Object>> membershipPaths,List<Map<String,Object>> applications) {}
}

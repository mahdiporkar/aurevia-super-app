package com.aurevia.authz.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class AuthorizationDtos {
  private AuthorizationDtos() {}

  public record CheckRequest(@NotBlank String subjectId,@NotBlank String issuer,
      @NotBlank String resource,@NotBlank String action,Map<String,Object> context,
      @NotBlank String correlationId) {}
  public record Decision(String result,String reasonCode,String modelVersion,String decisionId,
      Map<String,Object> obligations) {}
  public record SubjectView(String type,String issuer,String id) {}
  public record PanelSummary(UUID id,String code,String slug,String nameFa,String nameEn,
      String remoteEntry,String exposedModule,String routeBasePath,String semanticVersion,
      String contractVersion,String integrity) {}
  public record ResourceNode(UUID id,@JsonProperty("parent_id") UUID parentId,
      @JsonProperty("resource_key") String resourceKey,String type,
      @JsonProperty("name_fa") String nameFa,@JsonProperty("name_en") String nameEn,
      @JsonProperty("owner_domain") String ownerDomain,String classification,
      List<String> actions) {}
  public record UiCatalog(String catalogVersion,Instant generatedAt,String contractVersion,
      List<Map<String,Object>> modules) {}
  public record Manifest(String manifestType,SubjectView subject,String version,Instant expiresAt,
      List<PanelSummary> panels,Map<String,List<String>> permissions,
      List<ResourceNode> resourceTree,UiCatalog uiCatalog) {}
}

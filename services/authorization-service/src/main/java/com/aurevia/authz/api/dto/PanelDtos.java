package com.aurevia.authz.api.dto;
import com.aurevia.authz.registry.PanelModels.PanelCommand;
import jakarta.validation.constraints.NotBlank;
public final class PanelDtos {private PanelDtos(){}
 public record PanelRequest(@NotBlank String code,@NotBlank String nameFa,@NotBlank String nameEn,
   String description,@NotBlank String slug,String serviceSlug,String remoteName,String defaultRouteId,
   @NotBlank String remoteEntry,@NotBlank String exposedModule,@NotBlank String routeBasePath,
   @NotBlank String semanticVersion,@NotBlank String contractVersion,String integrity,boolean active,int sortOrder){
   public PanelCommand toCommand(){return new PanelCommand(code,nameFa,nameEn,description,slug,serviceSlug,
     remoteName,defaultRouteId,remoteEntry,exposedModule,routeBasePath,semanticVersion,contractVersion,
     integrity,active,sortOrder);}}
}

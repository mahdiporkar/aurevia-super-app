package com.aurevia.authz.api.dto;

import com.aurevia.authz.identityprovider.IdentityProviderModels.ProviderCommand;
import jakarta.validation.constraints.NotBlank;
import java.util.List;

public final class IdentityProviderDtos {
  private IdentityProviderDtos() {}
  public record ProviderRequest(@NotBlank String code,@NotBlank String name,@NotBlank String type,
      @NotBlank String issuerUrl,@NotBlank String authorizationEndpoint,@NotBlank String tokenEndpoint,
      @NotBlank String jwksUri,String userInfoEndpoint,@NotBlank String clientId,
      @NotBlank String clientSecretReference,boolean enabled,String tenantId,List<String> domains,
      List<String> scopes,List<String> audiences,String subjectClaim,String usernameClaim,
      String groupsClaim){
    public ProviderCommand toCommand(){return new ProviderCommand(code,name,type,issuerUrl,
        authorizationEndpoint,tokenEndpoint,jwksUri,userInfoEndpoint,clientId,clientSecretReference,
        enabled,tenantId,domains,scopes,audiences,subjectClaim,usernameClaim,groupsClaim);}
  }
  public record EnabledRequest(boolean enabled) {}
}

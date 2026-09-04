package com.aurevia.authz.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class IdentitySyncDtos {
  private IdentitySyncDtos() {}

  public record LoginIdentityRequest(@NotBlank String issuer,@NotBlank String subject,
      @NotBlank String username,String displayName,String email,
      List<@Valid DirectoryGroupRequest> groups,String distinguishedName,String ouExternalId,
      String directoryExternalId,Map<String,String> attributes) {
    public LoginIdentityRequest {
      groups=groups==null?List.of():List.copyOf(groups);
      attributes=attributes==null?Map.of():Map.copyOf(attributes);
    }
  }
  public record DirectoryGroupRequest(@NotBlank String externalId,@NotBlank String path,
      @NotBlank String displayName) {}
  public record LoginIdentityResponse(UUID userId,int groups,UUID ouId,int effectiveGroups) {}
}

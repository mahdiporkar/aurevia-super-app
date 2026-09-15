package com.aurevia.authz.identityprovider;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class IdentityProviderModels {
  private IdentityProviderModels() {}

  @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
  public record ProviderView(UUID id,String code,String name,String type,String issuerUrl,
      String authorizationEndpoint,String tokenEndpoint,String jwksUri,String userInfoEndpoint,
      String clientId,String clientSecretReference,boolean enabled,String tenantId,
      List<String> domains,List<String> scopes,List<String> audiences,String subjectClaim,
      String usernameClaim,String groupsClaim,String connectionStatus,Instant lastHealthCheckAt,
      String lastHealthError,long version) {}

  @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
  public record ProviderSummary(String code,String name,String type,String issuerUrl,
      String tenantId,List<String> domains,String connectionStatus) {}

  @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
  public record RuntimeProvider(String code,String name,String issuerUrl,
      String authorizationEndpoint,String tokenEndpoint,String jwksUri,String userInfoEndpoint,
      String clientId,String clientSecretReference,List<String> scopes,List<String> audiences,
      String subjectClaim,String usernameClaim,String groupsClaim) {}

  public record ProviderCommand(String code,String name,String type,String issuerUrl,
      String authorizationEndpoint,String tokenEndpoint,String jwksUri,String userInfoEndpoint,
      String clientId,String clientSecretReference,boolean enabled,String tenantId,
      List<String> domains,List<String> scopes,List<String> audiences,String subjectClaim,
      String usernameClaim,String groupsClaim) {}

  public record ProviderSnapshot(UUID id,String code,String issuerUrl,String clientId,
      String tenantId,boolean enabled,long version) {}
  public record MutationResult(UUID id,long version) {}
  public record HealthResult(String status,Instant checkedAt) {}
}

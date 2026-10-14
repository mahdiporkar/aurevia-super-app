package com.aurevia.authz.api.dto;
import com.aurevia.authz.outbound.OutboundModels.ConnectionCommand;
import com.aurevia.authz.outbound.OutboundModels.ProfileCommand;
import jakarta.validation.constraints.*;
public final class OutboundRegistryDtos {private OutboundRegistryDtos(){}
  public record ConnectionRequest(@NotBlank String connectionRef,@NotBlank String name,
                                  @NotBlank String baseUrl,boolean tlsRequired,boolean active,long version){
    public ConnectionCommand toCommand(){return new ConnectionCommand(connectionRef,name,baseUrl,tlsRequired,active,version);}}
  public record ProfileRequest(
    String code,
    String name,
    String description,
    String authMode,
    String tokenConnectionRef,
    String tokenEndpointPath,
    String requestFormat,
    String credentialSecretRef,
    String scope,
    String audience,
    String tokenResponsePointer,
    String expiresInResponsePointer,
    String tokenTypeResponsePointer,
    String authorizationScheme,
    String credentialTransport,
    int expirySkewSeconds,
    int connectTimeoutMs,
    int responseTimeoutMs,
    long maxTokenResponseSize,
    boolean active
  ) {
    public ProfileCommand toCommand() {
      return new ProfileCommand(
        code,
        name,
        description,
        authMode,
        tokenConnectionRef,
        tokenEndpointPath,
        requestFormat,
        credentialSecretRef,
        scope,
        audience,
        tokenResponsePointer,
        expiresInResponsePointer,
        tokenTypeResponsePointer,
        authorizationScheme,
        credentialTransport,
        expirySkewSeconds,
        connectTimeoutMs,
        responseTimeoutMs,
        maxTokenResponseSize,
        active
      );
    }
  }
  public record StatusRequest(boolean active){}
}

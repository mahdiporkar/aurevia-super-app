package com.aurevia.authz.api.dto;
import com.aurevia.authz.outbound.OutboundModels.ConnectionCommand;
import com.aurevia.authz.outbound.OutboundModels.ProfileCommand;
import jakarta.validation.constraints.*;
public final class OutboundRegistryDtos {private OutboundRegistryDtos(){}
 public record ConnectionRequest(@NotBlank String connectionRef,@NotBlank String name,
   @NotBlank String baseUrl,boolean tlsRequired,boolean active,long version){
   public ConnectionCommand toCommand(){return new ConnectionCommand(connectionRef,name,baseUrl,tlsRequired,active,version);}}
 public record ProfileRequest(@NotBlank String code,@NotBlank String name,String description,
   @NotBlank String authMode,String tokenConnectionRef,String tokenEndpointPath,@NotBlank String requestFormat,
   String credentialSecretRef,String scope,String audience,@NotBlank String tokenResponsePointer,
   @NotBlank String expiresInResponsePointer,@NotBlank String tokenTypeResponsePointer,
   @NotBlank String authorizationScheme,@NotBlank String credentialTransport,
   @Min(5) @Max(600) int expirySkewSeconds,@Min(100) @Max(30000) int connectTimeoutMs,
   @Min(100) @Max(120000) int responseTimeoutMs,@Min(1024) @Max(5242880) long maxTokenResponseSize,
   boolean active){public ProfileCommand toCommand(){return new ProfileCommand(code,name,description,authMode,
     tokenConnectionRef,tokenEndpointPath,requestFormat,credentialSecretRef,scope,audience,tokenResponsePointer,
     expiresInResponsePointer,tokenTypeResponsePointer,authorizationScheme,credentialTransport,expirySkewSeconds,
     connectTimeoutMs,responseTimeoutMs,maxTokenResponseSize,active);}}
 public record StatusRequest(boolean active){}
}

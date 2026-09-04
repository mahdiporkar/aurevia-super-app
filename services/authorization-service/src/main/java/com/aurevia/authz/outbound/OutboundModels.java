package com.aurevia.authz.outbound;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.time.Instant;
import java.util.UUID;
public final class OutboundModels {private OutboundModels(){}
 @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
 public record ConnectionView(UUID id,String connectionRef,String name,String kind,String baseUrl,
   boolean tlsRequired,boolean active,long version,Instant updatedAt){}
 @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
 public record RuntimeConnectionView(String connectionRef,String name,String kind,String baseUrl,
   boolean tlsRequired,long version){}
 public record ConnectionCommand(String connectionRef,String name,String baseUrl,boolean tlsRequired,
   boolean active,long version){}
 @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
 public record ProfileView(UUID id,String code,String name,String description,String authMode,
   String tokenConnectionRef,String tokenEndpointPath,String requestFormat,String credentialSecretRef,
   String scope,String audience,String tokenResponsePointer,String expiresInResponsePointer,
   String tokenTypeResponsePointer,String authorizationScheme,String credentialTransport,
   int expirySkewSeconds,int connectTimeoutMs,int responseTimeoutMs,long maxTokenResponseSize,
   boolean active,long version,long usageCount,Instant createdAt,Instant updatedAt){}
 @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
 public record RuntimeProfileView(UUID id,String authMode,String tokenConnectionRef,
   String tokenEndpointPath,String requestFormat,String credentialSecretRef,String scope,
   String audience,String tokenResponsePointer,String expiresInResponsePointer,
   String tokenTypeResponsePointer,String authorizationScheme,String credentialTransport,
   int expirySkewSeconds,int connectTimeoutMs,int responseTimeoutMs,long maxTokenResponseSize,
   long version){}
 @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
 public record UsageView(UUID id,String code,String name,boolean active){}
 public record ProfileCommand(String code,String name,String description,String authMode,
   String tokenConnectionRef,String tokenEndpointPath,String requestFormat,String credentialSecretRef,
   String scope,String audience,String tokenResponsePointer,String expiresInResponsePointer,
   String tokenTypeResponsePointer,String authorizationScheme,String credentialTransport,
   int expirySkewSeconds,int connectTimeoutMs,int responseTimeoutMs,long maxTokenResponseSize,
   boolean active){}
 public record MutationResult(UUID id,long version){}
}

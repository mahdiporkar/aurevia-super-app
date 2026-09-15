package com.aurevia.authz.routing;

import java.util.UUID;

/** Credential-free data-plane routing contract returned to the Java BFF. */
public record ResolvedRoute(UUID routeId,UUID operationId,UUID panelId,String panelSlug,
    String routeKey,String pathPrefix,UUID targetId,String targetKey,int stripPrefix,
    String rewritePattern,String rewriteReplacement,UUID resourceId,String resourceKey,
    String actionKey,boolean authorizationRequired,String dataPolicyKey,long maxBodyBytes,
    int connectTimeoutMs,int responseTimeoutMs,long maxResponseBytes,boolean retryEnabled,
    int maxRetries,String tlsProfileRef,UUID authProfileId,String authMode,
    long authProfileVersion,String credentialTransport) {}

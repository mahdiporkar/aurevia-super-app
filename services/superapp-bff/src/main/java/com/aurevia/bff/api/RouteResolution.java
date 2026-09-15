package com.aurevia.bff.api;

import java.util.UUID;

/** Type-safe copy of the Authorization Service route-resolution contract. */
public record RouteResolution(UUID routeId,UUID operationId,UUID panelId,String panelSlug,
    String routeKey,String pathPrefix,UUID targetId,String targetKey,int stripPrefix,
    String rewritePattern,String rewriteReplacement,UUID resourceId,String resourceKey,
    String actionKey,boolean authorizationRequired,String dataPolicyKey,long maxBodyBytes,
    int connectTimeoutMs,int responseTimeoutMs,long maxResponseBytes,boolean retryEnabled,
    int maxRetries,String tlsProfileRef,UUID authProfileId,String authMode,
    long authProfileVersion,String credentialTransport) {}

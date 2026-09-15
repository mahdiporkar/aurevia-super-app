package com.aurevia.authz.routing;

import java.util.List;
import java.util.UUID;

public interface RouteResolutionRepository {
  List<Candidate> activeCandidates(String httpMethod);

  record Candidate(UUID routeId,String routeKey,String pathPrefix,String normalizedPrefix,
      int stripPrefix,String rewritePattern,String rewriteReplacement,int priority,
      String allowedMethods,boolean retryEnabled,int maxRetries,UUID panelId,String panelSlug,
      UUID targetId,String targetKey,String tlsProfileRef,UUID operationId,String pathPattern,
      UUID resourceId,String resourceKey,String actionKey,boolean authorizationRequired,
      String dataPolicyKey,long maxBodyBytes,int connectTimeoutMs,int responseTimeoutMs,
      long maxResponseBytes,UUID authProfileId,String authMode,long authProfileVersion,
      String credentialTransport) {}
}

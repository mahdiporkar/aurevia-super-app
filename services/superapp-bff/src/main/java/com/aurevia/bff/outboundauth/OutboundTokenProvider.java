package com.aurevia.bff.outboundauth;
import reactor.core.publisher.Mono;
public interface OutboundTokenProvider {
  Mono<OutboundCredential> resolve(ServiceTarget target,AuthenticatedSession session,RequestContext context);
  Mono<Void> invalidate(ServiceTarget target,InvalidationReason reason);
  boolean supports(OutboundAuthMode mode);
  record ServiceTarget(String id,String authProfileId,OutboundAuthMode authMode,long authProfileVersion){}
  record AuthenticatedSession(String subject,String publicIamAccessToken){}
  record RequestContext(String correlationId,String routeId,String operationId){}
  enum InvalidationReason { ADMIN_REQUEST, CREDENTIAL_ROTATION, PROFILE_CHANGED, UPSTREAM_REJECTED }
}

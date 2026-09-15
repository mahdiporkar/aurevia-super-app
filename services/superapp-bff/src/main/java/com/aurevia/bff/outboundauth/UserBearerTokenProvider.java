package com.aurevia.bff.outboundauth;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
@Component public class UserBearerTokenProvider implements OutboundTokenProvider {
 public Mono<OutboundCredential> resolve(ServiceTarget t,AuthenticatedSession s,RequestContext c){return s.publicIamAccessToken()==null?Mono.error(new IllegalStateException("Public IAM credential unavailable")):Mono.just(new OutboundCredential("Bearer",s.publicIamAccessToken(),false));}
 public Mono<Void> invalidate(ServiceTarget t,InvalidationReason r){return Mono.empty();}
 public boolean supports(OutboundAuthMode m){return m==OutboundAuthMode.FORWARD_USER_TOKEN;}
}

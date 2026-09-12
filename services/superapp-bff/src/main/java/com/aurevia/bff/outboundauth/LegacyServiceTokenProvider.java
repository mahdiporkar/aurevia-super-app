package com.aurevia.bff.outboundauth;
import org.springframework.stereotype.Component;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

@Component
public class LegacyServiceTokenProvider implements OutboundTokenProvider {
  private final LegacyTokenManager manager;
  LegacyServiceTokenProvider(LegacyTokenManager manager) { this.manager=manager; }
  public Mono<OutboundCredential> resolve(ServiceTarget target,AuthenticatedSession session,RequestContext context) {
    return manager.resolve(target).onErrorMap(error -> new ResponseStatusException(
        HttpStatus.BAD_GATEWAY,"Legacy authentication unavailable"));
  }
  public Mono<Void> invalidate(ServiceTarget target,InvalidationReason reason) { return manager.invalidate(target); }
  public boolean supports(OutboundAuthMode mode) { return mode==OutboundAuthMode.LEGACY_SERVICE_TOKEN; }
}

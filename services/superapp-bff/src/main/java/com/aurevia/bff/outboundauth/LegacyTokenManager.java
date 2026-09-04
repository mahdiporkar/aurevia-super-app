package com.aurevia.bff.outboundauth;

import com.aurevia.bff.api.AuthorizationServiceClient;
import com.aurevia.bff.observability.DevelopmentTokenEvidenceLogger;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
public final class LegacyTokenManager {
  private final AuthorizationServiceClient authorization;
  private final LegacyTokenCache cache;
  private final SecretResolver secrets;
  private final LegacyTokenClient client;
  private final LegacyTokenResponseParser parser;
  private final LegacyTokenRefreshCoordinator coordinator;
  private final LegacyTokenAuditPublisher audit;
  private final DevelopmentTokenEvidenceLogger evidence;

  LegacyTokenManager(AuthorizationServiceClient authorization,LegacyTokenCache cache,
      SecretResolver secrets,LegacyTokenClient client,LegacyTokenResponseParser parser,
      LegacyTokenRefreshCoordinator coordinator,LegacyTokenAuditPublisher audit,
      DevelopmentTokenEvidenceLogger evidence) {
    this.authorization=authorization;this.cache=cache;this.secrets=secrets;this.client=client;
    this.parser=parser;this.coordinator=coordinator;this.audit=audit;
    this.evidence=evidence;
  }

  Mono<OutboundCredential> resolve(OutboundTokenProvider.ServiceTarget target) {
    return profile(target).flatMap(profile->cache.read(profile.id(),profile.version(),profile.skewSeconds())
        .doOnNext(ignored->{audit.event("cache-hit",profile.id(),"success");evidence.legacyCache("hit",profile.id());})
        .switchIfEmpty(Mono.defer(()->{
          audit.event("cache-miss",profile.id(),"success");
          evidence.legacyCache("miss",profile.id());
          return coordinator.once(cache.lock(profile.id()),
              ()->cache.read(profile.id(),profile.version(),profile.skewSeconds())
                  .switchIfEmpty(acquireAndCache(profile)),
              ()->cache.read(profile.id(),profile.version(),profile.skewSeconds()));
        })).map(token->new OutboundCredential(token.tokenType(),token.accessToken(),true)));
  }

  public Mono<Void> invalidate(String profileId) {
    return cache.invalidate(profileId)
        .doOnSuccess(ignored->audit.event("invalidate",profileId,"success"));
  }

  Mono<Void> invalidate(OutboundTokenProvider.ServiceTarget target) {
    return invalidate(target.authProfileId());
  }

  public Mono<Boolean> cacheStatus(String profileId) { return cache.cached(profileId); }

  /** Acquires a real token for validation, but deliberately does not populate Redis. */
  public Mono<OutboundCredential> test(String profileId) {
    OutboundTokenProvider.ServiceTarget target=new OutboundTokenProvider.ServiceTarget(
        "test",profileId,OutboundAuthMode.LEGACY_SERVICE_TOKEN,-1);
    return profile(target).flatMap(this::acquireUncached)
        .map(token->new OutboundCredential(token.tokenType(),token.accessToken(),true));
  }

  /** Validates registry linkage and the local egress policy without reading a secret. */
  public Mono<Void> validateConnection(String profileId) {
    OutboundTokenProvider.ServiceTarget target=new OutboundTokenProvider.ServiceTarget(
        "test",profileId,OutboundAuthMode.LEGACY_SERVICE_TOKEN,-1);
    return profile(target).flatMap(this::connection)
        .doOnNext(client::validate).then();
  }

  private Mono<OutboundAuthProfile> profile(OutboundTokenProvider.ServiceTarget target) {
    return authorization.outboundAuthProfile(target.authProfileId())
        .map(OutboundAuthProfile::from)
        .flatMap(profile->profile.mode()!=OutboundAuthMode.LEGACY_SERVICE_TOKEN
            ? Mono.error(new IllegalStateException("Not a Legacy profile")):Mono.just(profile));
  }

  private Mono<OutboundConnection> connection(OutboundAuthProfile profile) {
    return authorization.outboundConnection(profile.connectionRef())
        .map(OutboundConnection::from)
        .flatMap(connection->connection.reference().equals(profile.connectionRef())
            ? Mono.just(connection)
            : Mono.error(new IllegalStateException("Outbound connection mismatch")));
  }

  private Mono<LegacyTokenCache.Cached> acquireAndCache(OutboundAuthProfile profile) {
    return Mono.zip(connection(profile),secrets.resolve(
            new SecretResolver.SecretReference(profile.credentialSecretRef())))
        .flatMap(values->request(profile,values.getT1(),values.getT2())
            .flatMap(token->cache.write(profile.id(),token,values.getT2().version(),profile.version())
                .thenReturn(new LegacyTokenCache.Cached(token.accessToken(),token.tokenType(),
                    token.expiresAt(),values.getT2().version(),profile.version()))))
        .doOnSuccess(ignored->audit.event("acquire",profile.id(),"success"))
        .doOnError(ignored->audit.event("acquire",profile.id(),"failure"));
  }

  private Mono<LegacyTokenResponseParser.Parsed> acquireUncached(OutboundAuthProfile profile) {
    return Mono.zip(connection(profile),secrets.resolve(
            new SecretResolver.SecretReference(profile.credentialSecretRef())))
        .flatMap(values->request(profile,values.getT1(),values.getT2()))
        .doOnSuccess(ignored->audit.event("test-acquire",profile.id(),"success"))
        .doOnError(ignored->audit.event("test-acquire",profile.id(),"failure"));
  }

  private Mono<LegacyTokenResponseParser.Parsed> request(OutboundAuthProfile profile,
      OutboundConnection connection,SecretResolver.ResolvedSecret secret) {
    return client.acquire(profile,connection,secret).map(bytes->parser.parse(bytes,profile));
  }
}

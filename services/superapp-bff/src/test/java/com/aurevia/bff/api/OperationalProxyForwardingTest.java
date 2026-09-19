package com.aurevia.bff.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aurevia.bff.observability.DevelopmentTokenEvidenceLogger;
import com.aurevia.bff.outboundauth.OutboundAuthMode;
import com.aurevia.bff.outboundauth.OutboundCredential;
import com.aurevia.bff.outboundauth.OutboundTokenProvider;
import com.aurevia.bff.proxy.GatewayTargetPolicy;
import com.aurevia.bff.proxy.GatewayWebClientFactory;
import com.aurevia.bff.security.SessionIdentity;
import com.aurevia.bff.security.TokenRefreshService;
import com.aurevia.bff.security.TokenVaultService;
import com.aurevia.bff.security.VaultLogoutHandler;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/** Exact forwarding, credential-refresh and limit behaviour of the registry-driven proxy. */
class OperationalProxyForwardingTest {

  private final AuthorizationServiceClient authorization = mock(AuthorizationServiceClient.class);
  private final TokenVaultService vault = mock(TokenVaultService.class);
  private final TokenRefreshService refresh = mock(TokenRefreshService.class);
  private final OutboundTokenProvider provider = mock(OutboundTokenProvider.class);
  private final List<ClientRequest> sent = new ArrayList<>();
  private final TokenVaultService.Tokens tokens =
      new TokenVaultService.Tokens("opaque-user-credential", "refresh", Instant.now().plusSeconds(300));

  @Test void authorizationUsesTheCanonicalObjectComputedByTheAuthorizationService() {
    var route = route(OutboundAuthMode.FORWARD_USER_TOKEN, true, 0,
        "application:aurevia/finance", "/finance-service/ledger");
    allow(route);
    run(controller(ok()), MockServerHttpRequest.get("/api/proxy/fin/ledger"));

    ArgumentCaptor<Map<String, Object>> request = ArgumentCaptor.forClass(Map.class);
    verify(authorization).check(request.capture());
    assertThat(request.getValue().get("resource")).isEqualTo("application:aurevia/finance");
  }

  @Test void forwardsExactlyTheServerComputedUpstreamPathQueryBodyAndAllowlistedHeaders() {
    var route = route(OutboundAuthMode.FORWARD_USER_TOKEN, true, 0, null, "/hr-api/employees/42");
    allow(route);
    // A URI is used verbatim; the String overload of the mock builder would re-encode "%20".
    var request = MockServerHttpRequest.method(org.springframework.http.HttpMethod.POST,
            java.net.URI.create("/api/proxy/hr/employees/42?expand=salary&x=%20y"))
        .header("Content-Type", "application/json").header("Accept", "application/json")
        .header("Cookie", "SESSION=browser").header("X-Forwarded-For", "1.2.3.4")
        .header("Authorization", "Bearer browser-injected")
        .header("X-Correlation-ID", "corr-1").body("{\"a\":1}");
    run(controller(ok()), request);

    ClientRequest upstream = sent.getFirst();
    assertThat(upstream.url().toString())
        .isEqualTo("http://operation-gateway/hr-api/employees/42?expand=salary&x=%20y");
    assertThat(upstream.method().name()).isEqualTo("POST");
    assertThat(upstream.headers().getFirst("Authorization")).isEqualTo("Bearer opaque-user-credential");
    assertThat(upstream.headers().getFirst("Content-Type")).isEqualTo("application/json");
    assertThat(upstream.headers().getFirst("Accept")).isEqualTo("application/json");
    assertThat(upstream.headers().getFirst("X-Correlation-ID")).isEqualTo("corr-1");
    assertThat(upstream.headers().containsKey("Cookie")).isFalse();
    assertThat(upstream.headers().containsKey("X-Forwarded-For")).isFalse();
    assertThat(upstream.headers().containsKey("X-Internal-Legacy-Authorization")).isFalse();
  }

  @Test void upstream401RefreshesTheUserTokenOnceAndRetriesWithTheFreshOne() {
    var route = route(OutboundAuthMode.FORWARD_USER_TOKEN, true, 1, null, "/x");
    allow(route);
    var refreshed = new TokenVaultService.Tokens("opaque-fresh-credential", "refresh",
        Instant.now().plusSeconds(300));
    when(refresh.refresh("handle", tokens)).thenReturn(Mono.just(refreshed));
    AtomicInteger calls = new AtomicInteger();
    run(controller(request -> calls.getAndIncrement() == 0 ? status(HttpStatus.UNAUTHORIZED) : ok().apply(request)),
        MockServerHttpRequest.get("/api/proxy/hr/x"));

    assertThat(sent).hasSize(2);
    assertThat(sent.get(0).headers().getFirst("Authorization")).isEqualTo("Bearer opaque-user-credential");
    assertThat(sent.get(1).headers().getFirst("Authorization")).isEqualTo("Bearer opaque-fresh-credential");
    verify(refresh, times(1)).refresh("handle", tokens);
  }

  @Test void upstream403IsReturnedAsIsAndNeverTriggersARefresh() {
    var route = route(OutboundAuthMode.FORWARD_USER_TOKEN, true, 1, null, "/x");
    allow(route);
    var exchange = run(controller(request -> status(HttpStatus.FORBIDDEN)),
        MockServerHttpRequest.get("/api/proxy/hr/x"));

    assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    assertThat(sent).hasSize(1);
    verify(refresh, never()).refresh(anyString(), any());
  }

  @Test void withoutRetryA401IsReturnedOnceWithoutRefreshingAnything() {
    var route = route(OutboundAuthMode.FORWARD_USER_TOKEN, false, 0, null, "/x");
    allow(route);
    var exchange = run(controller(request -> status(HttpStatus.UNAUTHORIZED)),
        MockServerHttpRequest.post("/api/proxy/hr/x").body("{\"mutation\":true}"));

    assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    assertThat(sent).hasSize(1);
    verify(refresh, never()).refresh(anyString(), any());
  }

  @Test void legacy401InvalidatesTheCachedServiceTokenAndRetriesWithAFreshOne() {
    var route = route(OutboundAuthMode.LEGACY_SERVICE_TOKEN, true, 1, null, "/x");
    allow(route);
    when(provider.resolve(any(), any(), any()))
        .thenReturn(Mono.just(new OutboundCredential("Bearer", "legacy-stale", true)))
        .thenReturn(Mono.just(new OutboundCredential("Bearer", "legacy-fresh", true)));
    when(provider.invalidate(any(), eq(OutboundTokenProvider.InvalidationReason.UPSTREAM_REJECTED)))
        .thenReturn(Mono.empty());
    AtomicInteger calls = new AtomicInteger();
    run(controller(request -> calls.getAndIncrement() == 0 ? status(HttpStatus.UNAUTHORIZED) : ok().apply(request)),
        MockServerHttpRequest.get("/api/proxy/hr/x"));

    assertThat(sent.get(0).headers().getFirst("X-Internal-Legacy-Authorization")).isEqualTo("Bearer legacy-stale");
    assertThat(sent.get(1).headers().getFirst("X-Internal-Legacy-Authorization")).isEqualTo("Bearer legacy-fresh");
    // The user's Public IAM token is untouched by a legacy rejection.
    verify(refresh, never()).refresh(anyString(), any());
    verify(provider).invalidate(any(), eq(OutboundTokenProvider.InvalidationReason.UPSTREAM_REJECTED));
  }

  @Test void declaredBodyLargerThanTheOperationLimitIs413BeforeAnyUpstreamCall() {
    var route = route(OutboundAuthMode.FORWARD_USER_TOKEN, false, 0, null, "/x");
    allow(route);
    var request = MockServerHttpRequest.post("/api/proxy/hr/x").header("Content-Length", "2048")
        .body("x".repeat(2048));
    StepVerifier.create(controller(ok()).proxy("api", "", withSession(MockServerWebExchange.from(request)),
            identity())).expectErrorSatisfies(error -> assertThat(
        ((ResponseStatusException) error).getStatusCode()).isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE)).verify();
    assertThat(sent).isEmpty();
  }

  @Test void responseLargerThanTheTargetLimitIs502() {
    var route = route(OutboundAuthMode.FORWARD_USER_TOKEN, false, 0, null, "/x");
    allow(route);
    StepVerifier.create(controller(request -> Mono.just(ClientResponse.create(HttpStatus.OK)
            .body("y".repeat(70_000)).build())).proxy("api", "",
            withSession(MockServerWebExchange.from(MockServerHttpRequest.get("/api/proxy/hr/x"))), identity()))
        .expectErrorSatisfies(error -> assertThat(((ResponseStatusException) error).getStatusCode())
            .isEqualTo(HttpStatus.BAD_GATEWAY)).verify();
  }

  @Test void upstreamResponseTimeoutIs504() {
    var route = route(OutboundAuthMode.FORWARD_USER_TOKEN, false, 0, null, "/x");
    allow(route);
    StepVerifier.create(controller(request -> Mono.never()).proxy("api", "",
            withSession(MockServerWebExchange.from(MockServerHttpRequest.get("/api/proxy/hr/x"))), identity()))
        .expectErrorSatisfies(error -> assertThat(((ResponseStatusException) error).getStatusCode())
            .isEqualTo(HttpStatus.GATEWAY_TIMEOUT)).verify();
  }

  @Test void transportMismatchBetweenProfileAndModeIsRejectedBeforeCallingUpstream() {
    var base = route(OutboundAuthMode.LEGACY_SERVICE_TOKEN, false, 0, null, "/x");
    var route = new RouteResolution(base.routeId(), base.operationId(), base.panelId(), base.panelSlug(),
        base.routeKey(), base.pathPrefix(), base.targetId(), base.targetKey(), base.gatewayBaseUrl(),
        base.upstreamBasePath(), base.stripPrefix(), null, null, false, base.resourceId(),
        base.resourceKey(), base.actionKey(), true, null, 1024, 1000, 200, 65536, false, 0, null,
        base.authProfileId(), base.authMode(), 1, "USER_AUTHORIZATION_HEADER", null, "/x");
    allow(route);
    StepVerifier.create(controller(ok()).proxy("api", "",
            withSession(MockServerWebExchange.from(MockServerHttpRequest.get("/api/proxy/hr/x"))), identity()))
        .expectErrorSatisfies(error -> assertThat(((ResponseStatusException) error).getStatusCode())
            .isEqualTo(HttpStatus.BAD_GATEWAY)).verify();
    assertThat(sent).isEmpty();
  }

  // ---------------------------------------------------------------- harness

  private void allow(RouteResolution route) {
    when(authorization.resolveRoute(anyString(), anyString())).thenReturn(Mono.just(route));
    when(authorization.check(anyMap())).thenReturn(Mono.just(Map.of("result", "ALLOW")));
    when(vault.read("handle")).thenReturn(Mono.just(tokens));
    when(refresh.ensureFresh("handle", tokens)).thenReturn(Mono.just(tokens));
    when(provider.supports(any())).thenReturn(true);
    if (!"LEGACY_SERVICE_TOKEN".equals(route.authMode())) {
      when(provider.resolve(any(), any(), any()))
          .thenReturn(Mono.just(new OutboundCredential("Bearer", tokens.accessToken(), false)));
    }
  }

  private OperationalProxyController controller(Function<ClientRequest, Mono<ClientResponse>> upstream) {
    var gateway = WebClient.builder().exchangeFunction(request -> {
      sent.add(request);
      return upstream.apply(request);
    }).build();
    return new OperationalProxyController(authorization, vault, refresh,
        GatewayWebClientFactory.fixed(gateway), new GatewayTargetPolicy("http://operation-gateway", ""),
        List.of(provider), mock(DevelopmentTokenEvidenceLogger.class));
  }

  private MockServerWebExchange run(OperationalProxyController controller,
      MockServerHttpRequest.BaseBuilder<?> request) {
    return run(controller, request.build());
  }

  private MockServerWebExchange run(OperationalProxyController controller,
      MockServerHttpRequest request) {
    var exchange = withSession(MockServerWebExchange.from(request));
    StepVerifier.create(controller.proxy("api", "", exchange, identity())).verifyComplete();
    return exchange;
  }

  private static MockServerWebExchange withSession(MockServerWebExchange exchange) {
    exchange.getSession().block().getAttributes().put(VaultLogoutHandler.HANDLE, "handle");
    return exchange;
  }

  private static SessionIdentity identity() {
    return new SessionIdentity("https://issuer.example", "user-subject", "user");
  }

  private static Function<ClientRequest, Mono<ClientResponse>> ok() {
    return request -> Mono.just(ClientResponse.create(HttpStatus.OK)
        .header("Content-Type", "application/json").header("Set-Cookie", "upstream=1")
        .body("{\"ok\":true}").build());
  }

  private static Mono<ClientResponse> status(HttpStatus status) {
    return Mono.just(ClientResponse.create(status).body("{}").build());
  }

  private static RouteResolution route(OutboundAuthMode mode, boolean retry, int maxRetries,
      String resourceObject, String upstreamPath) {
    UUID id = UUID.randomUUID();
    return new RouteResolution(id, id, id, "hr", "hr-route", "/api/proxy/hr", id, "hr-target",
        "http://operation-gateway", "/", 3, null, null, false, id, "page:hr.employees", "view", true,
        null, 1024, 1000, 200, 65536, retry, maxRetries, null, id, mode.name(), 1,
        mode == OutboundAuthMode.LEGACY_SERVICE_TOKEN ? "INTERNAL_LEGACY_HEADER" : "USER_AUTHORIZATION_HEADER",
        resourceObject, upstreamPath);
  }
}

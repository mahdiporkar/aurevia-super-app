package com.aurevia.bff.api;

import com.aurevia.bff.security.*;
import com.aurevia.bff.outboundauth.*;
import com.aurevia.bff.observability.DevelopmentTokenEvidenceLogger;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.reactive.function.client.*;
import org.springframework.http.*;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.assertThat;

class OperationalProxyAuthorizationTest {
  @ParameterizedTest @EnumSource(OutboundAuthMode.class)
  void denialPrecedesVaultAndDownstreamCredentialAcquisition(OutboundAuthMode mode) {
    var authorization=mock(AuthorizationServiceClient.class);
    var vault=mock(TokenVaultService.class);
    var refresh=mock(TokenRefreshService.class);
    var provider=mock(OutboundTokenProvider.class);
    var route=route(mode);
    when(authorization.resolveRoute(anyString(),eq("GET"))).thenReturn(Mono.just(route));
    when(authorization.check(anyMap())).thenReturn(Mono.just(Map.of("result","DENY","reasonCode","NO_RELATIONSHIP")));
    var controller=new OperationalProxyController(authorization,vault,refresh,WebClient.create(),List.of(provider),mock(DevelopmentTokenEvidenceLogger.class));
    var exchange=MockServerWebExchange.from(MockServerHttpRequest.get("/api/proxy/test-sso/api/test/whoami"));
    StepVerifier.create(controller.proxy("api","",exchange,new SessionIdentity("https://issuer.example","user-subject","user")))
      .expectErrorSatisfies(error->assertThat(((ResponseStatusException)error).getStatusCode().value()).isEqualTo(403)).verify();
    verifyNoInteractions(vault,refresh,provider);
  }

  @ParameterizedTest @EnumSource(OutboundAuthMode.class)
  void gatewayReceivesServerCredentialAndNeverBrowserInjectedCredential(OutboundAuthMode mode) {
    var authorization=mock(AuthorizationServiceClient.class);
    var vault=mock(TokenVaultService.class);
    var refresh=mock(TokenRefreshService.class);
    var provider=mock(OutboundTokenProvider.class);
    when(authorization.resolveRoute(anyString(),eq("GET"))).thenReturn(Mono.just(route(mode)));
    when(authorization.check(anyMap())).thenReturn(Mono.just(Map.of("result","ALLOW")));
    var tokens=new TokenVaultService.Tokens("opaque-unit-user-credential",null,Instant.now().plusSeconds(300));
    when(vault.read("handle")).thenReturn(Mono.just(tokens));
    when(refresh.ensureFresh("handle",tokens)).thenReturn(Mono.just(tokens));
    when(provider.supports(mode)).thenReturn(true);
    boolean legacy=mode==OutboundAuthMode.LEGACY_SERVICE_TOKEN;
    when(provider.resolve(any(),any(),any())).thenReturn(Mono.just(new OutboundCredential(
      "Bearer",legacy?"opaque-unit-legacy-credential":tokens.accessToken(),legacy)));
    var sent=new AtomicReference<ClientRequest>();
    var gateway=WebClient.builder().exchangeFunction(request->{sent.set(request);return Mono.just(
      ClientResponse.create(HttpStatus.OK).header("Content-Type","application/json").body("{\"authenticated\":true}").build());}).build();
    var controller=new OperationalProxyController(authorization,vault,refresh,gateway,List.of(provider),mock(DevelopmentTokenEvidenceLogger.class));
    var exchange=MockServerWebExchange.from(MockServerHttpRequest.get("/api/proxy/test-sso/api/test/whoami")
      .header("Authorization","Bearer browser-injected").header("X-Internal-Legacy-Authorization","Bearer browser-injected"));
    exchange.getSession().block().getAttributes().put(VaultLogoutHandler.HANDLE,"handle");
    StepVerifier.create(controller.proxy("api","",exchange,new SessionIdentity("https://issuer.example","user-subject","user"))).verifyComplete();
    assertThat(sent.get().headers().getFirst("Authorization")).isEqualTo("Bearer "+tokens.accessToken());
    assertThat(sent.get().headers().getFirst("X-Internal-Legacy-Authorization"))
      .isEqualTo(legacy?"Bearer opaque-unit-legacy-credential":null);
    assertThat(exchange.getResponse().getBodyAsString().block()).isEqualTo("{\"authenticated\":true}");
    assertThat(exchange.getResponse().getHeaders().containsKey("Authorization")).isFalse();
  }
  private static RouteResolution route(OutboundAuthMode mode) {
    UUID id=UUID.randomUUID();
    return new RouteResolution(id,id,id,"test-sso","test-route","/api/proxy/test-sso",id,
      "test-target",0,"^/api/proxy/test-sso","/test-sso-service",id,"page:test-sso.home",
      "view",true,null,0,3000,5000,65536,false,0,null,id,mode.name(),1,
      mode==OutboundAuthMode.LEGACY_SERVICE_TOKEN?"INTERNAL_LEGACY_HEADER":"USER_AUTHORIZATION_HEADER");
  }
}

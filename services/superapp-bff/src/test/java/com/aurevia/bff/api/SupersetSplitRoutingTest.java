package com.aurevia.bff.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import com.aurevia.bff.proxy.SupersetTargetPolicy;
import com.aurevia.bff.security.SessionIdentity;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.reactive.function.client.WebClient;

class SupersetSplitRoutingTest {
  @ParameterizedTest @ValueSource(booleans = {true, false})
  void staticUsesPublicHostWhileReportsUseOperationHost(boolean assets) throws Exception {
    HttpServer publicHost = host("public", false);
    HttpServer operationHost = host("operation", true);
    try {
      String publicUrl = "http://127.0.0.1:"+publicHost.getAddress().getPort();
      String operationUrl = "http://127.0.0.1:"+operationHost.getAddress().getPort();
      var authorization = mock(AuthorizationServiceClient.class);
      when(authorization.resolveSupersetIntegration("native-public")).thenReturn(reactor.core.publisher.Mono.just(Map.of(
          "public_code", "native-public", "operation_code", "native-operation", "base_url", operationUrl,
          "public_base_url", publicUrl, "tls_required", false, "public_tls_required", false, "auth_mode", "REMOTE_USER")));
      when(authorization.supersetAccess(anyString(),anyString(),anyString(),anyString(),anyString(),
          anyString(),isNull(),anyString(),anyString())).thenReturn(reactor.core.publisher.Mono.just(Map.of("result", "ALLOW")));
      var policy = mock(SupersetTargetPolicy.class);
      when(policy.validate(anyString(),anyBoolean())).thenAnswer(call -> URI.create(call.getArgument(0)));
      when(policy.resolve(any(URI.class),anyString(),isNull())).thenAnswer(call ->
          URI.create(call.getArgument(0).toString()+call.getArgument(1)));
      var controller = new OperationSupersetProxyController(WebClient.create(),authorization,policy,
          new SupersetRequestInspector(new ObjectMapper()));
      String path = assets ? "/static/demo.js" : "/superset/dashboard/1/";
      var exchange = MockServerWebExchange.from(MockServerHttpRequest
          .get("http://localhost:8443/api/integrations/superset/native-public"+path)
          .header("Host", "localhost:8443")
          .cookie(new org.springframework.http.HttpCookie(
              "AUREVIA_SS_native_public__native_session", "demo-session"))
          .header("X-Aurevia-Subject", "forged-browser-subject"));
      controller.proxyIntegration("native-public",path,exchange,
          new SessionIdentity("https://issuer.example", "trusted-subject", "user")).block();
      assertThat(exchange.getResponse().getBodyAsString().block()).isEqualTo(assets ? "public" : "operation");
    } finally {
      publicHost.stop(0); operationHost.stop(0);
    }
  }

  private static HttpServer host(String response,boolean identityExpected) throws Exception {
    var server = HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
    server.createContext("/", request -> {
      boolean correct = identityExpected
          ? "trusted-subject".equals(request.getRequestHeaders().getFirst("X-Aurevia-Subject"))
              && "native_session=demo-session".equals(request.getRequestHeaders().getFirst("Cookie"))
          : request.getRequestHeaders().getFirst("X-Aurevia-Subject") == null
              && request.getRequestHeaders().getFirst("Cookie") == null;
      byte[] body = (correct ? response : "identity-error").getBytes(StandardCharsets.UTF_8);
      request.sendResponseHeaders(200,body.length);
      request.getResponseBody().write(body); request.close();
    });
    server.start(); return server;
  }
}

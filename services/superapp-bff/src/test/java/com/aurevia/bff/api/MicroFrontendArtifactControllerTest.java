package com.aurevia.bff.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import com.aurevia.bff.security.SessionIdentity;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class MicroFrontendArtifactControllerTest {
  @Test void exposesManifestOnlyForModuleInEffectiveUserCatalog() {
    AuthorizationServiceClient authorization=mock(AuthorizationServiceClient.class);
    Map<String,Object> module=Map.of("moduleKey","hr","routes",List.of(),
        "remote",Map.of("remoteEntryUrl","https://private.example/remoteEntry.js"));
    when(authorization.manifest("issuer","user-1")).thenReturn(Mono.just(Map.of(
        "uiCatalog",Map.of("modules",List.of(module)))));
    var controller=new MicroFrontendArtifactController(authorization,WebClient.builder().build(),
        new MicroFrontendArtifactTargetResolver("PRODUCTION_INTERNET",false,"",""));
    var principal=new SessionIdentity("issuer","user-1","operator");

    StepVerifier.create(controller.manifest("hr",principal)).assertNext(response->
        assertThat(response.getBody()).isSameAs(module)).verifyComplete();
    StepVerifier.create(controller.manifest("finance",principal)).expectErrorSatisfies(error->
        assertThat(error).hasMessageContaining("404")).verify();
  }

  @Test void authorizedArtifactIsProxiedAndUnauthorizedModuleNeverReachesTarget() throws Exception {
    AtomicInteger calls=new AtomicInteger();
    HttpServer server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
    server.createContext("/remoteEntry.js",exchange->{
      calls.incrementAndGet();
      byte[] body="globalThis.aurevia_test={};".getBytes(StandardCharsets.UTF_8);
      exchange.getResponseHeaders().set("Content-Type","application/javascript");
      exchange.sendResponseHeaders(200,body.length);
      exchange.getResponseBody().write(body);
      exchange.close();
    });
    server.start();
    try {
      AuthorizationServiceClient authorization=mock(AuthorizationServiceClient.class);
      String url="http://127.0.0.1:"+server.getAddress().getPort()+"/remoteEntry.js";
      Map<String,Object> module=Map.of("moduleKey","hr","routes",List.of(),
          "remote",Map.of("remoteEntryUrl",url));
      when(authorization.manifest("issuer","user-1")).thenReturn(Mono.just(Map.of(
          "uiCatalog",Map.of("modules",List.of(module)))));
      var controller=new MicroFrontendArtifactController(authorization,WebClient.builder().build(),
          new MicroFrontendArtifactTargetResolver("DEVELOPMENT",true,"",""));
      var principal=new SessionIdentity("issuer","user-1","operator");

      StepVerifier.create(controller.artifact("finance","remoteEntry.js",principal))
          .expectErrorSatisfies(error->assertThat(error).hasMessageContaining("404")).verify();
      assertThat(calls).hasValue(0);
      StepVerifier.create(controller.artifact("hr","remoteEntry.js",principal))
          .assertNext(response->{
            assertThat(response.getStatusCode().value()).isEqualTo(200);
            assertThat(new String(response.getBody(),StandardCharsets.UTF_8))
                .contains("aurevia_test");
          }).verifyComplete();
      assertThat(calls).hasValue(1);
    } finally { server.stop(0); }
  }

  @Test void unavailableMfeReturnsGatewayFailureWithoutBreakingCatalogAuthorization() throws Exception {
    int port;
    try(ServerSocket socket=new ServerSocket(0)) { port=socket.getLocalPort(); }
    AuthorizationServiceClient authorization=mock(AuthorizationServiceClient.class);
    Map<String,Object> module=Map.of("moduleKey","hr","routes",List.of(),"remote",Map.of(
        "remoteEntryUrl","http://127.0.0.1:"+port+"/remoteEntry.js"));
    when(authorization.manifest("issuer","user-1")).thenReturn(Mono.just(Map.of(
        "uiCatalog",Map.of("modules",List.of(module)))));
    var controller=new MicroFrontendArtifactController(authorization,WebClient.builder().build(),
        new MicroFrontendArtifactTargetResolver("DEVELOPMENT",true,"",""));
    var principal=new SessionIdentity("issuer","user-1","operator");

    StepVerifier.create(controller.artifact("hr","remoteEntry.js",principal))
        .expectErrorSatisfies(error->assertThat(error).hasMessageContaining("502"))
        .verify();
    StepVerifier.create(controller.manifest("hr",principal))
        .assertNext(response->assertThat(response.getStatusCode().value()).isEqualTo(200))
        .verifyComplete();
  }
}

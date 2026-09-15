package com.aurevia.bff.api;

import com.aurevia.bff.outboundauth.OutboundTokenProvider;
import com.aurevia.bff.observability.DevelopmentTokenEvidenceLogger;
import com.aurevia.bff.security.SessionIdentity;
import com.aurevia.bff.security.TokenRefreshService;
import com.aurevia.bff.security.TokenVaultService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.config.EnableWebFlux;
import org.springframework.web.reactive.config.ResourceHandlerRegistry;
import org.springframework.web.reactive.config.WebFluxConfigurer;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.WebFilter;
import reactor.core.publisher.Mono;
import static org.mockito.Mockito.*;

class SwaggerResourceRoutingTest {
  @Test
  void documentationAssetsReachResourceHandlersBeforeOperationalProxy() {
    try (var context = new AnnotationConfigApplicationContext(Routing.class)) {
      var client = WebTestClient.bindToApplicationContext(context).build();
      for (String path : List.of("/swagger-ui/index.html", "/webjars/swagger-ui/index.html"))
        client.get().uri(path).exchange().expectStatus().isOk()
            .expectBody(String.class).isEqualTo("Swagger routing fixture\n");
      verifyNoInteractions(context.getBean(AuthorizationServiceClient.class));
    }
  }

  @Test
  void ordinaryProxyRequestsStillResolveThroughAuthorizationRegistry() {
    try (var context = new AnnotationConfigApplicationContext(Routing.class)) {
      var client = WebTestClient.bindToApplicationContext(context).build();
      var authorization = context.getBean(AuthorizationServiceClient.class);
      for (String path : List.of("/api/proxy/test-sso/api/test/whoami", "/finance-micro/api/payments")) {
        client.get().uri(path).exchange().expectStatus().isNotFound();
        verify(authorization).resolveRoute(path, "GET");
      }
    }
  }

  @Configuration @EnableWebFlux
  static class Routing implements WebFluxConfigurer {
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
      registry.addResourceHandler("/swagger-ui/**", "/webjars/swagger-ui/**")
          .addResourceLocations("classpath:/swagger-routing-fixture/");
    }
    @Bean AuthorizationServiceClient authorization() {
      var client = mock(AuthorizationServiceClient.class);
      when(client.resolveRoute(anyString(), anyString())).thenReturn(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND)));
      return client;
    }
    @Bean WebFilter principal() {
      return (exchange, chain) -> chain.filter(exchange.mutate().principal(Mono.just(
          new SessionIdentity("https://issuer.example.test", "unit-subject", "unit-user"))).build());
    }
    @Bean OperationalProxyController proxy(AuthorizationServiceClient authorization) {
      return new OperationalProxyController(authorization, mock(TokenVaultService.class),
          mock(TokenRefreshService.class), WebClient.create(), List.<OutboundTokenProvider>of(),
          mock(DevelopmentTokenEvidenceLogger.class));
    }
  }
}

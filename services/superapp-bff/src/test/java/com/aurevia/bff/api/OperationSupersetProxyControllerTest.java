package com.aurevia.bff.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.aurevia.bff.proxy.SupersetTargetPolicy;
import com.aurevia.bff.security.SessionIdentity;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.WebSession;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class OperationSupersetProxyControllerTest {
  @Test
  void directIntegrationRequestIsForbiddenWhenAuthorizationDenies() {
    AuthorizationServiceClient authorization=mock(AuthorizationServiceClient.class);
    when(authorization.resolveSupersetIntegration("superset-operation"))
        .thenReturn(Mono.just(Map.of("operation_code","superset-operation")));
    when(authorization.supersetAccess(eq("https://issuer.example"),eq("subject-1"),
        eq("superset-operation"),eq("superset-operation"),anyString(),eq("GET"),
        isNull(),anyString(),anyString()))
        .thenReturn(Mono.just(Map.of("result","DENY","reasonCode","NO_RELATIONSHIP")));
    var controller=new OperationSupersetProxyController(3000,10000,authorization,
        mock(SupersetTargetPolicy.class),new SupersetRequestInspector(new ObjectMapper()));
    ServerWebExchange exchange=mock(ServerWebExchange.class);
    WebSession session=mock(WebSession.class);
    ServerHttpRequest request=mock(ServerHttpRequest.class);
    when(session.getAttributes()).thenReturn(new HashMap<>());
    when(exchange.getSession()).thenReturn(Mono.just(session));
    when(exchange.getRequest()).thenReturn(request);
    when(request.getMethod()).thenReturn(HttpMethod.GET);
    when(request.getURI()).thenReturn(URI.create(
        "https://aurevia.example/api/integrations/superset/superset-operation/dashboard/42"));

    StepVerifier.create(controller.proxyIntegration("superset-operation",
        "/superset/dashboard/42/",exchange,
        new SessionIdentity("https://issuer.example","subject-1","user")))
        .expectErrorSatisfies(error->{
          assertThat(error).isInstanceOf(ResponseStatusException.class);
          assertThat(((ResponseStatusException)error).getStatusCode().value()).isEqualTo(403);
        }).verify();
  }

  @Test
  void keepsCanonicalSupersetDashboardRedirect() {
    assertThat(rewrite("/superset/dashboard/1/?native_filters_key=test"))
        .isEqualTo("/superset/dashboard/1/?native_filters_key=test");
  }

  @Test
  void tunnelsLoginButCanonicalizesItsDashboardNextParameter() {
    assertThat(rewrite("/login/?next=/reports-runtime/superset/dashboard/1/"))
        .isEqualTo("/reports-runtime/login/?next=/superset/dashboard/1/");
  }

  @Test
  void canonicalizesEncodedDashboardNextParameter() {
    assertThat(rewrite("/login/?next=%2Freports-runtime%2Fsuperset%2Fdashboard%2F1%2F"))
        .isEqualTo("/reports-runtime/login/?next=%2Fsuperset%2Fdashboard%2F1%2F");
  }

  private static String rewrite(String upstreamLocation) {
    HttpHeaders source = new HttpHeaders();
    source.setLocation(java.net.URI.create(upstreamLocation));
    HttpHeaders target = new HttpHeaders();
    OperationSupersetProxyController.copyRewrittenLocation(source, target);
    return target.getFirst(HttpHeaders.LOCATION);
  }
}

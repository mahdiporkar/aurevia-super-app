package com.aurevia.bff.api;

import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.web.server.csrf.CsrfToken;
import org.springframework.security.web.server.csrf.DefaultCsrfToken;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class CsrfControllerTest {

  @Test
  void returnsTokenStoredBySecurityFilter() {
    var exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/v1/csrf"));
    var csrfToken = new DefaultCsrfToken("X-CSRF-TOKEN", "_csrf", "token-value");
    exchange.getAttributes().put(CsrfToken.class.getName(), Mono.just(csrfToken));

    StepVerifier.create(new CsrfController().csrf(exchange))
        .expectNextMatches(response ->
            "X-CSRF-TOKEN".equals(response.get("headerName"))
                && "token-value".equals(response.get("token")))
        .verifyComplete();
  }
}

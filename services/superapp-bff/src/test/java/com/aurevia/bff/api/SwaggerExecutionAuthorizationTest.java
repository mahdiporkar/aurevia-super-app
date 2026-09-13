package com.aurevia.bff.api;

import com.aurevia.bff.security.SessionIdentity;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.codec.HttpMessageWriter;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.mock.http.client.reactive.MockClientHttpRequest;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.reactive.function.BodyInserter;
import org.springframework.web.reactive.function.server.HandlerStrategies;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import static org.assertj.core.api.Assertions.assertThat;

class SwaggerExecutionAuthorizationTest {
  @ParameterizedTest @ValueSource(booleans = {true, false})
  void executionRequiresPlatformManagementAndUsesSessionActor(boolean manager) {
    var forwarded = new AtomicInteger();
    var client = WebClient.builder().baseUrl("http://authorization.test").exchangeFunction(request -> {
      if (request.url().getPath().equals("/internal/v1/authorize/check")) {
        var output = new MockClientHttpRequest(request.method(), request.url());
        var context = new BodyInserter.Context() {
          public List<HttpMessageWriter<?>> messageWriters() { return HandlerStrategies.withDefaults().messageWriters(); }
          public Optional<ServerHttpRequest> serverRequest() { return Optional.empty(); }
          public Map<String, Object> hints() { return Map.of(); }
        };
        return request.body().insert(output, context).then(Mono.defer(output::getBodyAsString)).map(json -> {
          try {
            Map<?, ?> check = new ObjectMapper().readValue(json, Map.class);
            assertThat(check.get("subjectId")).isEqualTo("unit-subject");
            assertThat(check.get("correlationId")).isEqualTo("swagger-unit-correlation");
            assertThat(request.headers().getFirst("X-Correlation-ID")).isEqualTo("swagger-unit-correlation");
            boolean allowed = manager && "application:aurevia".equals(check.get("resource"))
                && "admin".equals(check.get("action"));
            return ClientResponse.create(HttpStatus.OK).header("Content-Type", "application/json")
                .body("{\"result\":\""+(allowed?"ALLOW":"DENY")+"\"}").build();
          } catch (java.io.IOException failure) { throw new IllegalStateException(failure); }
        });
      }
      forwarded.incrementAndGet();
      assertThat(request.headers().getFirst("X-Actor")).isEqualTo("unit-admin");
      assertThat(request.headers().getFirst("X-Actor-Subject")).isEqualTo("unit-subject");
      assertThat(request.headers().getFirst("X-Actor-Issuer")).isEqualTo("https://issuer.example.test");
      return Mono.just(ClientResponse.create(HttpStatus.OK).header("Content-Type", "application/json").body("[]").build());
    }).build();
    var controller = new DeveloperDocumentationController(client, 2_097_152);
    var exchange = MockServerWebExchange.from(MockServerHttpRequest
        .get("/api/v1/docs/authorization/execute/internal/v1/registry/panels")
        .header("X-Actor", "forged").header("X-Correlation-ID", "swagger-unit-correlation"));
    var result = controller.execute("/internal/v1/registry/panels", Mono.empty(), exchange,
        new SessionIdentity("https://issuer.example.test", "unit-subject", "unit-admin"));
    if (manager) {
      StepVerifier.create(result).assertNext(response -> assertThat(response.getStatusCode().value()).isEqualTo(200)).verifyComplete();
      assertThat(forwarded).hasValue(1);
    } else {
      StepVerifier.create(result).expectErrorSatisfies(error ->
          assertThat(((ResponseStatusException) error).getStatusCode().value()).isEqualTo(403)).verify();
      assertThat(forwarded).hasValue(0);
    }
  }
}

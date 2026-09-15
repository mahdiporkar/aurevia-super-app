package com.aurevia.bff.api;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.web.reactive.function.client.*;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import static org.assertj.core.api.Assertions.assertThat;

class RouteResolutionStatusTest {
  @ParameterizedTest @ValueSource(ints={400,403,404,409,503})
  void preservesResolverStatusWithoutReturningInternalResponse(int code) {
    var client=WebClient.builder().exchangeFunction(request -> Mono.just(
        ClientResponse.create(org.springframework.http.HttpStatus.valueOf(code))
            .body("internal diagnostic must remain private").build())).build();
    StepVerifier.create(new AuthorizationServiceClient(client).resolveRoute(
        "/api/proxy/test-sso/api/test/whoami","GET"))
        .expectErrorSatisfies(error -> {
          assertThat(error).isInstanceOf(ResponseStatusException.class);
          var status=(ResponseStatusException)error;
          assertThat(status.getStatusCode().value()).isEqualTo(code);
          assertThat(status.getReason()).isEqualTo("Proxy route resolution rejected");
        }).verify();
  }
}

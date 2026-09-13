package com.aurevia.bff.api;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import com.aurevia.bff.security.SessionIdentity;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import static org.assertj.core.api.Assertions.assertThat;
import org.springframework.web.reactive.function.client.WebClient;

class DeveloperDocumentationControllerTest {
  private final WebClient authorization=WebClient.builder()
      .baseUrl("http://authorization.test").build();

  @Test
  void acceptsBoundedDocumentationBuffer() {
    assertDoesNotThrow(() -> new DeveloperDocumentationController(authorization,2_097_152));
  }

  @Test
  void rejectsUnsafeDocumentationBufferLimits() {
    assertThrows(IllegalArgumentException.class,
        () -> new DeveloperDocumentationController(authorization,262_143));
    assertThrows(IllegalArgumentException.class,
        () -> new DeveloperDocumentationController(authorization,8_388_609));
  }

  @Test
  void facadeIsPresentWhenDocumentationIsEnabledOutsideProduction() {
    context().run(application -> assertThat(application).hasSingleBean(DeveloperDocumentationController.class));
  }

  @Test
  void documentationDisabledAlsoDisablesTheInternalExecutionFacade() {
    context().withPropertyValues("springdoc.api-docs.enabled=false")
        .run(application -> assertThat(application).doesNotHaveBean(DeveloperDocumentationController.class));
  }

  @Test
  void productionDoesNotExposeTheInternalExecutionFacade() {
    context().withInitializer(application -> application.getEnvironment().setActiveProfiles("prod"))
        .run(application -> assertThat(application).doesNotHaveBean(DeveloperDocumentationController.class));
  }

  private ApplicationContextRunner context() {
    return new ApplicationContextRunner()
        .withBean("authorizationWebClient", WebClient.class, () -> authorization)
        .withUserConfiguration(DeveloperDocumentationController.class);
  }

  @ParameterizedTest @ValueSource(strings = {
      "/internal/v1/../../actuator/health", "/internal/v1/%2e%2e/registry/panels",
      "/internal/v1//registry/panels", "/internal/v1/registry/panels?query=ambiguous"
  })
  void ambiguousOrTraversingPathsAreRejectedBeforeConnecting(String path) {
    var controller = new DeveloperDocumentationController(authorization, 2_097_152);
    var exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/v1/docs/authorization/execute/test"));
    StepVerifier.create(controller.execute(path, Mono.empty(), exchange,
        new SessionIdentity("https://issuer.example.test", "unit-subject", "unit-user")))
        .expectErrorSatisfies(error -> assertThat(((ResponseStatusException) error).getStatusCode().value()).isEqualTo(400))
        .verify();
  }
}

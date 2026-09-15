package com.aurevia.bff.api;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.ObjectMapper;
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
  private final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  void acceptsBoundedDocumentationBuffer() {
    assertDoesNotThrow(() -> new DeveloperDocumentationController(
        authorization,2_097_152,objectMapper));
  }

  @Test
  void rejectsUnsafeDocumentationBufferLimits() {
    assertThrows(IllegalArgumentException.class,
        () -> new DeveloperDocumentationController(authorization,262_143,objectMapper));
    assertThrows(IllegalArgumentException.class,
        () -> new DeveloperDocumentationController(authorization,8_388_609,objectMapper));
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
        .withBean(ObjectMapper.class, () -> objectMapper)
        .withUserConfiguration(DeveloperDocumentationController.class);
  }

  @Test
  void adminContractUsesTheExactPublicBffPathsAndKeepsDownstreamSchemas() throws Exception {
    byte[] source = """
        {"openapi":"3.0.1","info":{"title":"Authorization","version":"1"},
         "servers":[{"url":"/api/v1/docs/authorization/execute"}],
         "paths":{
           "/internal/v1/registry/panels/{id}":{"put":{"operationId":"Registry_update",
             "parameters":[{"in":"path","name":"id","required":true},
               {"in":"header","name":"X-Actor","required":false}],
             "responses":{"200":{"description":"ok"}}}},
           "/internal/v1/authorize/check":{"post":{"operationId":"Authorization_check",
             "responses":{"200":{"description":"ok"}}}}},
         "components":{"schemas":{"Panel":{"type":"object","properties":{"code":{"type":"string"}}}}}}
        """.getBytes(java.nio.charset.StandardCharsets.UTF_8);

    var projected = objectMapper.readTree(
        DeveloperDocumentationController.projectAdminSpecification(source, objectMapper));

    assertThat(projected.path("paths").has("/api/v1/admin/panels/{id}")).isTrue();
    assertThat(projected.path("paths").has("/internal/v1/registry/panels/{id}")).isFalse();
    assertThat(projected.path("paths").has("/internal/v1/authorize/check")).isFalse();
    assertThat(projected.at("/paths/~1api~1v1~1admin~1panels~1{id}/put/operationId").asText())
        .isEqualTo("Registry_update");
    assertThat(projected.at("/paths/~1api~1v1~1admin~1panels~1{id}/put/parameters")).hasSize(1);
    assertThat(projected.at("/components/schemas/Panel/properties/code/type").asText())
        .isEqualTo("string");
    assertThat(projected.at("/servers/0/url").asText()).isEqualTo("/");
  }

  @Test
  void invalidOrEmptyDownstreamContractFailsClosed() {
    assertThrows(ResponseStatusException.class, () ->
        DeveloperDocumentationController.projectAdminSpecification(
            "{\"openapi\":\"3.0.1\",\"paths\":{}}".getBytes(), objectMapper));
  }

  @ParameterizedTest @ValueSource(strings = {
      "/internal/v1/../../actuator/health", "/internal/v1/%2e%2e/registry/panels",
      "/internal/v1//registry/panels", "/internal/v1/registry/panels?query=ambiguous"
  })
  void ambiguousOrTraversingPathsAreRejectedBeforeConnecting(String path) {
    var controller = new DeveloperDocumentationController(authorization, 2_097_152, objectMapper);
    var exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/v1/docs/authorization/execute/test"));
    StepVerifier.create(controller.execute(path, Mono.empty(), exchange,
        new SessionIdentity("https://issuer.example.test", "unit-subject", "unit-user")))
        .expectErrorSatisfies(error -> assertThat(((ResponseStatusException) error).getStatusCode().value()).isEqualTo(400))
        .verify();
  }
}

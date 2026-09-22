package com.aurevia.authz.contract;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.aurevia.authz.api.ApiError;
import com.aurevia.authz.api.ApiExceptionHandler;
import com.aurevia.authz.docs.AuthorizationOpenApiConfiguration;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.models.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Runtime error bodies must equal the documented {@code ApiError} schema for every error path:
 * exception handler, admin interceptor and authentication entry point.
 */
class ApiErrorContractTest {
  private static final ObjectMapper JSON = new ObjectMapper();

  @RestController
  static class Probe {
    record Body(@NotBlank String name) {}
    @GetMapping("/probe/illegal") String illegal() { throw new IllegalArgumentException("resourceKey must be normalized"); }
    @GetMapping("/probe/status") String status(@RequestParam int code) { throw new ResponseStatusException(HttpStatus.valueOf(code), "reason text"); }
    @GetMapping("/probe/lock") String lock() { throw new OptimisticLockingFailureException("VERSION_CONFLICT"); }
    @GetMapping("/probe/integrity") String integrity() { throw new DataIntegrityViolationException("duplicate key"); }
    @GetMapping("/probe/boom") String boom() { throw new IllegalStateException("secret-internal-detail"); }
    @PostMapping("/probe/body") String body(@Valid @RequestBody Body body) { return body.name(); }
    @GetMapping("/probe/param") String param(@RequestParam("size") int size) { return "ok"; }
  }

  private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new Probe())
      .setControllerAdvice(new ApiExceptionHandler()).build();

  @ParameterizedTest
  @CsvSource({
      "/probe/illegal, 400, INVALID_REQUEST",
      "/probe/status?code=404, 404, NOT_FOUND",
      "/probe/status?code=409, 409, CONFLICT",
      "/probe/status?code=403, 403, ACCESS_DENIED",
      "/probe/lock, 409, OPTIMISTIC_LOCK_CONFLICT",
      "/probe/integrity, 409, DATA_CONFLICT",
      "/probe/boom, 500, INTERNAL_ERROR",
      "/probe/param, 400, INVALID_REQUEST",
      "/probe/param?size=abc, 400, INVALID_REQUEST"})
  void everyErrorPathAnswersWithTheDocumentedShape(String path, int status, String code) throws Exception {
    MvcResult result = mvc.perform(get(path).header("X-Correlation-ID", "corr-123")).andReturn();
    assertThat(result.getResponse().getStatus()).isEqualTo(status);
    JsonNode body = assertApiError(result.getResponse().getContentAsString());
    assertThat(body.get("code").asText()).isEqualTo(code);
    assertThat(body.get("correlationId").asText()).isEqualTo("corr-123");
    assertThat(body.get("message").asText()).doesNotContain("secret-internal-detail", "abc");
  }

  @Test
  void beanValidationFailuresListFieldNamesOnly() throws Exception {
    MvcResult result = mvc.perform(post("/probe/body").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"\"}")).andReturn();
    assertThat(result.getResponse().getStatus()).isEqualTo(400);
    JsonNode body = assertApiError(result.getResponse().getContentAsString());
    assertThat(body.get("message").asText()).contains("name");
    MvcResult malformed = mvc.perform(post("/probe/body").contentType(MediaType.APPLICATION_JSON).content("{not json")).andReturn();
    assertThat(malformed.getResponse().getStatus()).isEqualTo(400);
    assertApiError(malformed.getResponse().getContentAsString());
  }

  @Test
  void interceptorAndEntryPointWriteTheSameShape() throws Exception {
    var request = new MockHttpServletRequest();
    request.setAttribute(ApiError.REQUEST_ATTRIBUTE, "filter-correlation");
    var response = new MockHttpServletResponse();
    ApiError.of("ACCESS_DENIED", "Administrative permission required: can_manage", request).write(response, 403);
    assertThat(response.getStatus()).isEqualTo(403);
    assertThat(response.getContentType()).startsWith(MediaType.APPLICATION_JSON_VALUE);
    JsonNode body = assertApiError(response.getContentAsString());
    assertThat(body.get("correlationId").asText()).isEqualTo("filter-correlation");
    var quoted = new MockHttpServletResponse();
    String tricky = "a \"quoted\" \\ message" + System.lineSeparator();
    ApiError.of("X", tricky, new MockHttpServletRequest()).write(quoted, 401);
    assertThat(JSON.readTree(quoted.getContentAsString()).get("message").asText()).isEqualTo(tricky);
  }

  /** The body has exactly the documented properties, all of them, and nothing else. */
  private static JsonNode assertApiError(String json) throws Exception {
    JsonNode body = JSON.readTree(json);
    Schema<?> schema = new AuthorizationOpenApiConfiguration().authorizationOpenApi().getComponents().getSchemas().get("ApiError");
    Set<String> documented = schema.getProperties().keySet();
    Set<String> actual = new java.util.TreeSet<>();
    body.fieldNames().forEachRemaining(actual::add);
    assertThat(actual).isEqualTo(new java.util.TreeSet<>(documented));
    assertThat(schema.getRequired()).containsExactlyInAnyOrderElementsOf(documented);
    for (String property : documented) assertThat(body.get(property).asText()).as(property).isNotBlank();
    return body;
  }

  @Test
  void statusCodeMappingIsStable() {
    assertThat(Map.of(400, "INVALID_REQUEST", 401, "AUTHENTICATION_REQUIRED", 403, "ACCESS_DENIED", 404, "NOT_FOUND",
        409, "CONFLICT", 502, "UPSTREAM_ERROR", 504, "UPSTREAM_TIMEOUT", 418, "REQUEST_REJECTED", 503, "SERVICE_UNAVAILABLE"))
        .allSatisfy((status, code) -> assertThat(ApiExceptionHandler.codeFor(HttpStatus.valueOf(status))).isEqualTo(code));
  }
}

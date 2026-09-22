package com.aurevia.bff.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.aurevia.bff.docs.BffOpenApiConfiguration;
import com.aurevia.bff.identity.KeycloakAdminException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** Runtime BFF error bodies equal the documented ApiError schema for every controller error path. */
class BffErrorContractTest {
  private static final ObjectMapper JSON = new ObjectMapper();

  @RestController
  static class Probe {
    @GetMapping("/probe/status") String status(@RequestParam int code) { throw new ResponseStatusException(HttpStatus.valueOf(code), "why"); }
    @GetMapping("/probe/keycloak") String keycloak() { throw new KeycloakAdminException(HttpStatus.CONFLICT, "KEYCLOAK_USER_CONFLICT", "exists"); }
    @GetMapping("/probe/boom") String boom() { throw new IllegalStateException("internal-detail"); }
    @GetMapping("/probe/param") String param(@RequestParam("n") int n) { return "ok"; }
  }

  private final WebTestClient client = WebTestClient.bindToController(new Probe())
      .controllerAdvice(new BffExceptionHandler()).build();

  @Test void everyErrorPathAnswersWithTheDocumentedShape() throws Exception {
    String[][] probes = {
        {"/probe/status?code=404", "404", "NOT_FOUND"}, {"/probe/status?code=403", "403", "ACCESS_DENIED"},
        {"/probe/status?code=502", "502", "UPSTREAM_ERROR"}, {"/probe/keycloak", "409", "KEYCLOAK_USER_CONFLICT"},
        {"/probe/boom", "500", "INTERNAL_ERROR"}, {"/probe/param?n=x", "400", "INVALID_REQUEST"},
        {"/probe/param", "400", "INVALID_REQUEST"}};
    for (String[] probe : probes) {
      byte[] body = client.get().uri(probe[0]).header("X-Correlation-ID", "corr-42").exchange()
          .expectStatus().isEqualTo(Integer.parseInt(probe[1])).expectBody().returnResult().getResponseBody();
      JsonNode json = assertApiError(new String(body));
      assertThat(json.get("code").asText()).as(probe[0]).isEqualTo(probe[2]);
      assertThat(json.get("correlationId").asText()).isEqualTo("corr-42");
      assertThat(json.get("message").asText()).doesNotContain("internal-detail");
    }
  }

  private static JsonNode assertApiError(String text) throws Exception {
    JsonNode body = JSON.readTree(text);
    var schema = new BffOpenApiConfiguration().bffOpenApi().getComponents().getSchemas().get("ApiError");
    Set<String> actual = new TreeSet<>();
    body.fieldNames().forEachRemaining(actual::add);
    assertThat(actual).isEqualTo(new TreeSet<>(schema.getProperties().keySet()));
    assertThat(schema.getRequired()).containsExactlyInAnyOrderElementsOf(schema.getProperties().keySet());
    return body;
  }
}

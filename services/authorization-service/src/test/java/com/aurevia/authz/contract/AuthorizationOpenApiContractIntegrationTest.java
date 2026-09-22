package com.aurevia.authz.contract;

import static org.assertj.core.api.Assertions.assertThat;

import com.aurevia.authz.support.PermissionStack;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/**
 * The generated runtime OpenAPI document (what Swagger UI serves) is compared with the live
 * request mappings of the same application context: exact method + path in both directions,
 * declared path parameters, resolvable references, valid examples, Persian text, unique operation
 * ids, correct success codes and the ApiError contract on every documented error.
 */
@EnabledIfEnvironmentVariable(named = "AUREVIA_TEST_OPENFGA_URL", matches = ".+")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AuthorizationOpenApiContractIntegrationTest {
  private static final Pattern PERSIAN = Pattern.compile("[\\u0600-\\u06FF]");
  private static final Pattern PATH_VARIABLE = Pattern.compile("\\{([^}/]+)}");
  private static final PermissionStack STACK;
  private static final ObjectMapper JSON = new ObjectMapper();
  private static JsonNode document;

  static {
    try { STACK = PermissionStack.configured() ? PermissionStack.provision() : null; }
    catch (Exception failure) { throw new IllegalStateException("Permission stack could not be provisioned", failure); }
  }

  @DynamicPropertySource
  static void stack(DynamicPropertyRegistry registry) {
    if (STACK == null) return;
    registry.add("spring.datasource.url", () -> STACK.jdbcUrl);
    registry.add("spring.datasource.username", () -> STACK.jdbcUser);
    registry.add("spring.datasource.password", () -> STACK.jdbcPassword);
    registry.add("spring.data.redis.host", () -> STACK.redisHost);
    registry.add("spring.data.redis.port", () -> STACK.redisPort);
    registry.add("spring.data.redis.password", () -> STACK.redisPassword);
    registry.add("aurevia.openfga.base-url", () -> STACK.openFgaUrl);
    registry.add("aurevia.openfga.store-id", () -> STACK.storeId);
    registry.add("aurevia.openfga.model-id", () -> STACK.modelId);
    registry.add("aurevia.openfga.reconcile-on-startup", () -> "false");
    registry.add("aurevia.internal.username", () -> "bff");
    registry.add("aurevia.internal.password", () -> "test-secret");
  }

  @AfterAll static void tearDown() throws Exception { if (STACK != null) STACK.dropDatabase(); }

  @Autowired TestRestTemplate http;
  @Autowired @org.springframework.beans.factory.annotation.Qualifier("requestMappingHandlerMapping") RequestMappingHandlerMapping mappings;
  @LocalServerPort int port;

  private JsonNode document() throws Exception {
    if (document == null) {
      ResponseEntity<String> response = http.exchange("http://localhost:" + port + "/v3/api-docs", HttpMethod.GET,
          new HttpEntity<>(headers()), String.class);
      assertThat(response.getStatusCode().value()).isEqualTo(200);
      document = JSON.readTree(response.getBody());
    }
    return document;
  }

  // ---------------------------------------------------------------- bidirectional sync

  @Test void everyLiveMappingIsDocumentedAndNothingElse() throws Exception {
    Set<String> live = new TreeSet<>();
    for (Map.Entry<RequestMappingInfo, HandlerMethod> entry : mappings.getHandlerMethods().entrySet()) {
      if (!entry.getValue().getBeanType().getPackageName().startsWith("com.aurevia.authz.api")) continue;
      for (String path : entry.getKey().getPathPatternsCondition().getPatternValues())
        for (var method : entry.getKey().getMethodsCondition().getMethods())
          live.add(method.name() + " " + path);
    }
    Set<String> documented = new TreeSet<>();
    document().get("paths").fields().forEachRemaining(path -> path.getValue().fieldNames()
        .forEachRemaining(method -> documented.add(method.toUpperCase() + " " + path.getKey())));
    assertThat(documented).as("stale OpenAPI operations").isSubsetOf(live);
    assertThat(live).as("undocumented controller mappings").isSubsetOf(documented);
    assertThat(live).hasSizeGreaterThan(50);
  }

  @Test void operationsHavePersianTextUniqueIdsTagsAndDeclaredPathParameters() throws Exception {
    Set<String> operationIds = new HashSet<>();
    forEachOperation((path, method, operation) -> {
      String label = method + " " + path;
      assertThat(operation.path("operationId").asText()).as("operationId " + label).isNotBlank();
      assertThat(operationIds.add(operation.get("operationId").asText())).as("duplicate operationId " + label).isTrue();
      assertThat(PERSIAN.matcher(operation.path("summary").asText()).find()).as("Persian summary " + label).isTrue();
      assertThat(PERSIAN.matcher(operation.path("description").asText()).find()).as("Persian description " + label).isTrue();
      assertThat(operation.path("tags")).as("tag " + label).isNotEmpty();
      Set<String> declared = new HashSet<>();
      for (JsonNode parameter : operation.path("parameters")) {
        assertThat(PERSIAN.matcher(parameter.path("description").asText()).find())
            .as("Persian parameter description " + label + " " + parameter.path("name").asText()).isTrue();
        if ("path".equals(parameter.path("in").asText())) {
          assertThat(parameter.path("required").asBoolean()).as("required path parameter " + label).isTrue();
          declared.add(parameter.get("name").asText());
        }
      }
      Matcher variables = PATH_VARIABLE.matcher(path);
      while (variables.find()) assertThat(declared).as("path variable declared " + label).contains(variables.group(1));
      assertThat(operation.path("responses").size()).as("responses " + label).isGreaterThan(0);
      operation.path("responses").fields().forEachRemaining(response -> assertThat(PERSIAN.matcher(
          response.getValue().path("description").asText()).find()).as("Persian response description " + label + " " + response.getKey()).isTrue());
    });
  }

  @Test void successStatusMatchesTheControllerContract() throws Exception {
    Map<String, HandlerMethod> byMapping = new java.util.HashMap<>();
    for (Map.Entry<RequestMappingInfo, HandlerMethod> entry : mappings.getHandlerMethods().entrySet())
      for (String path : entry.getKey().getPathPatternsCondition().getPatternValues())
        for (var method : entry.getKey().getMethodsCondition().getMethods())
          byMapping.put(method.name() + " " + path, entry.getValue());
    forEachOperation((path, method, operation) -> {
      HandlerMethod handler = byMapping.get(method + " " + path);
      var status = handler.getMethodAnnotation(org.springframework.web.bind.annotation.ResponseStatus.class);
      List<String> success = new ArrayList<>();
      operation.path("responses").fieldNames().forEachRemaining(code -> { if (code.startsWith("2") || code.startsWith("3")) success.add(code); });
      String expected = status == null ? "200" : String.valueOf(status.value().value());
      assertThat(success).as("success code of " + method + " " + path).containsExactly(expected);
      if ("204".equals(expected)) assertThat(operation.path("responses").path("204").has("content")).as("204 without body " + path).isFalse();
    });
  }

  @Test void everyDocumentedErrorUsesTheApiErrorSchemaAndAllReferencesResolve() throws Exception {
    JsonNode schemas = document().path("components").path("schemas");
    assertThat(schemas.path("ApiError").path("required")).hasSize(3);
    forEachOperation((path, method, operation) -> operation.path("responses").fields().forEachRemaining(response -> {
      if (!response.getKey().startsWith("4") && !response.getKey().startsWith("5")) return;
      assertThat(response.getValue().path("content").path("application/json").path("schema").path("$ref").asText())
          .as(method + " " + path + " " + response.getKey()).isEqualTo("#/components/schemas/ApiError");
    }));
    List<String> broken = new ArrayList<>();
    collectRefs(document(), broken, schemas);
    assertThat(broken).as("unresolvable $ref").isEmpty();
  }

  @Test void requestExamplesSatisfyTheirSchemasAndContainNoSecretsOrDemoIdentifiers() throws Exception {
    JsonNode schemas = document().path("components").path("schemas");
    List<String> problems = new ArrayList<>();
    forEachOperation((path, method, operation) -> {
      JsonNode content = operation.path("requestBody").path("content");
      content.fields().forEachRemaining(media -> {
        JsonNode schema = media.getValue().path("schema");
        JsonNode examples = media.getValue().path("examples");
        if (examples.isMissingNode()) { problems.add("no example: " + method + " " + path); return; }
        examples.fields().forEachRemaining(example -> {
          JsonNode value = example.getValue().path("value");
          String text = value.toString();
          for (String forbidden : List.of("eyJ", "local-change-me", "\"password\":\"", "\"clientSecret\":\"", "Bearer "))
            if (text.contains(forbidden)) problems.add("secret-looking example: " + method + " " + path);
          for (String demo : List.of("finance", "payroll", "hr-viewer", "hr.employee", "finance-maker"))
            if (text.toLowerCase().contains(demo)) problems.add("demo identifier in example (" + demo + "): " + method + " " + path);
          validate(value, schema, schemas, method + " " + path, problems);
        });
      });
    });
    assertThat(problems).isEmpty();
  }

  @Test void schemaFieldsAndEnumsAreDocumentedInPersianAndMatchCode() throws Exception {
    JsonNode schemas = document().path("components").path("schemas");
    List<String> undocumented = new ArrayList<>();
    schemas.fields().forEachRemaining(schema -> schema.getValue().path("properties").fields().forEachRemaining(property -> {
      if (!PERSIAN.matcher(property.getValue().path("description").asText()).find())
        undocumented.add(schema.getKey() + "." + property.getKey());
    }));
    assertThat(undocumented).as("schema fields without a Persian description").isEmpty();
    JsonNode resourceRequest = schemas.path("com.aurevia.authz.api.dto.AccessAdminDtos$ResourceRequest");
    if (!resourceRequest.isMissingNode()) {
      List<String> types = new ArrayList<>();
      resourceRequest.path("properties").path("type").path("enum").forEach(node -> types.add(node.asText()));
      assertThat(types).containsExactlyInAnyOrderElementsOf(com.aurevia.authz.access.AccessAdministrationService.RESOURCE_TYPES);
    }
  }

  // ---------------------------------------------------------------- helpers

  private interface OperationVisitor { void visit(String path, String method, JsonNode operation); }

  private void forEachOperation(OperationVisitor visitor) throws Exception {
    document().get("paths").fields().forEachRemaining(path -> path.getValue().fields().forEachRemaining(
        operation -> visitor.visit(path.getKey(), operation.getKey().toUpperCase(), operation.getValue())));
  }

  private static void collectRefs(JsonNode node, List<String> broken, JsonNode schemas) {
    if (node.isObject()) {
      JsonNode ref = node.get("$ref");
      if (ref != null && ref.isTextual()) {
        String name = ref.asText().replace("#/components/schemas/", "");
        if (!schemas.has(name)) broken.add(ref.asText());
      }
      node.fields().forEachRemaining(field -> collectRefs(field.getValue(), broken, schemas));
    } else if (node.isArray()) node.forEach(item -> collectRefs(item, broken, schemas));
  }

  /** Structural validation: required properties present, no unknown properties, enum values valid. */
  private static void validate(JsonNode value, JsonNode schema, JsonNode schemas, String label, List<String> problems) {
    if (schema.has("$ref")) schema = schemas.path(schema.get("$ref").asText().replace("#/components/schemas/", ""));
    if (value.isArray()) {
      JsonNode items = schema.path("items");
      value.forEach(item -> validate(item, items, schemas, label, problems));
      return;
    }
    if (!value.isObject() || schema.isMissingNode()) return;
    JsonNode properties = schema.path("properties");
    if (properties.isMissingNode()) return;
    for (JsonNode required : schema.path("required"))
      if (!value.has(required.asText())) problems.add(label + ": example misses required " + required.asText());
    Iterator<Map.Entry<String, JsonNode>> fields = value.fields();
    while (fields.hasNext()) {
      Map.Entry<String, JsonNode> field = fields.next();
      JsonNode property = properties.get(field.getKey());
      if (property == null) {
        if (!schema.path("additionalProperties").isMissingNode()) continue;
        problems.add(label + ": example property not in schema: " + field.getKey());
        continue;
      }
      if (property.has("enum")) {
        List<String> allowed = new ArrayList<>();
        property.get("enum").forEach(node -> allowed.add(node.asText()));
        if (!allowed.contains(field.getValue().asText())) problems.add(label + ": example enum value " + field.getValue() + " not in " + allowed);
      }
      validate(field.getValue(), property, schemas, label + "." + field.getKey(), problems);
    }
  }

  private static HttpHeaders headers() {
    HttpHeaders headers = new HttpHeaders();
    headers.setBasicAuth("bff", "test-secret");
    return headers;
  }
}

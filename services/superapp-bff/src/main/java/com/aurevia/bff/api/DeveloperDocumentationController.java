package com.aurevia.bff.api;

import com.aurevia.bff.security.SessionIdentity;
import com.aurevia.bff.proxy.RouteNormalizer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.swagger.v3.oas.annotations.Hidden;
import java.io.IOException;
import java.security.Principal;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

/**
 * Development-only façade used by the aggregated Swagger UI.
 * Internal Basic/mTLS credentials remain in the configured server-side WebClient.
 */
@Hidden
@Profile("!prod")
@ConditionalOnProperty(name = "springdoc.api-docs.enabled", havingValue = "true", matchIfMissing = true)
@RestController
public class DeveloperDocumentationController {
  private static final String REGISTRY_PREFIX = "/internal/v1/registry";
  private static final String ADMIN_PREFIX = "/api/v1/admin";
  private final WebClient authorization;
  private final ObjectMapper objectMapper;

  public DeveloperDocumentationController(
      @Qualifier("authorizationWebClient") WebClient authorization,
      @Value("${aurevia.documentation.max-openapi-bytes:2097152}") int maxOpenApiBytes,
      ObjectMapper objectMapper) {
    if(maxOpenApiBytes<262_144||maxOpenApiBytes>8_388_608) {
      throw new IllegalArgumentException("max-openapi-bytes must be between 256 KiB and 8 MiB");
    }
    this.authorization = authorization.mutate().codecs(codecs ->
        codecs.defaultCodecs().maxInMemorySize(maxOpenApiBytes)).build();
    this.objectMapper = objectMapper;
  }

  @GetMapping("/api/v1/docs/authorization/openapi")
  Mono<ResponseEntity<byte[]>> specification() {
    return fetchSpecification(false);
  }

  /**
   * Browser-facing Admin contract derived from the Authorization Service contract.
   * Keeping this projection at the BFF boundary makes its paths match the URLs used by
   * the Admin MFE without duplicating downstream DTO schemas or operation metadata.
   */
  @GetMapping("/api/v1/docs/admin/openapi")
  Mono<ResponseEntity<byte[]>> adminSpecification() {
    return fetchSpecification(true);
  }

  private Mono<ResponseEntity<byte[]>> fetchSpecification(boolean adminProjection) {
    return authorization.get().uri("/v3/api-docs").accept(MediaType.APPLICATION_JSON)
        .exchangeToMono(response -> response.bodyToMono(byte[].class).defaultIfEmpty(new byte[0])
            .map(body -> {
              byte[] result = body;
              if (adminProjection && response.statusCode().is2xxSuccessful()) {
                result = projectAdminSpecification(body, objectMapper);
              }
              return ResponseEntity.status(response.statusCode())
                  .contentType(MediaType.APPLICATION_JSON).body(result);
            }));
  }

  static byte[] projectAdminSpecification(byte[] source, ObjectMapper objectMapper) {
    try {
      JsonNode root = objectMapper.readTree(source);
      if (root == null || !root.isObject()) {
        throw new IOException("OpenAPI root object is missing");
      }
      ObjectNode document = (ObjectNode) root;
      JsonNode sourcePaths = document.path("paths");
      if (!sourcePaths.isObject()) {
        throw new IOException("OpenAPI paths object is missing");
      }

      ObjectNode publicPaths = objectMapper.createObjectNode();
      for (Map.Entry<String, JsonNode> entry : sourcePaths.properties()) {
        if (!entry.getKey().equals(REGISTRY_PREFIX)
            && !entry.getKey().startsWith(REGISTRY_PREFIX + "/")) {
          continue;
        }
        JsonNode pathItem = entry.getValue().deepCopy();
        removeServerGeneratedActorParameters(pathItem);
        publicPaths.set(ADMIN_PREFIX + entry.getKey().substring(REGISTRY_PREFIX.length()), pathItem);
      }
      if (publicPaths.isEmpty()) {
        throw new IOException("Authorization OpenAPI contains no registry operations");
      }
      document.set("paths", publicPaths);

      ObjectNode info = document.path("info").isObject()
          ? (ObjectNode) document.path("info") : objectMapper.createObjectNode();
      document.set("info", info);
      info.put("title", "API عمومی راهبری Aurevia از مسیر BFF");
      info.put("description", "این قرارداد به‌صورت خودکار از قرارداد Authorization Service ساخته شده است؛ "
          + "pathها همان URLهای عمومی مورد استفاده Admin MFE هستند و BFF هویت عامل را از نشست می‌سازد.");
      ArrayNode servers = objectMapper.createArrayNode();
      servers.add(objectMapper.createObjectNode().put("url", "/")
          .put("description", "همان origin سوپر اپ؛ نمونه local: http://localhost:8443"));
      document.set("servers", servers);
      return objectMapper.writeValueAsBytes(document);
    } catch (IOException error) {
      throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
          "Authorization Service returned an invalid OpenAPI document", error);
    }
  }

  private static void removeServerGeneratedActorParameters(JsonNode pathItem) {
    removeActorParameters(pathItem);
    for (String method : new String[]{"get", "post", "put", "patch", "delete", "head",
        "options", "trace"}) {
      removeActorParameters(pathItem.path(method));
    }
  }

  private static void removeActorParameters(JsonNode container) {
    JsonNode parameters = container.path("parameters");
    if (!(parameters instanceof ArrayNode array)) return;
    for (int index = array.size() - 1; index >= 0; index--) {
      if (isActorHeader(array.get(index))) array.remove(index);
    }
  }

  private static boolean isActorHeader(JsonNode parameter) {
    if (!parameter.isObject() || !"header".equals(parameter.path("in").asText())) return false;
    return switch (parameter.path("name").asText().toLowerCase(java.util.Locale.ROOT)) {
      case "x-actor", "x-actor-subject", "x-actor-issuer" -> true;
      default -> false;
    };
  }

  @RequestMapping("/api/v1/docs/authorization/execute/{*path}")
  Mono<ResponseEntity<byte[]>> execute(@PathVariable("path") String path,
      @RequestBody(required = false) Mono<byte[]> requestBody, ServerWebExchange exchange,
      Principal principal) {
    SessionIdentity identity = SessionIdentity.from(principal);
    boolean allowedPath;
    try {
      allowedPath = path.startsWith("/internal/v1/") && path.equals(RouteNormalizer.normalizePath(path));
    } catch (IllegalArgumentException invalid) {
      allowedPath = false;
    }
    if (!allowedPath) {
      return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST,
          "Only /internal/v1 authorization-service paths are available"));
    }
    String query = exchange.getRequest().getURI().getRawQuery();
    String target = path + (query == null ? "" : "?" + query);
    WebClient.RequestBodySpec request = authorization.method(exchange.getRequest().getMethod())
        .uri(target)
        .headers(headers -> {
          headers.set("X-Actor", identity.username());
          headers.set("X-Actor-Subject", identity.subject());
          headers.set("X-Actor-Issuer", identity.issuer());
          copyHeader(exchange.getRequest().getHeaders(), headers, HttpHeaders.CONTENT_TYPE);
          copyHeader(exchange.getRequest().getHeaders(), headers, HttpHeaders.ACCEPT);
          copyHeader(exchange.getRequest().getHeaders(), headers, "X-Correlation-ID");
        });
    String correlation = exchange.getRequest().getHeaders().getFirst("X-Correlation-ID");
    return requireSwaggerPermission(identity, correlation == null || correlation.isBlank()
        ? UUID.randomUUID().toString() : correlation).then(request
        .body(requestBody.defaultIfEmpty(new byte[0]), byte[].class)
        .exchangeToMono(response -> response.bodyToMono(byte[].class).defaultIfEmpty(new byte[0])
            .map(body -> {
              var builder = ResponseEntity.status(response.statusCode());
              MediaType contentType = response.headers().contentType().orElse(MediaType.APPLICATION_JSON);
              return builder.contentType(contentType).body(body);
            })));
  }

  private Mono<Void> requireSwaggerPermission(SessionIdentity identity, String correlation) {
    Map<String, Object> check = Map.of(
        "subjectId", identity.subject(), "issuer", identity.issuer(),
        "resource", "application:aurevia", "action", "admin",
        "context", Map.of("channel", "swagger"),
        "correlationId", correlation);
    return authorization.post().uri("/internal/v1/authorize/check")
        .contentType(MediaType.APPLICATION_JSON).header("X-Correlation-ID", correlation)
        .bodyValue(check).retrieve().bodyToMono(Map.class)
        .filter(decision -> "ALLOW".equals(decision.get("result")))
        .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.FORBIDDEN,
            "Swagger execution requires admin access to the platform application")))
        .then();
  }

  private static void copyHeader(HttpHeaders source, HttpHeaders target, String name) {
    String value = source.getFirst(name);
    if (value != null && !value.isBlank()) target.set(name, value);
  }
}

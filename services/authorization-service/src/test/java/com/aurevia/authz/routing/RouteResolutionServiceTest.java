package com.aurevia.authz.routing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/**
 * Route selection must be deterministic: prefix length, then priority, then operation
 * specificity. Anything still tied is a configuration conflict, never a coin toss.
 */
class RouteResolutionServiceTest {

  private final List<RouteResolutionRepository.Candidate> candidates = new ArrayList<>();
  private final RouteResolutionService service =
      new RouteResolutionService(method -> candidates.stream()
          .filter(candidate -> candidate.allowedMethods().contains(method)).toList());

  @Test void exactPathResolvesToTheOperationDeclaringIt() {
    candidate("hr", "/api/proxy/hr", 0, "/employees", "GET");
    var route = service.resolve("/api/proxy/hr/employees", "GET");
    assertThat(route.routeKey()).isEqualTo("hr");
    assertThat(route.upstreamPath()).isEqualTo("/employees");
  }

  @Test void prefixRouteMatchesNestedPathsThroughATerminalWildcard() {
    candidate("hr", "/api/proxy/hr", 0, "/files/**", "GET");
    assertThat(service.resolve("/api/proxy/hr/files/a/b/c.txt", "GET").routeKey()).isEqualTo("hr");
    assertThat(service.resolve("/api/proxy/hr/files", "GET").routeKey()).isEqualTo("hr");
  }

  @Test void nestedPatternOnlyMatchesItsOwnDepth() {
    candidate("hr", "/api/proxy/hr", 0, "/employees/{id}", "GET");
    assertThat(service.resolve("/api/proxy/hr/employees/42", "GET").routeKey()).isEqualTo("hr");
    assertThatThrownBy(() -> service.resolve("/api/proxy/hr/employees/42/salary", "GET"))
        .isInstanceOfSatisfying(ResponseStatusException.class,
            error -> assertThat(error.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
  }

  @Test void theLongerPrefixWinsRegardlessOfPriority() {
    candidate("short", "/api/proxy/hr", 100, "/**", "GET");
    candidate("long", "/api/proxy/hr/legacy", 0, "/**", "GET");
    assertThat(service.resolve("/api/proxy/hr/legacy/customer", "GET").routeKey()).isEqualTo("long");
    assertThat(service.resolve("/api/proxy/hr/modern/orders", "GET").routeKey()).isEqualTo("short");
  }

  @Test void samePrefixHigherPriorityWins() {
    candidate("low", "/api/proxy/hr", 1, "/**", "GET");
    candidate("high", "/api/proxy/hr", 5, "/**", "GET");
    assertThat(service.resolve("/api/proxy/hr/anything", "GET").routeKey()).isEqualTo("high");
  }

  @Test void samePrefixAndPriorityMoreSpecificOperationWins() {
    candidate("wild", "/api/proxy/hr", 0, "/**", "GET");
    candidate("typed", "/api/proxy/hr", 0, "/employees/{id}", "GET");
    candidate("literal", "/api/proxy/hr", 0, "/employees/me", "GET");
    assertThat(service.resolve("/api/proxy/hr/employees/me", "GET").routeKey()).isEqualTo("literal");
    assertThat(service.resolve("/api/proxy/hr/employees/42", "GET").routeKey()).isEqualTo("typed");
    assertThat(service.resolve("/api/proxy/hr/other", "GET").routeKey()).isEqualTo("wild");
  }

  @Test void genuineAmbiguityIsReportedAsAConflictNotResolvedArbitrarily() {
    candidate("a", "/api/proxy/hr", 0, "/employees/{id}", "GET");
    candidate("b", "/api/proxy/hr", 0, "/employees/*", "GET");
    assertThatThrownBy(() -> service.resolve("/api/proxy/hr/employees/42", "GET"))
        .isInstanceOfSatisfying(ResponseStatusException.class,
            error -> assertThat(error.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
  }

  @Test void methodMustBeAllowedByTheRouteAndDeclaredByTheOperation() {
    candidate("hr", "/api/proxy/hr", 0, "/employees", "GET,POST");
    assertThat(service.resolve("/api/proxy/hr/employees", "POST").routeKey()).isEqualTo("hr");
    assertThatThrownBy(() -> service.resolve("/api/proxy/hr/employees", "DELETE"))
        .isInstanceOfSatisfying(ResponseStatusException.class,
            error -> assertThat(error.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
  }

  @Test void inactiveCombinationsNeverReachResolutionBecauseTheRepositoryFiltersThem() {
    // The SQL joins require panel, route, target, auth profile, and operation to be active.
    // With no active candidate the outcome is a plain 404.
    assertThatThrownBy(() -> service.resolve("/api/proxy/hr/employees", "GET"))
        .isInstanceOfSatisfying(ResponseStatusException.class,
            error -> assertThat(error.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
  }

  @Test void resolvedRouteCarriesTheCanonicalOpenFgaObjectForEveryResourceType() {
    candidates.add(candidate("page", "/api/proxy/hr", 0, "/employees", "GET", "PAGE", "page:hr.employees").build());
    candidates.add(candidate("app", "/api/proxy/fin", 0, "/ledger", "GET", "APPLICATION", "application:aurevia/finance").build());
    candidates.add(candidate("ext", "/api/proxy/rep", 0, "/d", "GET", "EXTERNAL_RESOURCE",
        "external_resource:superset/operation-default/dashboard/7").build());
    assertThat(service.resolve("/api/proxy/hr/employees", "GET").resourceObject())
        .isEqualTo("resource:page/hr.employees");
    assertThat(service.resolve("/api/proxy/fin/ledger", "GET").resourceObject())
        .isEqualTo("application:aurevia/finance");
    assertThat(service.resolve("/api/proxy/rep/d", "GET").resourceObject())
        .isEqualTo("external_resource:superset/operation-default/dashboard/7");
  }

  @Test void aRewriteThatCannotApplyIsAConfigurationErrorNotASilentPathLoss() {
    candidates.add(candidate("hr", "/api/proxy/hr", 0, "/**", "GET", "PAGE", "page:hr.x")
        .withRewrite("^/legacy", "/svc").build());
    assertThatThrownBy(() -> service.resolve("/api/proxy/hr/employees", "GET"))
        .isInstanceOfSatisfying(ResponseStatusException.class, error -> {
          assertThat(error.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
          assertThat(error.getReason()).contains("REWRITE_PREFIX_NOT_FOUND_AFTER_STRIP");
        });
  }

  @Test void percentEncodedIdentifiersAreMatchedOpaquelyAndForwardedUnchanged() {
    candidate("hr", "/api/proxy/hr", 0, "/employees/{id}", "GET");
    assertThat(service.resolve("/api/proxy/hr/employees/John%20Doe", "GET").upstreamPath())
        .isEqualTo("/employees/John%20Doe");
    assertThatThrownBy(() -> service.resolve("/api/proxy/hr/employees/..%2fetc", "GET"))
        .isInstanceOfSatisfying(ResponseStatusException.class,
            error -> assertThat(error.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
  }

  private void candidate(String key, String prefix, int priority, String pattern, String methods) {
    candidates.add(candidate(key, prefix, priority, pattern, methods, "PAGE", "page:hr.employees").build());
  }

  private static Builder candidate(String key, String prefix, int priority, String pattern,
      String methods, String resourceType, String resourceKey) {
    return new Builder(key, prefix, priority, pattern, methods, resourceType, resourceKey);
  }

  /** Small builder so a test reads as configuration, not as a 34-argument constructor. */
  private static final class Builder {
    private final RouteResolutionRepository.Candidate base;
    Builder(String key, String prefix, int priority, String pattern, String methods,
        String resourceType, String resourceKey) {
      UUID id = UUID.nameUUIDFromBytes(key.getBytes());
      base = new RouteResolutionRepository.Candidate(id, key, prefix,
          RoutePathPolicy.prefix(prefix), UpstreamPathPolicy.segments(RoutePathPolicy.prefix(prefix)),
          null, null, priority, methods, false, 0, id, "hr", id, key + "-target",
          "http://operation-gateway", "/", null, false, id, pattern, id, resourceKey, resourceType,
          "view", true, null, 1024, 1000, 2000, 65536, id, "FORWARD_USER_TOKEN", 1,
          "USER_AUTHORIZATION_HEADER");
    }
    Builder withRewrite(String pattern, String replacement) {
      var b = base;
      return new Builder(new RouteResolutionRepository.Candidate(b.routeId(), b.routeKey(),
          b.pathPrefix(), b.normalizedPrefix(), b.stripPrefix(), pattern, replacement, b.priority(),
          b.allowedMethods(), b.retryEnabled(), b.maxRetries(), b.panelId(), b.panelSlug(),
          b.targetId(), b.targetKey(), b.gatewayBaseUrl(), b.upstreamBasePath(), b.tlsProfileRef(),
          b.preserveHost(), b.operationId(), b.pathPattern(), b.resourceId(), b.resourceKey(),
          b.resourceType(), b.actionKey(), b.authorizationRequired(), b.dataPolicyKey(),
          b.maxBodyBytes(), b.connectTimeoutMs(), b.responseTimeoutMs(), b.maxResponseBytes(),
          b.authProfileId(), b.authMode(), b.authProfileVersion(), b.credentialTransport()));
    }
    private Builder(RouteResolutionRepository.Candidate base) { this.base = base; }
    RouteResolutionRepository.Candidate build() { return base; }
  }
}

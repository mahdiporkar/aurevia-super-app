package com.aurevia.authz.routing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.Test;

/** The documented before/after table for pathPrefix, stripPrefix, rewrite and upstreamBasePath. */
class UpstreamPathPolicyTest {

  @ParameterizedTest(name = "[{index}] {0} strip={1} rewrite={2}->{3} base={4} => {5}")
  @CsvSource(nullValues = "null", value = {
      // incoming, strip, rewritePattern, rewriteReplacement, upstreamBasePath, expected
      "/api/proxy/hr/employees/42, 3, null, null, /hr-api, /hr-api/employees/42",
      "/api/proxy/hr/employees/42, 3, null, null, null,   /employees/42",
      "/api/proxy/hr/employees/42, 3, null, null, /,      /employees/42",
      "/api/proxy/hr/employees/42, 0, null, null, /hr-api, /hr-api/api/proxy/hr/employees/42",
      "/api/proxy/hr/employees/42, 0, ^/api/proxy/hr, /svc/v1, null, /svc/v1/employees/42",
      "/api/proxy/hr/employees/42, 3, ^/employees, /people, null, /people/42",
      "/api/proxy/hr/employees/42, 3, ^/employees, /people, /ignored-when-rewriting, /people/42",
      "/api/proxy/hr,              3, null, null, /hr-api, /hr-api",
      "/api/proxy/hr,              3, null, null, null,   /",
      "/api/proxy/hr/,             3, null, null, /hr-api, /hr-api",
      "/api/proxy/hr/a/b/c,        3, null, null, /base/, /base/a/b/c",
      "/api/proxy/hr/a/b/c,        2, null, null, null,   /hr/a/b/c",
  })
  void producesExactlyTheDocumentedDownstreamPath(String incoming, int strip, String pattern,
      String replacement, String base, String expected) {
    String canonical = RoutePathPolicy.path(incoming);
    assertThat(UpstreamPathPolicy.upstreamPath(
        new UpstreamPathPolicy.Transformation(strip, pattern, replacement, base), canonical))
        .isEqualTo(expected);
  }

  @Test void rewriteThatDoesNotApplyIsRejectedInsteadOfDroppingTheBasePath() {
    assertThatThrownBy(() -> UpstreamPathPolicy.upstreamPath(
        new UpstreamPathPolicy.Transformation(3, "^/legacy", "/svc", "/hr-api"),
        "/api/proxy/hr/employees"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("REWRITE_PREFIX_NOT_FOUND_AFTER_STRIP");
  }

  @Test void prefixSegmentCountBoundsStripPrefix() {
    assertThat(UpstreamPathPolicy.segments("/")).isZero();
    assertThat(UpstreamPathPolicy.segments("/api/")).isEqualTo(1);
    assertThat(UpstreamPathPolicy.segments("/api/proxy/hr/")).isEqualTo(3);
  }
}

package com.aurevia.bff.proxy;

import static org.assertj.core.api.Assertions.*;
import org.junit.jupiter.api.Test;

class RouteNormalizerTest {
  @Test void rejectsTraversalAndAmbiguousEncoding() {
    assertThatThrownBy(() -> RouteNormalizer.normalizePath("/hr/%2f/admin")).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> RouteNormalizer.normalizePath("/hr/../../admin")).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> RouteNormalizer.normalizePath("/hr/%2e%2e/admin")).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> RouteNormalizer.normalizePath("/hr/%252f/admin")).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> RouteNormalizer.normalizePath("/hr/employee\r\nX-Test:value")).isInstanceOf(IllegalArgumentException.class);
  }
  @Test void acceptsCanonicalRelativePath() { assertThat(RouteNormalizer.normalizePath("/hr/api/v1/employees")).isEqualTo("/hr/api/v1/employees"); }
}

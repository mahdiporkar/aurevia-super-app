package com.aurevia.bff.api;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

class OperationSupersetProxyControllerTest {
  @Test
  void keepsCanonicalSupersetDashboardRedirect() {
    assertThat(rewrite("/superset/dashboard/1/?native_filters_key=test"))
        .isEqualTo("/superset/dashboard/1/?native_filters_key=test");
  }

  @Test
  void tunnelsLoginButCanonicalizesItsDashboardNextParameter() {
    assertThat(rewrite("/login/?next=/reports-runtime/superset/dashboard/1/"))
        .isEqualTo("/reports-runtime/login/?next=/superset/dashboard/1/");
  }

  @Test
  void canonicalizesEncodedDashboardNextParameter() {
    assertThat(rewrite("/login/?next=%2Freports-runtime%2Fsuperset%2Fdashboard%2F1%2F"))
        .isEqualTo("/reports-runtime/login/?next=%2Fsuperset%2Fdashboard%2F1%2F");
  }

  private static String rewrite(String upstreamLocation) {
    HttpHeaders source = new HttpHeaders();
    source.setLocation(java.net.URI.create(upstreamLocation));
    HttpHeaders target = new HttpHeaders();
    OperationSupersetProxyController.copyRewrittenLocation(source, target);
    return target.getFirst(HttpHeaders.LOCATION);
  }
}

package com.aurevia.authz.ui;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class UiArtifactPolicyTest {
  @Test void dynamicOriginDoesNotRequireEnvironmentAllowlist() {
    var policy=new UiArtifactPolicy("PRODUCTION_INTERNET",false,false,"","");
    assertThat(policy.validate(
        "https://example-mfe.company.com/remoteEntry.js",null))
        .isEqualTo("https://example-mfe.company.com/remoteEntry.js");
  }

  @Test void productionStillRequiresHttpsAndSriWhenConfigured() {
    var policy=new UiArtifactPolicy("PRODUCTION_INTERNET",false,true,"","");
    assertThatThrownBy(()->policy.validate(
        "http://example-mfe.company.com/remoteEntry.js","sha384-YWJjZA=="))
        .hasMessageContaining("HTTPS");
    assertThatThrownBy(()->policy.validate(
        "https://example-mfe.company.com/remoteEntry.js",null))
        .hasMessageContaining("SRI");
  }
}

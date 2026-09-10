package com.aurevia.bff.proxy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class SupersetTargetPolicyTest {
  @Test void resolvesRegistryBasePathWithoutAHostAllowlist() {
    var policy=new SupersetTargetPolicy("DEVELOPMENT",true,"","");
    assertThat(policy.resolve("http://localhost:8088/bi",false,
        "/api/v1/dashboard/1","q=test").toString())
        .isEqualTo("http://localhost:8088/bi/api/v1/dashboard/1?q=test");
  }

  @Test void productionRejectsLoopbackAndCloudMetadataTargets() {
    var policy=new SupersetTargetPolicy("PRODUCTION_INTERNET",false,"","");
    assertThatThrownBy(()->policy.validate("https://127.0.0.1",true))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(()->policy.validate("https://169.254.169.254",true))
        .isInstanceOf(IllegalArgumentException.class);
  }
}

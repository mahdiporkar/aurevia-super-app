package com.aurevia.authz.authorization;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;

class DemoDataPolicyTest {
  @Test void productionPolicyExcludesDemoButKeepsRealRegistrations() {
    DemoDataPolicy policy=new DemoDataPolicy(false);
    assertThat(policy.allows("DEMO")).isFalse();
    assertThat(policy.allows("REAL")).isTrue();
  }

  @Test void localPolicyAllowsDemoRegistrations() {
    assertThat(new DemoDataPolicy(true).allows("DEMO")).isTrue();
  }
}

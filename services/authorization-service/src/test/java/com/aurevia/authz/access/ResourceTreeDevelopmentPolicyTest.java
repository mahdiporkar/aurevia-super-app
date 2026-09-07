package com.aurevia.authz.access;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class ResourceTreeDevelopmentPolicyTest {
  @Test void requiresBothDevProfileAndExplicitFlag() {
    assertThat(new ResourceTreeDevelopmentPolicy(
        new MockEnvironment().withProperty("spring.profiles.active","dev"),true).enabled()).isTrue();
    assertThat(new ResourceTreeDevelopmentPolicy(
        new MockEnvironment().withProperty("spring.profiles.active","dev"),false).enabled()).isFalse();
    assertThat(new ResourceTreeDevelopmentPolicy(
        new MockEnvironment().withProperty("spring.profiles.active","prod"),true).enabled()).isFalse();
  }
}

package com.aurevia.bff.observability;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class DevelopmentTokenEvidenceLoggerTest {
  @Test void isDisabledUnlessExplicitlyEnabled() {
    assertThat(new DevelopmentTokenEvidenceLogger(new MockEnvironment(),false).enabled()).isFalse();
  }

  @Test void canBeEnabledForDevelopment() {
    assertThat(new DevelopmentTokenEvidenceLogger(new MockEnvironment(),true).enabled()).isTrue();
  }

  @Test void productionProfileAlwaysSuppressesEvidenceEvenWhenConfigured() {
    MockEnvironment production=new MockEnvironment();
    production.setActiveProfiles("prod");
    assertThat(new DevelopmentTokenEvidenceLogger(production,true).enabled()).isFalse();
  }
}

package com.aurevia.bff.api;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class SupersetWebClientConfigurationTest {
  private final ApplicationContextRunner context = new ApplicationContextRunner()
      .withUserConfiguration(SupersetWebClientConfiguration.class);

  @Test
  void defaultConnectorDoesNotRequirePrivateCertificates() {
    context.run(application -> assertThat(application).hasBean("supersetWebClient"));
  }

  @ParameterizedTest @ValueSource(strings = {
      "aurevia.superset.tls.client-cert-file=missing-client.pem",
      "aurevia.superset.tls.client-key-file=missing-client-key.pem"
  })
  void partialClientIdentityFailsClosed(String property) {
    context.withPropertyValues(property).run(application -> assertThat(application).hasFailed());
  }

  @Test
  void requiredMtlsWithoutIdentityFailsClosed() {
    context.withPropertyValues("aurevia.superset.tls.require-mtls=true")
        .run(application -> assertThat(application).hasFailed());
  }

  @Test
  void requiredMtlsDoesNotAllowPlainHttp() {
    context.withPropertyValues("aurevia.superset.tls.require-mtls=true", "aurevia.superset.allow-http=true",
        "aurevia.superset.tls.ca-cert-file=ca.pem", "aurevia.superset.tls.client-cert-file=client.pem",
        "aurevia.superset.tls.client-key-file=key.pem")
        .run(application -> assertThat(application.getStartupFailure())
            .hasRootCauseMessage("Superset mTLS requires an explicit CA, client identity, and HTTP disabled"));
  }

  @Test
  void unreadableTrustMaterialDoesNotFallBackToInsecureTls() {
    context.withPropertyValues("aurevia.superset.tls.ca-cert-file=missing-aurevia-demo-ca.pem")
        .run(application -> assertThat(application).hasFailed());
  }
}

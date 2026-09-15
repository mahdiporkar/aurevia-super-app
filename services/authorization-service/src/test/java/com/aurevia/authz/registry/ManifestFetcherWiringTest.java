package com.aurevia.authz.registry;

import com.aurevia.authz.ui.UiArtifactPolicy;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class ManifestFetcherWiringTest {
  @Test void oneBoundedAdapterSatisfiesBothManifestPorts() {
    new ApplicationContextRunner().withBean(UiArtifactPolicy.class,()->mock(UiArtifactPolicy.class))
      .withUserConfiguration(HttpManifestFetcher.class).run(context->{
        assertThat(context).hasNotFailed().hasSingleBean(ManifestFetcher.class)
          .hasSingleBean(ResourceManifestFetcher.class);
        assertThat(context.getBean(ManifestFetcher.class))
          .isSameAs(context.getBean(ResourceManifestFetcher.class));
      });
  }
}

package com.aurevia.bff.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.Test;

class MicroFrontendArtifactTargetResolverTest {
  @Test void mapsAnyLoopbackPortToOneDevelopmentHost() {
    var resolver=new MicroFrontendArtifactTargetResolver("DEVELOPMENT",true,"localhost","");
    assertThat(resolver.resolve("http://localhost:3001/remoteEntry.js").toString())
        .isEqualTo("http://localhost:3001/remoteEntry.js");
    assertThat(resolver.resolve("http://127.0.0.1:3002/assets/chunk.js").toString())
        .isEqualTo("http://localhost:3002/assets/chunk.js");
    assertThat(resolver.resolve("http://localhost:3999/remoteEntry.js").toString())
        .isEqualTo("http://localhost:3999/remoteEntry.js");
    assertThat(resolver.resolveAsset("http://localhost:3001/remoteEntry.js","925.js").toString())
        .isEqualTo("http://localhost:3001/925.js");
  }

  @Test void productionProxyRejectsLoopbackSsrfTarget() {
    var resolver=new MicroFrontendArtifactTargetResolver("PRODUCTION_INTERNET",true,"","");
    assertThatThrownBy(()->resolver.resolveAsset(
        "http://127.0.0.1:8080/remoteEntry.js","remoteEntry.js"))
        .hasMessageContaining("loopback");
  }
}

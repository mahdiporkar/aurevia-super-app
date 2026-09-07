package com.aurevia.bff.api;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;

class MicroFrontendArtifactTargetResolverTest {
  @Test void mapsOnlyExplicitLoopbackPortsToDockerNetworkTargets() {
    var resolver=new MicroFrontendArtifactTargetResolver(
        "3001=http://mfe-admin:8080,3002=http://mfe-hr:8080");
    assertThat(resolver.resolve("http://localhost:3001/remoteEntry.js").toString())
        .isEqualTo("http://mfe-admin:8080/remoteEntry.js");
    assertThat(resolver.resolve("http://127.0.0.1:3002/assets/chunk.js").toString())
        .isEqualTo("http://mfe-hr:8080/assets/chunk.js");
    assertThat(resolver.resolve("https://cdn.example/mfe/remoteEntry.js").toString())
        .isEqualTo("https://cdn.example/mfe/remoteEntry.js");
    assertThat(resolver.resolve("http://localhost:3999/remoteEntry.js").toString())
        .isEqualTo("http://localhost:3999/remoteEntry.js");
    assertThat(resolver.resolveAsset("http://localhost:3001/remoteEntry.js","925.js").toString())
        .isEqualTo("http://mfe-admin:8080/925.js");
  }
}

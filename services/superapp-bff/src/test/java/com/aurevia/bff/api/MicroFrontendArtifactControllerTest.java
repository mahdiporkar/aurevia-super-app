package com.aurevia.bff.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import com.aurevia.bff.security.SessionIdentity;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class MicroFrontendArtifactControllerTest {
  @Test void exposesManifestOnlyForModuleInEffectiveUserCatalog() {
    AuthorizationServiceClient authorization=mock(AuthorizationServiceClient.class);
    Map<String,Object> module=Map.of("moduleKey","hr","routes",List.of(),
        "remote",Map.of("remoteEntryUrl","https://private.example/remoteEntry.js"));
    when(authorization.manifest("issuer","user-1")).thenReturn(Mono.just(Map.of(
        "uiCatalog",Map.of("modules",List.of(module)))));
    var controller=new MicroFrontendArtifactController(authorization,WebClient.builder(),
        new MicroFrontendArtifactTargetResolver(""));
    var principal=new SessionIdentity("issuer","user-1","operator");

    StepVerifier.create(controller.manifest("hr",principal)).assertNext(response->
        assertThat(response.getBody()).isSameAs(module)).verifyComplete();
    StepVerifier.create(controller.manifest("finance",principal)).expectErrorSatisfies(error->
        assertThat(error).hasMessageContaining("404")).verify();
  }
}

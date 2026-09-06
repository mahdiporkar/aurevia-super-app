package com.aurevia.bff.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.aurevia.bff.security.SessionIdentity;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.CacheControl;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class MeControllerTest {
  @Test void manifestPassesThroughEffectiveCatalogWithoutRebuildingAuthorization() {
    AuthorizationServiceClient authorization=mock(AuthorizationServiceClient.class);
    Map<String,Object> catalog=Map.of("catalogVersion","manifest-1","contractVersion","1.0",
        "modules",List.of(Map.of("moduleKey","admin","routePrefix","management")));
    Map<String,Object> body=Map.of("version","manifest-1","uiCatalog",catalog,
        "panels",List.of(),"permissions",Map.of());
    when(authorization.manifest("https://issuer.example","subject-1"))
        .thenReturn(Mono.just(body));

    var result=new MeController(authorization).manifest(
        new SessionIdentity("https://issuer.example","subject-1","operator"));

    StepVerifier.create(result).assertNext(response->{
      assertThat(response.getBody()).isSameAs(body);
      assertThat(response.getHeaders().getETag()).isEqualTo("\"manifest-1\"");
      assertThat(response.getHeaders().getCacheControl())
          .isEqualTo(CacheControl.noCache().cachePrivate().getHeaderValue());
    }).verifyComplete();
  }

  @Test void uiCatalogReturnsOnlyEffectiveCatalogAtStableContractPath() {
    AuthorizationServiceClient authorization=mock(AuthorizationServiceClient.class);
    Map<String,Object> catalog=Map.of("catalogVersion","catalog-7","contractVersion","1.0",
        "modules",List.of(Map.of("moduleKey","hr","navigation",List.of())));
    when(authorization.manifest("https://issuer.example","subject-1"))
        .thenReturn(Mono.just(Map.of("version","manifest-7","uiCatalog",catalog)));

    var result=new MeController(authorization).uiCatalog(
        new SessionIdentity("https://issuer.example","subject-1","operator"));

    StepVerifier.create(result).assertNext(response->{
      assertThat(response.getBody()).isSameAs(catalog);
      assertThat(response.getHeaders().getETag()).isEqualTo("\"catalog-7\"");
      assertThat(response.getHeaders().getCacheControl())
          .isEqualTo(CacheControl.noCache().cachePrivate().getHeaderValue());
    }).verifyComplete();
  }
}

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

  @Test void contextIsSingleEffectiveContractAndRewritesArtifactsThroughProxy() {
    AuthorizationServiceClient authorization=mock(AuthorizationServiceClient.class);
    Map<String,Object> module=Map.of("moduleKey","finance",
        "remote",Map.of("remoteEntryUrl","https://private.example/remoteEntry.js"),
        "routes",List.of(Map.of("id","invoices","resource","finance.invoice")),
        "navigation",List.of(Map.of("key","invoices")));
    Map<String,Object> body=Map.of("version","manifest-9","expiresAt","2099-01-01T00:00:00Z",
        "uiCatalog",Map.of("catalogVersion","manifest-9","modules",List.of(module)),
        "permissions",Map.of("finance.invoice",List.of("view")),"resourceTree",List.of());
    when(authorization.manifest("https://issuer.example","subject-1")).thenReturn(Mono.just(body));
    when(authorization.supersetIntegrations("https://issuer.example","subject-1"))
        .thenReturn(Mono.just(List.of(Map.of("key","superset-public",
            "url","/api/integrations/superset/superset-public/"))));

    var result=new MeController(authorization).context(
        new SessionIdentity("https://issuer.example","subject-1","operator"));

    StepVerifier.create(result).assertNext(response->{
      Map<String,Object> context=response.getBody();
      assertThat(context).isNotNull();
      assertThat(context.get("allowedApplications")).isEqualTo(
          List.of("finance","superset-public"));
      assertThat(context.get("applications")).isEqualTo(List.of(Map.of(
          "key","superset-public","url","/api/integrations/superset/superset-public/")));
      assertThat(context.get("actions")).isEqualTo(body.get("permissions"));
      @SuppressWarnings("unchecked") Map<String,Object> catalog=(Map<String,Object>)context.get("uiCatalog");
      @SuppressWarnings("unchecked") Map<String,Object> effective=(Map<String,Object>)((List<?>)catalog.get("modules")).getFirst();
      @SuppressWarnings("unchecked") Map<String,Object> remote=(Map<String,Object>)effective.get("remote");
      assertThat(remote.get("remoteEntryUrl")).isEqualTo("/api/mfe/finance/remoteEntry.js");
      assertThat(effective.get("manifestUrl")).isEqualTo("/api/mfe/finance/manifest.json");
    }).verifyComplete();
  }
}

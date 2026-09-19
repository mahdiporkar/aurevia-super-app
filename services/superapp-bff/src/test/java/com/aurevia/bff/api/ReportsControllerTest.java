package com.aurevia.bff.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.aurevia.bff.security.SessionIdentity;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class ReportsControllerTest {
  private static final SessionIdentity IDENTITY=new SessionIdentity(
      "https://issuer.example","subject-1","alice");

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings={" "})
  void listsAuthorizedAssetsAcrossAllVisibleMappingsWhenInstanceIsNotSelected(String instance) {
    var authorization=mock(AuthorizationServiceClient.class);
    when(authorization.supersetIntegrations(IDENTITY.issuer(),IDENTITY.subject()))
        .thenReturn(Mono.just(List.of(Map.of("key","public-default"),
            Map.of("key","public-finance"))));
    mapping(authorization,"public-default","operation-default",List.of(Map.of(
        "id","asset-1","external_id","42","url_path","/superset/dashboard/42/")));
    mapping(authorization,"public-finance","operation-finance",List.of(Map.of(
        "id","asset-2","external_id","42","url_path","/superset/dashboard/42/")));

    StepVerifier.create(new ReportsController(authorization).reports(IDENTITY,instance))
        .assertNext(assets->assertThat(assets).extracting(asset->asset.get("url_path"))
            .containsExactly(
                "/api/v1/superset-instances/public-default/superset/dashboard/42/",
                "/api/v1/superset-instances/public-finance/superset/dashboard/42/"))
        .verifyComplete();

    verify(authorization,never()).resolveSupersetProxy(null);
  }

  @Test void keepsAnExplicitInstanceScopedToItsOwnOperation() {
    var authorization=mock(AuthorizationServiceClient.class);
    mapping(authorization,"public-finance","operation-finance",List.of(Map.of(
        "id","asset-2","url_path","/superset/dashboard/84/")));

    StepVerifier.create(new ReportsController(authorization).reports(IDENTITY,"public-finance"))
        .assertNext(assets->assertThat(assets).singleElement().satisfies(asset->
            assertThat(asset.get("url_path")).isEqualTo(
                "/api/v1/superset-instances/public-finance/superset/dashboard/84/")))
        .verifyComplete();

    verify(authorization,never()).supersetIntegrations(IDENTITY.issuer(),IDENTITY.subject());
    verify(authorization,never()).resolveSupersetProxy(null);
  }

  @Test void deduplicatesAnAssetExposedThroughMultiplePublicMappings() {
    var authorization=mock(AuthorizationServiceClient.class);
    when(authorization.supersetIntegrations(IDENTITY.issuer(),IDENTITY.subject()))
        .thenReturn(Mono.just(List.of(Map.of("key","public-default"),
            Map.of("key","public-alias"),Map.of("key","public-default"))));
    List<Map> assets=List.of(Map.of("id","asset-1","url_path","/superset/dashboard/42/"));
    mapping(authorization,"public-default","operation-default",assets);
    mapping(authorization,"public-alias","operation-default",assets);

    StepVerifier.create(new ReportsController(authorization).reports(IDENTITY,null))
        .assertNext(result->assertThat(result).singleElement().satisfies(asset->
            assertThat(asset.get("url_path")).isEqualTo(
                "/api/v1/superset-instances/public-default/superset/dashboard/42/")))
        .verifyComplete();

    verify(authorization).resolveSupersetProxy("public-default");
  }

  @Test void returnsAnEmptyCatalogWhenNoIntegrationsAreAuthorized() {
    var authorization=mock(AuthorizationServiceClient.class);
    when(authorization.supersetIntegrations(IDENTITY.issuer(),IDENTITY.subject()))
        .thenReturn(Mono.just(List.of()));

    StepVerifier.create(new ReportsController(authorization).reports(IDENTITY,null))
        .assertNext(assets->assertThat(assets).isEmpty()).verifyComplete();

    verify(authorization,never()).resolveSupersetProxy(null);
  }

  @Test void propagatesAnAssetAuthorizationFailureInsteadOfUsingUnfilteredReports() {
    var authorization=mock(AuthorizationServiceClient.class);
    when(authorization.supersetIntegrations(IDENTITY.issuer(),IDENTITY.subject()))
        .thenReturn(Mono.just(List.of(Map.of("key","public-default"))));
    mapping(authorization,"public-default","operation-default",List.of());
    var failure=new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
        "Authorization unavailable");
    when(authorization.supersetAssets(IDENTITY.issuer(),IDENTITY.subject(),"operation-default"))
        .thenReturn(Mono.error(failure));

    StepVerifier.create(new ReportsController(authorization).reports(IDENTITY,null))
        .expectErrorMatches(error->error==failure).verify();
  }

  @Test void rejectsAnInvalidRequestedMappingBeforeContactingAuthorization() {
    var authorization=mock(AuthorizationServiceClient.class);

    StepVerifier.create(new ReportsController(authorization).reports(IDENTITY,"../public-default"))
        .expectErrorSatisfies(error->assertThat(error).isInstanceOfSatisfying(
            ResponseStatusException.class,exception->
                assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST)))
        .verify();

    verifyNoInteractions(authorization);
  }

  @Test void rejectsAnInvalidMappingFromAuthorizationWithoutBindingAssetUrls() {
    var authorization=mock(AuthorizationServiceClient.class);
    when(authorization.resolveSupersetProxy("public-default")).thenReturn(Mono.just(Map.of(
        "public_code","../public-finance","operation_code","operation-default")));
    StepVerifier.create(new ReportsController(authorization).reports(IDENTITY,"public-default"))
        .expectErrorSatisfies(error->assertThat(error).isInstanceOfSatisfying(
            ResponseStatusException.class,exception->
                assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY)))
        .verify();
  }

  @Test void bindsEveryAuthorizedReportToItsLogicalPublicOperationMapping() {
    List<Map> result=ReportsController.bindToMapping(List.of(Map.of(
        "id","asset-1","url_path","/superset/dashboard/42/")),"public-default");

    assertThat(result).singleElement().satisfies(asset->assertThat(asset.get("url_path"))
        .isEqualTo("/api/v1/superset-instances/public-default/superset/dashboard/42/"));
  }

  @ParameterizedTest
  @ValueSource(strings={"https://outside.example/dashboard/42","//outside.example/dashboard/42"})
  void doesNotTurnAnInvalidCatalogUrlIntoATunnelUrl(String url) {
    List<Map> result=ReportsController.bindToMapping(List.of(Map.of(
        "id","asset-1","url_path",url)),"public-default");

    assertThat(result).singleElement().satisfies(asset->assertThat(asset.get("url_path"))
        .isEqualTo(url));
  }

  private static void mapping(AuthorizationServiceClient authorization,String publicCode,
      String operationCode,List<Map> assets) {
    when(authorization.resolveSupersetProxy(publicCode)).thenReturn(Mono.just(Map.of(
        "public_code",publicCode,"operation_code",operationCode)));
    when(authorization.supersetAssets(IDENTITY.issuer(),IDENTITY.subject(),operationCode))
        .thenReturn(Mono.just(assets));
  }
}

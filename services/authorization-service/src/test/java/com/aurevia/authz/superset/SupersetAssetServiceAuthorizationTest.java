package com.aurevia.authz.superset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.aurevia.authz.access.AccessAdministrationService;
import com.aurevia.authz.observability.AuditTrail;
import com.aurevia.authz.identity.CanonicalIdentityResolver;
import com.aurevia.authz.openfga.RelationshipAuthorizationPort;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.CsvSource;
import java.util.List;
import java.util.UUID;

class SupersetAssetServiceAuthorizationTest {
  @ParameterizedTest
  @ValueSource(strings={"/api/v1/dashboard/42/charts", "/api/v1/dashboard/42/datasets/"})
  void permitsDependenciesOfTheGrantedDashboard(String path) {
    assertThat(viewer().accessForSubject("https://issuer.example","subject-1",
        "superset-operation","superset-operation",path,"GET","","","").result()).isEqualTo("ALLOW");
  }

  @ParameterizedTest
  @CsvSource({"/api/v1/dashboard/99/charts,GET", "/api/v1/dashboard/99/datasets,GET",
      "/api/v1/dashboard/42/charts,POST", "/api/v1/dashboard/42/datasets,PATCH",
      "/api/v1/dashboard/42/delete,GET"})
  void doesNotExtendDependencyPermissionToOtherDashboardsOrMutations(String path,String method) {
    assertThat(viewer().accessForSubject("https://issuer.example","subject-1",
        "superset-operation","superset-operation",path,method,"","","").result()).isEqualTo("DENY");
  }

  private SupersetAssetService viewer() {
    return viewer("DASHBOARD");
  }

  private SupersetAssetService viewer(String type) {
    var integrations=mock(SupersetInstanceService.class);
    when(integrations.canAccess("https://issuer.example","subject-1","superset-operation",
        "superset-operation")).thenReturn(true);
    var identities=mock(CanonicalIdentityResolver.class);
    when(identities.openFgaUser("https://issuer.example","subject-1")).thenReturn("user:canonical");
    var relationships=mock(RelationshipAuthorizationPort.class);
    when(relationships.check("user:canonical","can_view",
        "external_resource:superset/superset-operation/"+type.toLowerCase()+"/42")).thenReturn(true);
    var repository=mock(SupersetAssetRepository.class);
    when(repository.publishedAssets("superset-operation")).thenReturn(List.of(new SupersetAssetModels.AssetView(
        UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),"superset-operation","42",type,
        "Demo","/superset/dashboard/42",null,true,List.of(),
        "external_resource:superset/superset-operation/"+type.toLowerCase()+"/42","Demo","Demo")));
    return new SupersetAssetService(repository,relationships,mock(AccessAdministrationService.class),
        mock(AuditTrail.class),integrations,identities);
  }

  @ParameterizedTest
  @CsvSource({
      "/api/v1/dashboard/favorite_status/,GET,q=!(42),ALLOW",
      "/api/v1/dashboard/favorite_status,GET,q=%21%2842%29,ALLOW",
      "/api/v1/dashboard/favorite_status/,GET,'q=!(42,99)',DENY",
      "/api/v1/chart/favorite_status/,GET,q=!(42),DENY",
      "/api/v1/dashboard/favorite_status/,GET,q=!(),DENY",
      "/api/v1/dashboard/favorite_status/,GET,q=%broken,DENY",
      "/api/v1/dashboard/favorite_status/,POST,q=!(42),DENY"
  })
  void favoriteStatusIsReadOnlyAndRequiresEveryRequestedAsset(String path,String method,String query,String expected) {
    assertThat(viewer().accessForSubject("https://issuer.example","subject-1",
        "superset-operation","superset-operation",path,method,query,"","").result()).isEqualTo(expected);
  }

  @Test void deniesEveryRuntimePathWhenIntegrationPermissionIsMissing() {
    SupersetInstanceService integrations=mock(SupersetInstanceService.class);
    when(integrations.canAccess("https://issuer.example","subject-1","superset-operation",
        "superset-operation")).thenReturn(false);
    var service=new SupersetAssetService(mock(SupersetAssetRepository.class),
        mock(RelationshipAuthorizationPort.class),mock(AccessAdministrationService.class),
        mock(AuditTrail.class),integrations,mock(CanonicalIdentityResolver.class));

    var decision=service.accessForSubject("https://issuer.example","subject-1",
        "superset-operation","superset-operation","/superset/dashboard/42/","GET","",
        "DASHBOARD","42");

    assertThat(decision.result()).isEqualTo("DENY");
    assertThat(decision.reasonCode()).isEqualTo("SUPERSET_INTEGRATION_DENIED");
  }

  @ParameterizedTest
  @CsvSource({
      "/api/v1/explore/form_data,POST,CHART,42,ALLOW",
      "/api/v1/explore/form_data/,POST,CHART,42,ALLOW",
      "/api/v1/explore/form_data,POST,CHART,99,DENY",
      "/api/v1/explore/form_data,POST,DASHBOARD,42,DENY",
      "/api/v1/explore/form_data,POST,'','',DENY",
      "/api/v1/explore/form_data,PUT,CHART,42,DENY",
      "/api/v1/explore/form_data/other,POST,CHART,42,DENY",
      "/api/v1/chart/42,PUT,CHART,42,DENY"
  })
  void exploreFormCacheRequiresAnExplicitGrantedChartAndDoesNotEnableWrites(
      String path,String method,String type,String id,String expected) {
    assertThat(viewer("CHART").accessForSubject("https://issuer.example","subject-1",
        "superset-operation","superset-operation",path,method,"",type,id).result()).isEqualTo(expected);
  }
}

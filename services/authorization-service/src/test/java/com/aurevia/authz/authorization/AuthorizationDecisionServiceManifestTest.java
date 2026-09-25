package com.aurevia.authz.authorization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aurevia.authz.audit.AuthorizationDecisionAuditor;
import com.aurevia.authz.api.dto.AuthorizationDtos.CheckRequest;
import com.aurevia.authz.identity.CanonicalIdentityResolver;
import com.aurevia.authz.openfga.RelationshipAuthorizationPort;
import com.aurevia.authz.openfga.RelationshipAuthorizationPort.RelationshipCheck;
import com.aurevia.authz.policy.RuntimePolicyService;
import com.aurevia.authz.semantics.AuthorizationSemanticsRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AuthorizationDecisionServiceManifestTest {
  private static final String SUPERSET_ROOT_KEY="external_resource:superset-public";
  private static final UUID SUPERSET_ROOT_ID=
      UUID.fromString("33333333-3333-3333-3333-333333333333");
  private final RelationshipAuthorizationPort relationships=mock(RelationshipAuthorizationPort.class);
  private final AuthorizationQueryRepository queries=mock(AuthorizationQueryRepository.class);
  private AuthorizationDecisionService service;

  @BeforeEach void setUp() {
    CanonicalIdentityResolver identities=mock(CanonicalIdentityResolver.class);
    when(identities.openFgaUser(anyString(),anyString())).thenReturn("user:usr_canonical");
    service=new AuthorizationDecisionService(relationships,queries,
        new AuthorizationSemanticsRegistry(),mock(RuntimePolicyService.class),
        mock(AuthorizationDecisionAuditor.class),new ObjectMapper(),identities);
    when(queries.activeResources()).thenReturn(List.of());
    when(queries.menuOverrides(org.mockito.ArgumentMatchers.any())).thenReturn(List.of());
    when(relationships.checkBatch(anyList())).thenReturn(Map.of());
  }

  @Test void unauthorizedMicrofrontendIsAbsentAndEmptyAuthorizationReturnsEmptyCatalog() {
    when(queries.activePanels()).thenReturn(List.of(panel()));
    when(queries.permissionCandidates()).thenReturn(List.of());
    when(relationships.check(anyString(),eq("can_view"),eq("application:aurevia/admin")))
        .thenReturn(false);

    var manifest=service.manifest("user-1","https://issuer.example");

    assertThat(manifest.uiCatalog().modules()).isEmpty();
    assertThat(manifest.panels()).isEmpty();
  }

  @Test void authorizedModuleContainsOnlyAuthorizedRelativePagesAndRegisteredMetadata() {
    when(queries.activePanels()).thenReturn(List.of(panel()));
    when(queries.permissionCandidates()).thenReturn(List.of(
        new AuthorizationQueryRepository.PermissionCandidate("page:admin.allowed","PAGE","view"),
        new AuthorizationQueryRepository.PermissionCandidate("page:admin.denied","PAGE","view")));
    when(relationships.check(anyString(),eq("can_view"),eq("application:aurevia/admin")))
        .thenReturn(true);
    allowBatchObjects(Set.of("resource:page/admin.allowed"));

    var manifest=service.manifest("user-1","https://issuer.example");

    assertThat(manifest.uiCatalog().modules()).hasSize(1);
    var module=manifest.uiCatalog().modules().getFirst();
    assertThat(module.moduleKey()).isEqualTo("admin");
    assertThat(module.routePrefix()).isEqualTo("management");
    assertThat(module.icon()).isEqualTo("control");
    assertThat(module.routes()).extracting(route->route.id()).containsExactly("allowed");
    assertThat(module.routes().getFirst().path()).isEqualTo("resources");
    assertThat(module.routes().getFirst().path()).doesNotStartWith("/");
    assertThat(module.menus()).extracting(menu->menu.routeId()).containsExactly("allowed");
    assertThat(module.defaultRouteId()).isEqualTo("allowed");
    assertThat(module.runtime().apiBasePath()).isEqualTo("/api/v1/admin");
    assertThat(module.remote().remoteEntryUrl())
        .isEqualTo("https://static.example.test/admin/remoteEntry.js");
    assertThat(module.remote().remoteName()).isEqualTo("aurevia_admin");
    assertThat(module.remote().exposedModule()).isEqualTo("./bootstrap");
    assertThat(module.remote().contractVersion()).isEqualTo("1.0");
    assertThat(module.remote().artifactVersion()).isEqualTo("0.2.0");
  }

  @Test void panelWithNoAuthorizedPageIsNotAnEffectiveModule() {
    when(queries.activePanels()).thenReturn(List.of(panel()));
    when(queries.permissionCandidates()).thenReturn(List.of(
        new AuthorizationQueryRepository.PermissionCandidate("page:admin.allowed","PAGE","view"),
        new AuthorizationQueryRepository.PermissionCandidate("page:admin.denied","PAGE","view")));
    when(relationships.check(anyString(),eq("can_view"),eq("application:aurevia/admin")))
        .thenReturn(true);
    allowBatchObjects(Set.of());

    var manifest=service.manifest("user-1","https://issuer.example");

    assertThat(manifest.panels()).hasSize(1);
    assertThat(manifest.uiCatalog().modules()).isEmpty();
  }

  @Test void manifestPageGrantExposesItsMicrofrontendWithoutApplicationGrant() {
    when(queries.activePanels()).thenReturn(List.of(panel()));
    when(queries.permissionCandidates()).thenReturn(List.of(
        new AuthorizationQueryRepository.PermissionCandidate("page:admin.allowed","PAGE","view")));
    when(relationships.check(anyString(),eq("can_view"),eq("application:aurevia/admin")))
        .thenReturn(false);
    allowBatchObjects(Set.of("resource:page/admin.allowed"));

    var manifest=service.manifest("user-1","https://issuer.example");

    assertThat(manifest.uiCatalog().modules()).extracting(module->module.moduleKey())
        .containsExactly("admin");
    assertThat(manifest.panels()).extracting(panel->panel.slug()).containsExactly("admin");
  }

  @Test void disabledOrDemoResourceIsDeniedBeforeOpenFgaIsCalled() {
    when(queries.runtimeResourceActionEnabled("resource:page/hr.employees","view"))
        .thenReturn(Optional.of(false));

    var result=service.check(new CheckRequest("user-1","https://issuer.example",
        "resource:page/hr.employees","view",Map.of(),"correlation-1"));

    assertThat(result.decision().result()).isEqualTo("DENY");
    assertThat(result.decision().reasonCode()).isEqualTo("RESOURCE_DISABLED");
    verify(relationships,never()).check(anyString(),anyString(),anyString());
  }

  @Test void leafGrantIncludesOnlyNavigationAncestorsWithoutAuthorizingThemOrOtherResources() {
    UUID app=UUID.randomUUID(),module=UUID.randomUUID(),page=UUID.randomUUID();
    var catalog=List.of(
        new AuthorizationQueryRepository.ResourceRecord(app,null,"application:aurevia/admin",
            "APPLICATION","Admin","Admin","admin","REAL"),
        new AuthorizationQueryRepository.ResourceRecord(module,app,"module:admin",
            "MODULE","Module","Module","admin","REAL"),
        new AuthorizationQueryRepository.ResourceRecord(page,module,"page:admin.allowed",
            "PAGE","Allowed","Allowed","admin","REAL"),
        new AuthorizationQueryRepository.ResourceRecord(UUID.randomUUID(),module,"page:admin.denied",
            "PAGE","Denied","Denied","admin","REAL"),
        new AuthorizationQueryRepository.ResourceRecord(UUID.randomUUID(),page,"component:salary",
            "UI_COMPONENT","Salary","Salary","admin","REAL"));
    when(queries.activePanels()).thenReturn(List.of(panel()));
    when(queries.activeResources()).thenReturn(catalog);
    when(queries.permissionCandidates()).thenReturn(catalog.stream().map(resource->
        new AuthorizationQueryRepository.PermissionCandidate(resource.resourceKey(),resource.type(),"view"))
        .toList());
    allowBatchObjects(Set.of("resource:page/admin.allowed"));

    var manifest=service.manifest("user-1","https://issuer.example");

    assertThat(manifest.permissions()).containsOnlyKeys("page:admin.allowed")
        .containsEntry("page:admin.allowed",List.of("view"));
    assertThat(manifest.resourceTree()).extracting(node->node.resourceKey())
        .containsExactly("application:aurevia/admin","module:admin","page:admin.allowed");
    assertThat(manifest.resourceTree()).filteredOn(node->!node.resourceKey().equals("page:admin.allowed"))
        .allSatisfy(node->assertThat(node.actions()).isEmpty());
    assertThat(manifest.panels()).extracting(value->value.slug()).containsExactly("admin");
    assertThat(manifest.uiCatalog().modules().getFirst().routes())
        .extracting(route->route.id()).containsExactly("allowed");
    for(String object:List.of("application:aurevia/admin","resource:module/admin",
        "resource:page/admin.denied","resource:component/salary")) {
      assertThat(service.check(new CheckRequest("user-1","https://issuer.example",object,"view",
          Map.of(),"navigation-only")).decision().result()).isEqualTo("DENY");
    }
  }

  @Test void supersetAssetGrantExposesOnlyReportsLandingWithoutApplicationPermission() {
    String asset="external_resource:superset/operation-east/dashboard/7";
    when(queries.activePanels()).thenReturn(List.of(reportsPanel("reports")));
    when(queries.activeResources()).thenReturn(supersetCatalog(asset));
    when(queries.permissionCandidates()).thenReturn(List.of(
        new AuthorizationQueryRepository.PermissionCandidate(asset,"EXTERNAL_RESOURCE","view"),
        new AuthorizationQueryRepository.PermissionCandidate(
            "application:aurevia/reports","APPLICATION","view")));
    allowBatchObjects(Set.of(asset));

    var manifest=service.manifest("user-1","https://issuer.example");

    assertThat(manifest.panels()).extracting(value->value.slug()).containsExactly("reports");
    assertThat(manifest.uiCatalog().modules()).hasSize(1);
    var module=manifest.uiCatalog().modules().getFirst();
    assertThat(module.routes()).extracting(route->route.id()).containsExactly("index");
    assertThat(module.routes().getFirst().resource()).isEqualTo(asset);
    assertThat(manifest.permissions()).containsEntry(asset,List.of("view"))
        .doesNotContainKey("application:aurevia/reports");
    assertThat(service.check(new CheckRequest("user-1","https://issuer.example",
        "application:aurevia/reports","view",Map.of(),"correlation-1"))
        .decision().result()).isEqualTo("DENY");
  }

  @Test void chartViewAlsoExposesReportsAndRevokingItRemovesTheModule() {
    String asset="external_resource:superset/secondary/chart/13";
    when(queries.activePanels()).thenReturn(List.of(reportsPanel("reports")));
    when(queries.activeResources()).thenReturn(supersetCatalog(asset));
    when(queries.permissionCandidates()).thenReturn(List.of(
        new AuthorizationQueryRepository.PermissionCandidate(asset,"EXTERNAL_RESOURCE","view")));
    allowBatchObjects(Set.of(asset));
    var before=service.manifest("user-1","https://issuer.example");
    assertThat(before.uiCatalog().modules()).hasSize(1);

    allowBatchObjects(Set.of());
    var after=service.manifest("user-1","https://issuer.example");
    assertThat(after.uiCatalog().modules()).isEmpty();
    assertThat(after.panels()).isEmpty();
    assertThat(after.version()).isNotEqualTo(before.version());
  }

  @Test void permissionsOutsideTheDiscoverySubtreeDoNotExposeReports() {
    when(queries.activePanels()).thenReturn(List.of(reportsPanel("reports")));
    for(String key:List.of("external_resource:other/operation/dashboard/7",
        "external_resource:superset/integration","page:hr.employees")) {
      when(queries.activeResources()).thenReturn(
          List.of(supersetCatalog().getFirst(),unownedResource(key)));
      when(queries.permissionCandidates()).thenReturn(List.of(
          new AuthorizationQueryRepository.PermissionCandidate(key,"EXTERNAL_RESOURCE","view")));
      allowBatchObjects(Set.of(key));
      assertThat(service.manifest("user-1","https://issuer.example").uiCatalog().modules())
          .as(key).isEmpty();
    }
  }

  @Test void panelWithoutDiscoverySubtreeStaysHiddenWhenNoDeclaredRouteIsAuthorized() {
    String asset="external_resource:superset/operation/dashboard/7";
    when(queries.activePanels()).thenReturn(List.of(reportsPanel("reports",null)));
    when(queries.activeResources()).thenReturn(supersetCatalog(asset));
    when(queries.permissionCandidates()).thenReturn(List.of(
        new AuthorizationQueryRepository.PermissionCandidate(asset,"EXTERNAL_RESOURCE","view")));
    allowBatchObjects(Set.of(asset));
    assertThat(service.manifest("user-1","https://issuer.example").uiCatalog().modules()).isEmpty();
  }

  @Test void authorizedDeclaredRoutesSuppressTheDiscoverabilityLandingFallback() {
    String asset="external_resource:superset/operation/dashboard/7";
    when(queries.activePanels()).thenReturn(List.of(reportsPanel("reports")));
    when(queries.activeResources()).thenReturn(supersetCatalog(asset));
    when(queries.permissionCandidates()).thenReturn(List.of(
        new AuthorizationQueryRepository.PermissionCandidate(asset,"EXTERNAL_RESOURCE","view"),
        new AuthorizationQueryRepository.PermissionCandidate(
            "application:aurevia/reports","APPLICATION","admin")));
    allowBatchObjects(Set.of(asset,"application:aurevia/reports"));

    var module=service.manifest("user-1","https://issuer.example").uiCatalog().modules().getFirst();

    assertThat(module.routes()).extracting(route->route.id()).containsExactly("admin");
    assertThat(module.routes().getFirst().resource()).isEqualTo("application:aurevia/reports");
  }

  @Test void landingGuardFallsBackToAHeldActionWhenTheDeclaredActionIsNotGranted() {
    String asset="external_resource:superset/operation/dashboard/7";
    when(queries.activePanels()).thenReturn(List.of(reportsPanel("reports")));
    when(queries.activeResources()).thenReturn(supersetCatalog(asset));
    when(queries.permissionCandidates()).thenReturn(List.of(
        new AuthorizationQueryRepository.PermissionCandidate(asset,"EXTERNAL_RESOURCE","update")));
    allowBatchObjects(Set.of(asset));

    var module=service.manifest("user-1","https://issuer.example").uiCatalog().modules().getFirst();

    assertThat(module.routes()).singleElement()
        .satisfies(route->{
          assertThat(route.id()).isEqualTo("index");
          assertThat(route.resource()).isEqualTo(asset);
          assertThat(route.action()).isEqualTo("update");
        });
  }

  @Test void theLowestOrderedAuthorizedDescendantIsTheDeterministicLandingGuard() {
    String first="external_resource:superset/operation/chart/1";
    String second="external_resource:superset/operation/dashboard/9";
    when(queries.activePanels()).thenReturn(List.of(reportsPanel("reports")));
    when(queries.activeResources()).thenReturn(supersetCatalog(second,first));
    when(queries.permissionCandidates()).thenReturn(List.of(
        new AuthorizationQueryRepository.PermissionCandidate(first,"EXTERNAL_RESOURCE","view"),
        new AuthorizationQueryRepository.PermissionCandidate(second,"EXTERNAL_RESOURCE","view")));
    allowBatchObjects(Set.of(first,second));

    var manifest=service.manifest("user-1","https://issuer.example");

    assertThat(manifest.uiCatalog().modules().getFirst().routes().getFirst().resource())
        .isEqualTo(first);
    // Discoverability never widens the permission set it was derived from.
    assertThat(manifest.permissions()).containsOnlyKeys(first,second);
  }

  private static AuthorizationQueryRepository.PanelRecord reportsPanel(String slug) {
    return reportsPanel(slug,SUPERSET_ROOT_KEY);
  }

  private static AuthorizationQueryRepository.PanelRecord reportsPanel(String slug,
      String discoveryResourceKey) {
    return new AuthorizationQueryRepository.PanelRecord(
        UUID.fromString("22222222-2222-2222-2222-222222222222"),"REPORTS",slug,
        "Reports","Reports","/reports","Reports catalog","dashboard",
        "reports","index",20,"1.0.0","https://static.example.test/reports/remoteEntry.js",
        "aurevia_reports","./bootstrap","1",null,"REAL","HYBRID","""
        {
          "routes":[
            {"key":"index","path":"","requiredResource":"application:aurevia/reports","requiredAction":"view"},
            {"key":"settings","path":"settings","requiredResource":"application:aurevia/reports","requiredAction":"view"},
            {"key":"admin","path":"admin","requiredResource":"application:aurevia/reports","requiredAction":"admin"}
          ],
          "navigation":[{"key":"reports-menu","type":"PAGE","routeKey":"index","title":"Reports"}]
        }
        """,discoveryResourceKey);
  }

  /** Superset assets are registered as children of the logical Superset catalog resource. */
  private static List<AuthorizationQueryRepository.ResourceRecord> supersetCatalog(
      String... assetKeys) {
    List<AuthorizationQueryRepository.ResourceRecord> catalog=new java.util.ArrayList<>();
    catalog.add(new AuthorizationQueryRepository.ResourceRecord(SUPERSET_ROOT_ID,null,
        SUPERSET_ROOT_KEY,"EXTERNAL_RESOURCE","سوپرست","Superset","reports","REAL"));
    for(String key:assetKeys) {
      catalog.add(new AuthorizationQueryRepository.ResourceRecord(
          UUID.nameUUIDFromBytes(key.getBytes(java.nio.charset.StandardCharsets.UTF_8)),
          SUPERSET_ROOT_ID,key,"EXTERNAL_RESOURCE",key,key,"reports","REAL"));
    }
    return List.copyOf(catalog);
  }

  private static AuthorizationQueryRepository.ResourceRecord unownedResource(String key) {
    return new AuthorizationQueryRepository.ResourceRecord(
        UUID.nameUUIDFromBytes(key.getBytes(java.nio.charset.StandardCharsets.UTF_8)),null,
        key,"EXTERNAL_RESOURCE",key,key,"other","REAL");
  }

  private void allowBatchObjects(Set<String> allowed) {
    when(relationships.checkBatch(anyList())).thenAnswer(invocation->{
      List<RelationshipCheck> checks=invocation.getArgument(0);
      Map<RelationshipCheck,Boolean> result=new LinkedHashMap<>();
      checks.forEach(check->result.put(check,allowed.contains(check.object())));
      return result;
    });
  }

  private static AuthorizationQueryRepository.PanelRecord panel() {
    return new AuthorizationQueryRepository.PanelRecord(
        UUID.fromString("11111111-1111-1111-1111-111111111111"),"ADMIN","admin",
        "مدیریت","Administration","/management","مدیریت سامانه","control",
        "admin","denied",10,"0.2.0",
        "https://static.example.test/admin/remoteEntry.js","aurevia_admin","./bootstrap",
        "1.0",null,"REAL","HYBRID",// no discovery subtree: declared routes are the only path
        """
        {
          "schemaVersion":"1.0",
          "microfrontend":{"key":"admin","name":"Administration","version":"0.2.0"},
          "defaultRouteKey":"denied",
          "runtime":{"apiBasePath":"/api/v1/admin"},
          "routes":[
            {"key":"denied","path":"settings","title":"تنظیمات","requiredResource":"page:admin.denied","requiredAction":"view"},
            {"key":"allowed","path":"resources","title":"منابع","requiredResource":"page:admin.allowed","requiredAction":"view"}
          ],
          "navigation":[
            {"key":"denied-menu","type":"PAGE","routeKey":"denied","title":"تنظیمات","order":10},
            {"key":"allowed-menu","type":"PAGE","routeKey":"allowed","title":"منابع","order":20}
          ]
        }
        """,null);
  }
}

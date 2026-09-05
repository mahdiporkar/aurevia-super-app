package com.aurevia.authz.authorization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.aurevia.authz.audit.AuthorizationDecisionAuditor;
import com.aurevia.authz.openfga.RelationshipAuthorizationPort;
import com.aurevia.authz.openfga.RelationshipAuthorizationPort.RelationshipCheck;
import com.aurevia.authz.policy.RuntimePolicyService;
import com.aurevia.authz.semantics.AuthorizationSemanticsRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AuthorizationDecisionServiceManifestTest {
  private final RelationshipAuthorizationPort relationships=mock(RelationshipAuthorizationPort.class);
  private final AuthorizationQueryRepository queries=mock(AuthorizationQueryRepository.class);
  private AuthorizationDecisionService service;

  @BeforeEach void setUp() {
    service=new AuthorizationDecisionService(relationships,queries,
        new AuthorizationSemanticsRegistry(),mock(RuntimePolicyService.class),
        mock(AuthorizationDecisionAuditor.class),new ObjectMapper());
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
        "1.0",null,"""
        {
          "schemaVersion":"1.0",
          "moduleKey":"admin",
          "defaultRouteId":"denied",
          "runtime":{"apiBasePath":"/api/v1/admin"},
          "routes":[
            {"id":"denied","path":"settings","title":"تنظیمات","resource":"page:admin.denied","action":"view"},
            {"id":"allowed","path":"resources","title":"منابع","resource":"page:admin.allowed","action":"view"}
          ],
          "menus":[
            {"id":"denied-menu","routeId":"denied","title":"تنظیمات","order":10},
            {"id":"allowed-menu","routeId":"allowed","title":"منابع","order":20}
          ]
        }
        """);
  }
}

package com.aurevia.authz.ui;

import static com.aurevia.authz.api.dto.UiPluginDtos.*;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aurevia.authz.observability.AuditTrail;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class UiPluginRegistryServiceTest {
  @Test void manifestNavigationIsChangedOnlyThroughOverlay() {
    UiPluginRepository repository=mock(UiPluginRepository.class);
    UUID panel=UUID.randomUUID();
    when(repository.activeManifest(panel)).thenReturn("""
        {"routes":[{"key":"employees","resourceKey":"page:hr.employee.list"}],
         "navigation":[{"key":"hr.nav.root","type":"GROUP","title":"HR"},
          {"key":"hr.nav.employees","type":"PAGE","parentKey":"hr.nav.root",
           "pageKey":"employees","title":"Employees"}]}
        """);
    when(repository.navigationOverrides(panel)).thenReturn(List.of());
    var service=new UiPluginRegistryService(repository,new ObjectMapper(),
        mock(UiArtifactPolicy.class),mock(AuditTrail.class));

    service.overrideNavigation(panel,"hr.nav.employees","operator",
        new NavigationOverrideRequest("مدیریت کارکنان","user",5,false,
            "MANIFEST",null,null,null,null));

    verify(repository).upsertMenu(eq(panel),eq("hr.nav.employees"),eq("مدیریت کارکنان"),
        eq("user"),eq(5),eq(false),eq("MANIFEST"),eq("PAGE"),
        eq("hr.nav.root"),eq("employees"),eq(null),eq("operator"));
  }

  @Test void manifestOverlayCannotChangeTheManifestOwnedStructure() {
    UiPluginRepository repository=mock(UiPluginRepository.class);
    UUID panel=UUID.randomUUID();
    when(repository.activeManifest(panel)).thenReturn("""
        {"routes":[{"key":"employees","resourceKey":"page:hr.employee.list"}],
         "navigation":[{"key":"hr.nav.root","type":"GROUP","title":"HR"},
          {"key":"hr.nav.employees","type":"PAGE","parentKey":"hr.nav.root",
           "pageKey":"employees","title":"Employees"}]}
        """);
    when(repository.navigationOverrides(panel)).thenReturn(List.of());
    var service=new UiPluginRegistryService(repository,new ObjectMapper(),
        mock(UiArtifactPolicy.class),mock(AuditTrail.class));

    assertThatThrownBy(()->service.overrideNavigation(panel,"hr.nav.employees","operator",
        new NavigationOverrideRequest("Employees",null,10,false,
            "MANIFEST","GROUP",null,null,null)))
        .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("presentation");
  }

  @Test void artifactPublicationRejectsInvalidNavigationHierarchy() {
    UiPluginRepository repository=mock(UiPluginRepository.class);
    UUID panel=UUID.randomUUID();
    when(repository.activePanelSlug(panel)).thenReturn(java.util.Optional.of("hr"));
    when(repository.resourceActionExists("page:hr.employee.list","view")).thenReturn(true);
    UiArtifactPolicy policy=mock(UiArtifactPolicy.class);
    when(policy.validate("https://static.example.test/hr/remoteEntry.js",null))
        .thenReturn("https://static.example.test/hr/remoteEntry.js");
    var service=new UiPluginRegistryService(repository,new ObjectMapper(),policy,
        mock(AuditTrail.class));
    var request=new ArtifactRequest("1.0.0",
        "https://static.example.test/hr/remoteEntry.js","aurevia_hr","./bootstrap","1.0",
        null,"""
        {"schemaVersion":"1.0","module":{"key":"hr"},
         "routes":[{"key":"employees","path":"/employees",
           "resourceKey":"page:hr.employee.list","action":"view"}],
         "navigation":[{"key":"hr.nav.employees","type":"PAGE",
           "parentKey":"hr.nav.missing","pageKey":"employees","title":"Employees"}]}
        """);

    assertThatThrownBy(()->service.publish(panel,"operator",request))
        .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("parent does not exist");
  }

  @Test void artifactPublicationRejectsUnsafeExternalNavigation() {
    UiPluginRepository repository=mock(UiPluginRepository.class);
    UUID panel=UUID.randomUUID();
    when(repository.activePanelSlug(panel)).thenReturn(java.util.Optional.of("hr"));
    UiArtifactPolicy policy=mock(UiArtifactPolicy.class);
    when(policy.validate("https://static.example.test/hr/remoteEntry.js",null))
        .thenReturn("https://static.example.test/hr/remoteEntry.js");
    var service=new UiPluginRegistryService(repository,new ObjectMapper(),policy,
        mock(AuditTrail.class));
    var request=new ArtifactRequest("1.0.0",
        "https://static.example.test/hr/remoteEntry.js","aurevia_hr","./bootstrap","1.0",
        null,"""
        {"schemaVersion":"1.0","module":{"key":"hr"},"routes":[],
         "navigation":[{"key":"hr.nav.external","type":"EXTERNAL_LINK",
           "externalUrl":"javascript:alert(1)","title":"Unsafe"}]}
        """);

    assertThatThrownBy(()->service.publish(panel,"operator",request))
        .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("safe absolute HTTPS");
  }
}

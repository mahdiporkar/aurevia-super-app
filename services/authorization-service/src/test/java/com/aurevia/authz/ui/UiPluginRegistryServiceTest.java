package com.aurevia.authz.ui;

import static com.aurevia.authz.api.dto.UiPluginDtos.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

import com.aurevia.authz.observability.AuditTrail;
import com.aurevia.authz.registry.ResourceManifestFetcher;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class UiPluginRegistryServiceTest {
  @Test void navigationDefinitionsExposeDefaultOverrideEffectiveAndStaleState() {
    UiPluginRepository repository=mock(UiPluginRepository.class);
    UUID panel=UUID.randomUUID();
    when(repository.activeManifestOptional(panel)).thenReturn(Optional.of("""
        {"routes":[{"key":"employees","path":"employees",
          "requiredResource":"page:hr.employee.list"}],
         "navigation":[{"key":"hr.nav.employees","type":"PAGE",
           "routeKey":"employees","title":"Employees","icon":"user","order":10}]}
        """));
    when(repository.navigationOverrides(panel)).thenReturn(List.of(
        new NavigationOverrideView("hr.nav.employees","مدیریت کارکنان",null,5,false,
            "MANIFEST","PAGE",null,"employees",null,"ACTIVE",2,null,"operator"),
        new NavigationOverrideView("hr.nav.removed","عنوان قدیمی",null,20,false,
            "MANIFEST","PAGE",null,"removed",null,"ACTIVE",1,null,"operator")));
    var service=new UiPluginRegistryService(repository,new ObjectMapper(),
        mock(UiArtifactPolicy.class),mock(ResourceManifestFetcher.class),mock(AuditTrail.class));

    var definitions=service.navigationDefinitions(panel);

    assertThat(definitions).hasSize(2);
    assertThat(definitions.getFirst().defaultTitle()).isEqualTo("Employees");
    assertThat(definitions.getFirst().overrideTitle()).isEqualTo("مدیریت کارکنان");
    assertThat(definitions.getFirst().effectiveTitle()).isEqualTo("مدیریت کارکنان");
    assertThat(definitions.getLast().sourceState()).isEqualTo("REMOVED_FROM_SOURCE");
    assertThat(definitions.getLast().effectiveTitle()).isNull();
  }

  @Test void frontendManifestSyncIsIdempotentAndKeepsAuthorizationSeparate() {
    UiPluginRepository repository=mock(UiPluginRepository.class);
    ResourceManifestFetcher fetcher=mock(ResourceManifestFetcher.class);
    UiArtifactPolicy policy=mock(UiArtifactPolicy.class);
    AuditTrail audit=mock(AuditTrail.class);
    UUID panel=UUID.randomUUID();
    String manifest="""
        {"schemaVersion":"1.0",
         "microfrontend":{"key":"hr","name":"Human Resources","version":"2.0.0"},
         "runtime":{"remoteEntry":"https://static.example.test/hr/remoteEntry.js",
           "remoteName":"aurevia_hr","exposedModule":"./plugin","contractVersion":"1.0",
           "apiBasePath":"/api/proxy/hr"},
         "defaultRouteKey":"employees",
         "routes":[{"key":"employees","path":"employees/:id",
           "requiredResource":"page:hr.employee.list","requiredAction":"view"}],
         "navigation":[{"key":"hr.nav.employees","type":"PAGE",
           "routeKey":"employees","title":"Employees"}]}
        """;
    var settings=new UiPluginRepository.PanelFrontendSettings(panel,"hr",
        "https://static.example.test/hr/mf-manifest.json",
        "https://static.example.test/hr/remoteEntry.js","aurevia_hr","./plugin","1.0",
        null,null,7,true);
    when(repository.lockFrontendSettings(panel)).thenReturn(Optional.of(settings));
    when(repository.activePanelSlug(panel)).thenReturn(Optional.of("hr"));
    when(repository.resourceActionExists("page:hr.employee.list","view")).thenReturn(true);
    when(repository.activeManifestOptional(panel)).thenReturn(Optional.empty());
    when(policy.validateMicroFrontendManifestUrl(settings.mfManifestUrl()))
        .thenReturn(settings.mfManifestUrl());
    when(policy.validate(settings.remoteEntryPath(),null)).thenReturn(settings.remoteEntryPath());
    when(fetcher.fetch(settings.mfManifestUrl())).thenReturn(manifest);
    when(repository.activate(any(),any(),eq(7L))).thenReturn(true);
    AtomicReference<UiPluginRepository.ArtifactInsert> inserted=new AtomicReference<>();
    org.mockito.Mockito.doAnswer(call->{inserted.set(call.getArgument(0));return null;})
        .when(repository).insertArtifact(any());
    when(repository.artifactByVersion(panel,"2.0.0")).thenAnswer(call->{
      var value=inserted.get();
      return value==null?Optional.empty():Optional.of(new UiPluginRepository.ArtifactRevision(
          value.id(),value.checksum(),value.manifest(),true,value.remoteEntryUrl(),
          value.remoteName(),value.exposedModule(),value.contractVersion(),value.integrity()));
    });
    var service=new UiPluginRegistryService(repository,new ObjectMapper(),policy,fetcher,audit);

    FrontendManifestSyncResult first=service.syncFrontendManifest(panel,"operator");
    FrontendManifestSyncResult second=service.syncFrontendManifest(panel,"operator");

    assertThat(first.idempotent()).isFalse();
    assertThat(first.routesAdded()).isEqualTo(1);
    assertThat(second.idempotent()).isTrue();
    assertThat(second.routesAdded()).isZero();
    verify(repository,times(1)).insertArtifact(any());
    verify(repository,times(1)).activate(eq(panel),any(),eq(7L));
  }

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
        mock(UiArtifactPolicy.class),mock(ResourceManifestFetcher.class),mock(AuditTrail.class));

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
        mock(UiArtifactPolicy.class),mock(ResourceManifestFetcher.class),mock(AuditTrail.class));

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
        mock(ResourceManifestFetcher.class),mock(AuditTrail.class));
    var request=new ArtifactRequest("1.0.0",
        "https://static.example.test/hr/remoteEntry.js","aurevia_hr","./bootstrap","1.0",
        null,"""
        {"schemaVersion":"1.0","module":{"key":"hr"},
         "routes":[{"key":"employees","path":"employees",
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
        mock(ResourceManifestFetcher.class),mock(AuditTrail.class));
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

  @Test void artifactPublicationRejectsConflictingParameterizedRouteShapes() {
    UiPluginRepository repository=mock(UiPluginRepository.class);
    UUID panel=UUID.randomUUID();
    when(repository.activePanelSlug(panel)).thenReturn(Optional.of("hr"));
    when(repository.resourceActionExists("page:hr.employee.detail","view")).thenReturn(true);
    UiArtifactPolicy policy=mock(UiArtifactPolicy.class);
    String remote="https://static.example.test/hr/remoteEntry.js";
    when(policy.validate(remote,null)).thenReturn(remote);
    var service=new UiPluginRegistryService(repository,new ObjectMapper(),policy,
        mock(ResourceManifestFetcher.class),mock(AuditTrail.class));
    var request=new ArtifactRequest("1.0.0",remote,"aurevia_hr","./bootstrap","1.0",null,"""
        {"schemaVersion":"1.0","microfrontend":{"key":"hr"},
         "routes":[
           {"key":"employee-by-id","path":"employees/:id",
            "requiredResource":"page:hr.employee.detail"},
           {"key":"employee-by-code","path":"employees/:employeeCode",
            "requiredResource":"page:hr.employee.detail"}],
         "navigation":[]}
        """);

    assertThatThrownBy(()->service.publish(panel,"operator",request))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("conflicts with another effective local route");
  }

  @Test void mfManifestCannotDefineAuthorizationResources() {
    UiPluginRepository repository=mock(UiPluginRepository.class);
    UUID panel=UUID.randomUUID();
    when(repository.activePanelSlug(panel)).thenReturn(Optional.of("hr"));
    UiArtifactPolicy policy=mock(UiArtifactPolicy.class);
    String remote="https://static.example.test/hr/remoteEntry.js";
    when(policy.validate(remote,null)).thenReturn(remote);
    var service=new UiPluginRegistryService(repository,new ObjectMapper(),policy,
        mock(ResourceManifestFetcher.class),mock(AuditTrail.class));
    var request=new ArtifactRequest("1.0.0",remote,"aurevia_hr","./bootstrap","1.0",null,"""
        {"schemaVersion":"1.0","microfrontend":{"key":"hr"},
         "routes":[],"navigation":[],"resources":[]}
        """);

    assertThatThrownBy(()->service.publish(panel,"operator",request))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("must not define authorization resources");
  }
}

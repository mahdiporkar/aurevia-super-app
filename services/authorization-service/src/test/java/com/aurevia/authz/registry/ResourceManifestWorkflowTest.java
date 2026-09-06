package com.aurevia.authz.registry;

import static com.aurevia.authz.api.dto.ResourceManifestDtos.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aurevia.authz.observability.AuditTrail;
import com.aurevia.authz.ui.UiArtifactPolicy;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class ResourceManifestWorkflowTest {
  private final ResourceManifestRepository repository=mock(ResourceManifestRepository.class);
  private final ObjectMapper json=new ObjectMapper();

  @Test void hybridImportCreatesDraftAndPublishAppliesOnlyAfterApproval() {
    UUID panelId=UUID.randomUUID();
    var settings=new ResourceManifestRepository.PanelManifestSettings(panelId,"hr","منابع انسانی",
        "Human Resources","HYBRID","https://static.example.test/hr/resource-manifest.json");
    when(repository.panelSettings(panelId)).thenReturn(Optional.of(settings));
    when(repository.definitionTree(anyString())).thenReturn(List.of());
    when(repository.revisionByVersion(any(),anyString())).thenReturn(Optional.empty());
    when(repository.actionExists(anyString())).thenReturn(true);
    AtomicReference<ResourceManifestRepository.DraftInsert> stored=new AtomicReference<>();
    doAnswer(invocation->{stored.set(invocation.getArgument(0));return true;})
        .when(repository).insertDraft(any());
    when(repository.draft(any(),any())).thenAnswer(invocation->{
      var value=stored.get();
      return value==null||!value.id().equals(invocation.getArgument(1))?Optional.empty():Optional.of(
          new ResourceManifestRepository.DraftRecord(value.id(),value.panelId(),
              value.applicationKey(),value.manifestVersion(),value.schemaVersion(),value.checksum(),
              value.actor(),value.sourceUrl(),value.payload(),"DRAFT",value.diffSummary(),
              Instant.parse("2026-01-01T00:00:00Z"),null,null));
    });
    Map<String,UUID> ids=new HashMap<>();
    when(repository.resourceExists(anyString())).thenAnswer(invocation->ids.containsKey(invocation.getArgument(0)));
    when(repository.resourceOwnership(anyString())).thenReturn(Optional.empty());
    doAnswer(invocation->{
      ResourceDefinition value=invocation.getArgument(0);ids.put(value.key(),UUID.randomUUID());return null;
    }).when(repository).upsertResource(any(),any(),anyString(),any(),anyString());
    when(repository.resourceId(anyString())).thenAnswer(invocation->Optional.ofNullable(ids.get(invocation.getArgument(0))));
    when(repository.addAction(any(),anyString())).thenReturn(true);
    when(repository.deprecateMissing(any(),anyString(),any())).thenReturn(1);
    when(repository.markPublished(any(),anyString())).thenReturn(true);
    ResourceManifestService service=service();

    ManifestDraftView draft=service.stage(panelId,manifest(),"operator-1");

    assertThat(draft.workflowStatus()).isEqualTo("DRAFT");
    assertThat(draft.changes()).extracting(ManifestChange::changeType).contains("CREATE");
    assertThat(ids).isEmpty();

    PublishResult result=service.publish(panelId,draft.id(),"operator-1");

    assertThat(result.workflowStatus()).isEqualTo("PUBLISHED");
    assertThat(result.created()).isEqualTo(3);
    assertThat(result.deprecated()).isEqualTo(1);
    assertThat(ids).containsKeys("application:aurevia/hr","module:hr","page:hr.employee.list");
    verify(repository,atLeastOnce()).enqueueParent(any(),any(),
        org.mockito.ArgumentMatchers.eq("RESOURCE_PARENT_WRITE"));
  }

  @Test void manualModeRejectsManifestImport() {
    UUID panelId=UUID.randomUUID();
    when(repository.panelSettings(panelId)).thenReturn(Optional.of(
        new ResourceManifestRepository.PanelManifestSettings(panelId,"hr","HR","HR",
            "MANUAL",null)));
    assertThatThrownBy(()->service().stage(panelId,manifest(),"operator-1"))
        .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("MANUAL");
  }

  @Test void aPublishedSemanticVersionCannotBeReusedForDifferentContent() {
    UUID panelId=UUID.randomUUID();
    when(repository.panelSettings(panelId)).thenReturn(Optional.of(
        new ResourceManifestRepository.PanelManifestSettings(panelId,"hr","HR","HR",
            "HYBRID",null)));
    when(repository.definitionTree(anyString())).thenReturn(List.of());
    when(repository.actionExists(anyString())).thenReturn(true);
    when(repository.revisionByVersion(panelId,"1.2.0")).thenReturn(Optional.of(
        new ResourceManifestRepository.DraftRecord(UUID.randomUUID(),panelId,
            "application:aurevia/hr","1.2.0","1.0","different-checksum","operator-1",
            null,"{}","PUBLISHED","[]",Instant.parse("2026-01-01T00:00:00Z"),
            Instant.parse("2026-01-01T00:01:00Z"),"operator-1")));

    assertThatThrownBy(()->service().stage(panelId,manifest(),"operator-2"))
        .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("immutable");
  }

  @Test void moduleKeyMustMatchTheRegisteredPanel() {
    UUID panelId=UUID.randomUUID();
    when(repository.panelSettings(panelId)).thenReturn(Optional.of(
        new ResourceManifestRepository.PanelManifestSettings(panelId,"hr","HR","HR",
            "HYBRID",null)));
    MicroFrontendManifest wrong=new MicroFrontendManifest("1.0",
        new ModuleMetadata("finance","Finance","مالی","Finance","1.0.0"),
        List.of(),List.of(),List.of());

    assertThatThrownBy(()->service().stage(panelId,wrong,"operator-1"))
        .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("panel slug");
  }

  @Test void malformedFetchedManifestIsRejectedAsAContractError() {
    UUID panelId=UUID.randomUUID();
    when(repository.panelSettings(panelId)).thenReturn(Optional.of(
        new ResourceManifestRepository.PanelManifestSettings(panelId,"hr","HR","HR",
            "HYBRID",null)));
    MicroFrontendManifest malformed=new MicroFrontendManifest("1.0",null,
        List.of(),List.of(),List.of());

    assertThatThrownBy(()->service().stage(panelId,malformed,"operator-1"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("module is required");
  }

  private ResourceManifestService service() {
    return new ResourceManifestService(repository,mock(ResourceManifestFetcher.class),
        mock(UiArtifactPolicy.class),mock(AuditTrail.class),json);
  }

  private static MicroFrontendManifest manifest() {
    return new MicroFrontendManifest("1.0",
        new ModuleMetadata("hr","Human Resources","منابع انسانی","Human Resources","1.2.0"),
        List.of(new ManifestRoute("employees",null,"/employees","./EmployeeList",
            "page:hr.employee.list",null,"view","کارکنان")),
        List.of(new ManifestResource("page:hr.employee.list","PAGE",null,null,
            "Employees","کارکنان","Employees","hr","INTERNAL",List.of("view"),
            Map.of(),null,null,null)),
        List.of(new NavigationNode("hr.nav.root",null,"GROUP",null,null,null,null,
                "منابع انسانی","team",10,null),
            new NavigationNode("hr.nav.employees",null,"PAGE","hr.nav.root",null,
                "employees",null,"کارکنان","user",20,null)));
  }
}

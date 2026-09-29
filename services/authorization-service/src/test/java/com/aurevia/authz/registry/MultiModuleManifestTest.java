package com.aurevia.authz.registry;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import com.aurevia.authz.api.dto.ResourceManifestDtos.*;
import com.aurevia.authz.ui.UiArtifactPolicy;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class MultiModuleManifestTest {
  private final ObjectMapper json=new ObjectMapper();
  private final UUID panel=UUID.randomUUID();
  private final ResourceManifestRepository repository=mock(ResourceManifestRepository.class);
  private final ResourceManifestService service=new ResourceManifestService(repository,
      mock(ResourceManifestFetcher.class),mock(UiArtifactPolicy.class),null,json,
      mock(JdbcManifestReleaseRepository.class),mock(JdbcManifestCatalogProjection.class));

  private ObjectNode fixture() throws Exception {
    when(repository.panelSettings(panel)).thenReturn(Optional.of(new ResourceManifestRepository.PanelManifestSettings(
        panel,"test-multi-module","TEST_MULTI_MODULE","TEST_MULTI_MODULE","HYBRID",null)));
    when(repository.actionExists(anyString())).thenReturn(true);
    AtomicReference<ResourceManifestRepository.DraftInsert> stored=new AtomicReference<>();
    when(repository.insertDraft(any())).thenAnswer(call->{stored.set(call.getArgument(0));return true;});
    when(repository.draft(eq(panel),any())).thenAnswer(call->{var d=stored.get();
      return Optional.of(new ResourceManifestRepository.DraftRecord(d.id(),d.panelId(),d.applicationKey(),
          d.manifestVersion(),d.schemaVersion(),d.checksum(),d.actor(),d.sourceUrl(),d.payload(),
          "DRAFT",d.diffSummary(),Instant.now(),null,null));});
    return (ObjectNode)json.readTree(com.aurevia.authz.support.MultiModuleFixture.read("resource-manifest.json"));
  }

  @Test void shippedManifestNormalizesToOneApplicationThreeModulesAndSixPages() throws Exception {
    var draft=service.stageJson(panel,fixture(),"test");
    assertThat(draft.changes()).hasSize(10);
    assertThat(draft.changes()).filteredOn(c->"APPLICATION".equals(c.afterType())).hasSize(1);
    assertThat(draft.changes()).filteredOn(c->"MODULE".equals(c.afterType())).hasSize(3);
    assertThat(draft.changes()).filteredOn(c->"PAGE".equals(c.afterType())).hasSize(6);
    verify(repository,never()).upsertResource(any(),any(),any(),any(),any());
  }

  @Test void threeIndependentModuleKeysAlsoProduceTheSyntheticFourthModule() throws Exception {
    var payload=fixture();
    for(var node:payload.withArray("resources")) {
      if(node.path("key").asText().equals("module:test-multi-module"))
        ((ObjectNode)node).put("key","module:test-multi-module.employee");
      if(node.path("parentKey").asText().equals("module:test-multi-module"))
        ((ObjectNode)node).put("parentKey","module:test-multi-module.employee");
    }
    assertThat(service.stageJson(panel,payload,"test").changes())
        .filteredOn(c->"MODULE".equals(c.afterType())).hasSize(4);
  }

  @Test void moduleCannotNestInsideSiblingAndPageCannotSkipModule() throws Exception {
    var nested=fixture();
    ((ObjectNode)nested.withArray("resources").get(3)).put("parentKey","module:test-multi-module");
    assertThatThrownBy(()->service.stageJson(panel,nested,"test")).hasMessageContaining("invalid parent type MODULE for MODULE");
    var skipped=fixture();
    ((ObjectNode)skipped.withArray("resources").get(1)).put("parentKey","application:aurevia/test-multi-module");
    assertThatThrownBy(()->service.stageJson(panel,skipped,"test")).hasMessageContaining("invalid parent type APPLICATION for PAGE");
  }
}

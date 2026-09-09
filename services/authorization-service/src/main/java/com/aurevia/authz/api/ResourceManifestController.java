package com.aurevia.authz.api;

import static com.aurevia.authz.api.dto.ResourceManifestDtos.*;

import com.aurevia.authz.registry.ResourceManifestService;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.ResponseStatus;

/** HTTP adapter for capability-definition manifests; subject grants are separate. */
@RestController
@RequestMapping("/internal/v1/registry")
public final class ResourceManifestController {
  private final ResourceManifestService manifests;

  public ResourceManifestController(ResourceManifestService manifests) {
    this.manifests=manifests;
  }

  @GetMapping("/resource-definition-manifests/{application}")
  public DefinitionManifest definition(@PathVariable String application) {
    return manifests.definition(application);
  }

  @PutMapping("/resource-definition-manifests/{application}")
  public ManifestDraftView stageLegacy(@PathVariable String application,
      @Valid @RequestBody DefinitionManifest manifest,
      @RequestHeader(value="X-Actor",defaultValue="unknown") String actor) {
    return manifests.stageLegacy(application,manifest,actor);
  }

  @GetMapping("/panels/{panelId}/resource-manifests")
  public List<ManifestDraftView> drafts(@PathVariable UUID panelId) {
    return manifests.drafts(panelId);
  }

  @PostMapping("/panels/{panelId}/resource-manifests/fetch")
  @ResponseStatus(HttpStatus.CREATED)
  public ManifestDraftView fetch(@PathVariable UUID panelId,
      @RequestHeader("X-Actor") String actor) {
    return manifests.fetch(panelId,actor);
  }

  @PostMapping("/panels/{panelId}/resource-manifests/drafts")
  @ResponseStatus(HttpStatus.CREATED)
  public ManifestDraftView importDraft(@PathVariable UUID panelId,
      @RequestBody JsonNode manifest,
      @RequestHeader("X-Actor") String actor) {
    return manifests.stageJson(panelId,manifest,actor);
  }

  @GetMapping("/panels/{panelId}/resource-manifests/drafts/{draftId}")
  public ManifestDraftView preview(@PathVariable UUID panelId,@PathVariable UUID draftId) {
    return manifests.preview(panelId,draftId);
  }

  @PostMapping("/panels/{panelId}/resource-manifests/drafts/{draftId}/publish")
  public PublishResult publish(@PathVariable UUID panelId,@PathVariable UUID draftId,
      @RequestHeader("X-Actor") String actor) {
    return manifests.publish(panelId,draftId,actor);
  }
}

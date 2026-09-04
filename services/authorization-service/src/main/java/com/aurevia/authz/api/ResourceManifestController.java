package com.aurevia.authz.api;

import static com.aurevia.authz.api.dto.ResourceManifestDtos.*;

import com.aurevia.authz.registry.ResourceManifestService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** HTTP adapter for capability-definition manifests; subject grants are separate. */
@RestController
@RequestMapping("/internal/v1/registry/resource-definition-manifests")
public final class ResourceManifestController {
  private final ResourceManifestService manifests;

  public ResourceManifestController(ResourceManifestService manifests) {
    this.manifests=manifests;
  }

  @GetMapping("/{application}")
  public DefinitionManifest definition(@PathVariable String application) {
    return manifests.definition(application);
  }

  @PutMapping("/{application}")
  public SyncResult sync(@PathVariable String application,
      @Valid @RequestBody DefinitionManifest manifest,
      @RequestHeader(value="X-Actor",defaultValue="unknown") String actor) {
    return manifests.sync(application,manifest,actor);
  }
}

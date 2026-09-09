package com.aurevia.authz.api;

import static com.aurevia.authz.api.dto.UiPluginDtos.*;

import com.aurevia.authz.ui.UiPluginRegistryService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** HTTP adapter for immutable UI artifacts and operator-owned menu overrides. */
@RestController
@RequestMapping("/internal/v1/registry/panels/{panelId}")
public final class UiPluginRegistryController {
  private final UiPluginRegistryService plugins;

  public UiPluginRegistryController(UiPluginRegistryService plugins) { this.plugins=plugins; }

  @GetMapping("/artifacts")
  public List<ArtifactView> artifacts(@PathVariable UUID panelId) {
    return plugins.artifacts(panelId);
  }

  @PostMapping("/artifacts")
  @ResponseStatus(HttpStatus.CREATED)
  public ArtifactPublishedResponse publish(@PathVariable UUID panelId,
      @RequestHeader("X-Actor") String actor,@Valid @RequestBody ArtifactRequest request) {
    return plugins.publish(panelId,actor,request);
  }

  @PostMapping("/frontend-manifests/sync")
  public FrontendManifestSyncResult syncFrontendManifest(@PathVariable UUID panelId,
      @RequestHeader("X-Actor") String actor) {
    return plugins.syncFrontendManifest(panelId,actor);
  }

  @PostMapping("/artifacts/{artifactId}/activate")
  public ArtifactActivatedResponse activate(@PathVariable UUID panelId,
      @PathVariable UUID artifactId,@RequestParam long version) {
    return plugins.activate(panelId,artifactId,version);
  }

  @PutMapping("/menu-overrides/{menuId}")
  public MenuOverrideResponse menu(@PathVariable UUID panelId,@PathVariable String menuId,
      @RequestHeader(name="X-Actor",defaultValue="unknown") String actor,
      @Valid @RequestBody MenuOverrideRequest request) {
    return plugins.overrideMenu(panelId,menuId,actor,request);
  }

  @GetMapping("/navigation-overrides")
  public List<NavigationOverrideView> navigationOverrides(@PathVariable UUID panelId) {
    return plugins.navigationOverrides(panelId);
  }

  @GetMapping("/navigation-definitions")
  public List<EffectiveNavigationDefinitionView> navigationDefinitions(@PathVariable UUID panelId) {
    return plugins.navigationDefinitions(panelId);
  }

  @PutMapping("/navigation-overrides/{navigationKey}")
  public MenuOverrideResponse navigation(@PathVariable UUID panelId,
      @PathVariable String navigationKey,
      @RequestHeader(name="X-Actor",defaultValue="unknown") String actor,
      @Valid @RequestBody NavigationOverrideRequest request) {
    return plugins.overrideNavigation(panelId,navigationKey,actor,request);
  }

  @DeleteMapping("/navigation-overrides/{navigationKey}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void removeNavigation(@PathVariable UUID panelId,@PathVariable String navigationKey,
      @RequestHeader(name="X-Actor",defaultValue="unknown") String actor) {
    plugins.removeNavigationOverride(panelId,navigationKey,actor);
  }
}

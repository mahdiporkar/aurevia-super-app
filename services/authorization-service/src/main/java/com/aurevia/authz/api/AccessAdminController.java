package com.aurevia.authz.api;

import static com.aurevia.authz.access.AccessModels.*;
import static com.aurevia.authz.api.dto.AccessAdminDtos.*;

import com.aurevia.authz.access.AccessAdministrationService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

/** HTTP adapter only: validation, DTO mapping, and use-case invocation. */
@RestController
@RequestMapping("/internal/v1/registry")
public class AccessAdminController {
  private final AccessAdministrationService service;

  public AccessAdminController(AccessAdministrationService service) { this.service = service; }

  @GetMapping("/resource-types")
  public Set<String> resourceTypes() { return AccessAdministrationService.RESOURCE_TYPES; }

  @GetMapping({"/resources", "/resource-tree"})
  public List<ResourceView> resources() { return service.resources(); }

  @PostMapping("/resources")
  @ResponseStatus(HttpStatus.CREATED)
  public MutationResult createResource(@Valid @RequestBody ResourceRequest request,
      @RequestHeader("X-Actor") String actor) {
    return service.createResource(request.toCommand(), actor);
  }

  @PutMapping("/resources/{id}")
  public MutationResult updateResource(@PathVariable UUID id, @RequestParam long version,
      @Valid @RequestBody ResourceRequest request, @RequestHeader("X-Actor") String actor) {
    return service.updateResource(id, version, request.toCommand(), actor);
  }

  @GetMapping("/actions")
  public List<ActionView> actions() { return service.actions(); }

  @PostMapping("/actions")
  @ResponseStatus(HttpStatus.CREATED)
  public MutationResult createAction(@Valid @RequestBody ActionRequest request,
      @RequestHeader("X-Actor") String actor) {
    return service.createAction(request.toCommand(), actor);
  }

  @PutMapping("/resources/{resourceId}/actions/{actionId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void attachAction(@PathVariable UUID resourceId, @PathVariable UUID actionId,
      @RequestHeader("X-Actor") String actor) {
    service.attachAction(resourceId, actionId, actor);
  }

  @DeleteMapping("/resources/{resourceId}/actions/{actionId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void detachAction(@PathVariable UUID resourceId, @PathVariable UUID actionId,
      @RequestHeader("X-Actor") String actor) {
    service.detachAction(resourceId, actionId, actor);
  }

  @GetMapping("/users")
  public List<UserView> users() { return service.users(); }

  @PostMapping("/users")
  @ResponseStatus(HttpStatus.CREATED)
  public MutationResult createUser(@Valid @RequestBody UserRequest request,
      @RequestHeader("X-Actor") String actor) {
    return service.createUser(request.toCommand(), actor);
  }

  @GetMapping("/users/{id}/grants")
  public List<GrantView> userGrants(@PathVariable UUID id) { return service.grants("USER", id); }

  @GetMapping("/subjects/{type}/{id}/grants")
  public List<GrantView> subjectGrants(@PathVariable String type, @PathVariable UUID id) {
    return service.grants(type, id);
  }

  @PostMapping("/grants")
  @ResponseStatus(HttpStatus.CREATED)
  public GrantResult grant(@Valid @RequestBody GrantRequest request,
      @RequestHeader("X-Actor") String actor) {
    return service.grant(request.toCommand(), actor);
  }

  @DeleteMapping("/grants/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void revoke(@PathVariable UUID id, @RequestHeader("X-Actor") String actor) {
    service.revoke(id, actor);
  }
}

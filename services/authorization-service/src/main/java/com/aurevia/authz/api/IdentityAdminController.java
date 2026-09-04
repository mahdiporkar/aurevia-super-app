package com.aurevia.authz.api;

import static com.aurevia.authz.api.dto.IdentityAdminDtos.*;
import static com.aurevia.authz.identity.IdentityModels.*;

import com.aurevia.authz.identity.IdentityAdministrationService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

/** HTTP adapter for users, directory/access groups, roles, and role assignments. */
@RestController
@RequestMapping("/internal/v1/registry")
public class IdentityAdminController {
  private final IdentityAdministrationService service;
  public IdentityAdminController(IdentityAdministrationService service) { this.service = service; }

  @GetMapping("/directory-groups")
  public List<DirectoryGroupView> directoryGroups() { return service.directoryGroups(); }

  @GetMapping("/roles")
  public List<RoleView> roles() { return service.roles(); }

  @GetMapping("/role-assignments")
  public List<RoleAssignmentView> roleAssignments() { return service.roleAssignments(); }

  @PostMapping("/roles")
  @ResponseStatus(HttpStatus.CREATED)
  public MutationResult createRole(@Valid @RequestBody RoleRequest request,
      @RequestHeader("X-Actor") String actor) {
    return service.createRole(request.toCommand(), actor);
  }

  @PutMapping("/roles/{id}")
  public MutationResult updateRole(@PathVariable UUID id, @RequestParam long version,
      @Valid @RequestBody RoleRequest request, @RequestHeader("X-Actor") String actor) {
    return service.updateRole(id, version, request.toCommand(), actor);
  }

  @PatchMapping("/roles/{id}/status")
  public MutationResult updateRoleStatus(@PathVariable UUID id, @RequestParam long version,
      @RequestBody StatusRequest request, @RequestHeader("X-Actor") String actor) {
    return service.updateRoleStatus(id, version, request.active(), actor);
  }

  @PostMapping("/role-assignments")
  @ResponseStatus(HttpStatus.CREATED)
  public void assignRole(@Valid @RequestBody RoleAssignmentRequest request,
      @RequestHeader("X-Actor") String actor) {
    service.assignRole(request.toCommand(), actor);
  }

  @DeleteMapping("/role-assignments/{subjectType}/{subjectId}/{roleId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void revokeRole(@PathVariable String subjectType, @PathVariable UUID subjectId,
      @PathVariable UUID roleId, @RequestHeader("X-Actor") String actor) {
    service.revokeRole(subjectType, subjectId, roleId, actor);
  }
}

package com.aurevia.authz.api;

import static com.aurevia.authz.api.dto.OuAccessDtos.*;

import com.aurevia.authz.directory.OuAccessAdministrationService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** HTTP adapter for read-only LDAP OU data and administrator-owned access rules. */
@RestController
@RequestMapping("/internal/v1/registry/ou-access")
public final class OuAccessAdminController {
  private final OuAccessAdministrationService access;
  public OuAccessAdminController(OuAccessAdministrationService access) { this.access=access; }

  @GetMapping("/ous") public List<Map<String,Object>> ous() { return access.ous(); }
  @GetMapping("/access-groups") public List<Map<String,Object>> groups() { return access.groups(); }
  @GetMapping("/access-groups/{id}/rules") public List<Map<String,Object>> rules(@PathVariable UUID id) { return access.rules(id); }
  @GetMapping("/access-groups/{id}/members") public List<Map<String,Object>> members(@PathVariable UUID id) { return access.members(id); }
  @GetMapping("/recalculation-jobs") public List<Map<String,Object>> jobs() { return access.recalculationJobs(); }

  @PostMapping("/access-groups") @ResponseStatus(HttpStatus.CREATED)
  public VersionResponse createGroup(@Valid @RequestBody GroupRequest request,
      @RequestHeader("X-Actor") String actor) { return access.createGroup(request,actor); }

  @PutMapping("/access-groups/{id}")
  public VersionResponse updateGroup(@PathVariable UUID id,@RequestParam long version,
      @Valid @RequestBody GroupRequest request,@RequestHeader("X-Actor") String actor) {
    return access.updateGroup(id,version,request,actor);
  }

  @PostMapping("/access-groups/{id}/rules") @ResponseStatus(HttpStatus.CREATED)
  public VersionResponse addRule(@PathVariable UUID id,@Valid @RequestBody RuleRequest request,
      @RequestHeader("X-Actor") String actor) { return access.addRule(id,request,actor); }

  @DeleteMapping("/access-groups/{groupId}/rules/{ruleId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void removeRule(@PathVariable UUID groupId,@PathVariable UUID ruleId,
      @RequestHeader("X-Actor") String actor) { access.removeRule(groupId,ruleId,actor); }

  @PostMapping("/access-groups/{id}/preview")
  public PreviewResponse preview(@PathVariable UUID id) { return access.preview(id); }

  @GetMapping("/application-grants")
  public List<Map<String,Object>> grants() { return access.grants(); }

  @PostMapping("/application-grants") @ResponseStatus(HttpStatus.CREATED)
  public PendingGrantResponse grant(@Valid @RequestBody GrantRequest request,
      @RequestHeader("X-Actor") String actor) { return access.grant(request,actor); }

  @DeleteMapping("/application-grants/{id}") @ResponseStatus(HttpStatus.NO_CONTENT)
  public void revoke(@PathVariable UUID id,@RequestHeader("X-Actor") String actor) {
    access.revoke(id,actor);
  }

  @GetMapping("/users/{id}/explain")
  public ExplanationResponse explain(@PathVariable UUID id) { return access.explain(id); }
}

package com.aurevia.authz.identity;

import static com.aurevia.authz.identity.IdentityModels.*;

import com.aurevia.authz.observability.AuditTrail;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class IdentityAdministrationService {
  private static final Pattern ROLE_KEY = Pattern.compile("^[a-z][a-z0-9-]{2,159}$");
  private static final Set<String> SUBJECT_TYPES = Set.of("USER", "DIRECTORY_GROUP", "ACCESS_GROUP");
  private final IdentityRepository repository;
  private final AuditTrail auditTrail;

  public IdentityAdministrationService(IdentityRepository repository, AuditTrail auditTrail) {
    this.repository = repository;
    this.auditTrail = auditTrail;
  }

  public List<DirectoryGroupView> directoryGroups() { return repository.directoryGroups(); }
  public List<AccessGroupView> accessGroups() { return repository.accessGroups(); }
  public List<RoleView> roles() { return repository.roles(); }
  public List<RoleAssignmentView> roleAssignments() { return repository.roleAssignments(); }

  @Transactional
  public MutationResult createRole(RoleCommand command, String actor) {
    validateRole(command);
    UUID id = UUID.randomUUID();
    repository.createRole(id, command);
    audit(actor, "ROLE_CREATED", "role", command.roleKey());
    return new MutationResult(id, 0);
  }

  @Transactional
  public MutationResult updateRole(UUID id, long version, RoleCommand command, String actor) {
    validateRole(command);
    RoleSnapshot prior = repository.role(id)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Role not found"));
    if (!prior.roleKey().equals(command.roleKey())) {
      throw new IllegalArgumentException("roleKey is immutable");
    }
    if (repository.updateRole(id, version, command) != 1) {
      throw new OptimisticLockingFailureException("role changed or missing");
    }
    audit(actor, "ROLE_UPDATED", "role", command.roleKey());
    return new MutationResult(id, version + 1);
  }

  @Transactional
  public MutationResult updateRoleStatus(UUID id, long version, boolean active, String actor) {
    RoleSnapshot role = repository.role(id)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Role not found"));
    if (repository.updateRoleStatus(id, version, active) != 1) {
      throw new OptimisticLockingFailureException("role changed or missing");
    }
    audit(actor, active ? "ROLE_ACTIVATED" : "ROLE_DEACTIVATED", "role", role.roleKey());
    return new MutationResult(id, version + 1);
  }

  @Transactional
  public void assignRole(RoleAssignmentCommand command, String actor) {
    String type = normalizeSubjectType(command.subjectType());
    long version = repository.upsertRoleAssignment(type, command.subjectId(), command.roleId(),
        command.expiresAt(), actor);
    repository.enqueueRoleAssignment(type, command.subjectId(), command.roleId(),
        "ROLE_ASSIGNMENT_WRITE", version);
    audit(actor, "ROLE_ASSIGNED", type.toLowerCase(Locale.ROOT), command.subjectId().toString());
  }

  @Transactional
  public void revokeRole(String subjectType, UUID subjectId, UUID roleId, String actor) {
    String type = normalizeSubjectType(subjectType);
    long version = repository.roleAssignmentVersion(type, subjectId, roleId)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
            "Role assignment not found"));
    repository.enqueueRoleAssignment(type, subjectId, roleId, "ROLE_ASSIGNMENT_DELETE", version + 1);
    if (repository.deleteRoleAssignment(type, subjectId, roleId) != 1) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Role assignment not found");
    }
    audit(actor, "ROLE_REVOKED", type.toLowerCase(Locale.ROOT), subjectId.toString());
  }

  private static void validateRole(RoleCommand command) {
    if (!ROLE_KEY.matcher(command.roleKey()).matches()) {
      throw new IllegalArgumentException("roleKey must be lowercase kebab-case");
    }
  }

  private static String normalizeSubjectType(String value) {
    String normalized = value == null ? "" : value.toUpperCase(Locale.ROOT);
    if ("GROUP".equals(normalized)) normalized = "DIRECTORY_GROUP";
    if (!SUBJECT_TYPES.contains(normalized)) {
      throw new IllegalArgumentException(
          "subjectType must be USER, DIRECTORY_GROUP, or ACCESS_GROUP");
    }
    return normalized;
  }

  private void audit(String actor, String event, String type, String key) {
    repository.appendAudit(actor, event, type, key);
    auditTrail.success("IDENTITY", event.toLowerCase(Locale.ROOT).replace('_', '.'), type, key,
        type, key, key, event, null, Map.of("target", key));
  }
}

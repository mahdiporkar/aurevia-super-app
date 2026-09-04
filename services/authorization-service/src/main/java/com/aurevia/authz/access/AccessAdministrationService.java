package com.aurevia.authz.access;

import static com.aurevia.authz.access.AccessModels.*;

import com.aurevia.authz.observability.AuditTrail;
import com.aurevia.authz.semantics.AuthorizationSemanticsRegistry;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

@Service
public class AccessAdministrationService {
  public static final Set<String> RESOURCE_TYPES = Set.of(
      "APPLICATION", "MODULE", "PAGE", "UI_COMPONENT", "FIELD", "BUSINESS_RESOURCE",
      "EXTERNAL_RESOURCE", "API_RESOURCE", "DATA_RESOURCE", "DATA_GOVERNANCE_RESOURCE");
  private static final Set<String> SOURCES = Set.of(
      "APPLICATION_MANIFEST", "ADMIN", "EXTERNAL_SYNC", "SYSTEM");
  private static final Set<String> SUBJECT_TYPES = Set.of("USER", "GROUP", "ACCESS_GROUP", "ROLE");
  private static final Map<String, String> PREFIXES = Map.ofEntries(
      Map.entry("APPLICATION", "application:"), Map.entry("MODULE", "module:"),
      Map.entry("PAGE", "page:"), Map.entry("UI_COMPONENT", "component:"),
      Map.entry("FIELD", "field:"), Map.entry("BUSINESS_RESOURCE", "business:"),
      Map.entry("EXTERNAL_RESOURCE", "external_resource:"), Map.entry("API_RESOURCE", "api:"),
      Map.entry("DATA_RESOURCE", "data:"), Map.entry("DATA_GOVERNANCE_RESOURCE", "governance:"));
  private static final Pattern KEY = Pattern.compile("^[a-z][a-z0-9_-]*:[a-z0-9][a-z0-9._/-]*$");
  private static final Pattern BUTTON = Pattern.compile(
      "(?i).*(?:create|delete|edit|export|approve|reject)[-_]?button.*");

  private final AccessRepository repository;
  private final AuthorizationSemanticsRegistry semantics;
  private final AuditTrail auditTrail;

  public AccessAdministrationService(AccessRepository repository,
      AuthorizationSemanticsRegistry semantics, AuditTrail auditTrail) {
    this.repository = repository;
    this.semantics = semantics;
    this.auditTrail = auditTrail;
  }

  public List<ResourceView> resources() { return repository.resources(); }
  public List<ActionView> actions() { return repository.actions(); }
  public List<UserView> users() { return repository.users(); }

  public List<GrantView> grants(String subjectType, UUID subjectId) {
    return repository.grants(normalizeSubjectType(subjectType), subjectId);
  }

  @Transactional
  public MutationResult createResource(ResourceCommand command, String actor) {
    validateResource(command, null);
    UUID id = UUID.randomUUID();
    repository.createResource(id, command, normalizeSource(command.source()));
    repository.enqueueParent(id, command.parentId(), "RESOURCE_PARENT_WRITE");
    audit(actor, "RESOURCE_CREATED", "resource", command.resourceKey());
    return new MutationResult(id, 0);
  }

  @Transactional
  public MutationResult updateResource(UUID id, long version, ResourceCommand command, String actor) {
    validateResource(command, id);
    ResourceSnapshot previous = repository.resource(id)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Resource not found"));
    if (!previous.resourceKey().equals(command.resourceKey())) {
      throw new IllegalArgumentException("resourceKey is immutable; use an explicit migration");
    }
    if (!Objects.equals(previous.parentId(), command.parentId())) {
      repository.enqueueParent(id, previous.parentId(), "RESOURCE_PARENT_DELETE");
    }
    if (repository.updateResource(id, version, command, normalizeSource(command.source())) != 1) {
      throw new OptimisticLockingFailureException("resource changed or missing");
    }
    if (!Objects.equals(previous.parentId(), command.parentId())) {
      repository.enqueueParent(id, command.parentId(), "RESOURCE_PARENT_WRITE");
    }
    audit(actor, "RESOURCE_UPDATED", "resource", command.resourceKey());
    return new MutationResult(id, version + 1);
  }

  @Transactional
  public MutationResult createAction(ActionCommand command, String actor) {
    UUID id = UUID.randomUUID();
    repository.createAction(id, command);
    audit(actor, "ACTION_CREATED", "action", command.actionKey());
    return new MutationResult(id, 0);
  }

  @Transactional
  public void attachAction(UUID resourceId, UUID actionId, String actor) {
    repository.attachAction(resourceId, actionId);
    audit(actor, "RESOURCE_ACTION_ATTACHED", "resource", resourceId.toString());
  }

  @Transactional
  public void detachAction(UUID resourceId, UUID actionId, String actor) {
    repository.detachAction(resourceId, actionId);
    audit(actor, "RESOURCE_ACTION_DETACHED", "resource", resourceId.toString());
  }

  @Transactional
  public MutationResult createUser(UserCommand command, String actor) {
    UUID id = UUID.randomUUID();
    repository.createUser(id, command);
    audit(actor, "USER_REGISTERED", "user", command.username());
    return new MutationResult(id, 0);
  }

  @Transactional
  public GrantResult grant(GrantCommand command, String actor) {
    String subjectType = normalizeSubjectType(command.subjectType() == null ? "USER" : command.subjectType());
    UUID subjectId = command.subjectId() != null ? command.subjectId() : command.userId();
    if (subjectId == null) throw new IllegalArgumentException("A subject is required");
    GrantTarget target = repository.grantTarget(command.resourceId(), command.actionId())
        .orElseThrow(() -> new IllegalArgumentException(
            "Action is not attached to an active resource"));
    String relation = semantics.resolve(target.resourceType(), target.actionKey()).relation();
    repository.archiveExpiredGrant(subjectType, subjectId, command.resourceId(), command.actionId());
    var existing = repository.activeGrant(subjectType, subjectId, command.resourceId(), command.actionId());
    if (existing.isPresent()) {
      return new GrantResult(existing.get().id(), existing.get().version(), true);
    }
    UUID id = UUID.randomUUID();
    repository.createGrant(id, subjectType, subjectId, command.resourceId(), command.actionId(),
        relation, command.expiresAt());
    repository.enqueueGrant(id, "GRANT_WRITE", 0);
    audit(actor, "GRANT_CREATED", "grant", id.toString());
    return new GrantResult(id, 0, false);
  }

  @Transactional
  public void revoke(UUID id, String actor) {
    if (!repository.isActiveGrant(id)) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Active grant not found");
    }
    repository.enqueueGrant(id, "GRANT_DELETE", 1);
    repository.archiveGrant(id);
    audit(actor, "GRANT_REVOKED", "grant", id.toString());
  }

  private void validateResource(ResourceCommand command, UUID currentId) {
    String type = command.type().toUpperCase(Locale.ROOT);
    String key = command.resourceKey();
    if (!RESOURCE_TYPES.contains(type)) {
      throw new IllegalArgumentException("unsupported resource type");
    }
    if (!KEY.matcher(key).matches() || !key.startsWith(PREFIXES.get(type))) {
      throw new IllegalArgumentException("resourceKey must be normalized and use its canonical prefix");
    }
    if (key.startsWith("http:") || key.startsWith("https:") || key.contains("/api/")
        || key.matches("(?i)^(GET|POST|PUT|PATCH|DELETE)-.*")) {
      throw new IllegalArgumentException("API routes are bindings, not resource identities");
    }
    if (type.equals("UI_COMPONENT") && BUTTON.matcher(key).matches()) {
      throw new IllegalArgumentException(
          "buttons that execute actions are not resources; attach the action to the business resource");
    }
    if (type.equals("EXTERNAL_RESOURCE") && (blank(command.externalSystem())
        || blank(command.externalType()) || blank(command.externalId()))) {
      throw new IllegalArgumentException(
          "external resources require provider, external type, and external id");
    }
    if (currentId != null && currentId.equals(command.parentId())) {
      throw new IllegalArgumentException("resource cannot be its own parent");
    }
    if (command.parentId() != null && !repository.resourceExists(command.parentId())) {
      throw new IllegalArgumentException("parent resource does not exist");
    }
    if (currentId != null && command.parentId() != null
        && repository.resourceHierarchyContains(command.parentId(), currentId)) {
      throw new IllegalArgumentException("resource hierarchy cannot contain a cycle");
    }
  }

  private String normalizeSubjectType(String value) {
    String normalized = value == null ? "" : value.toUpperCase(Locale.ROOT);
    if (!SUBJECT_TYPES.contains(normalized)) {
      throw new IllegalArgumentException("subjectType must be USER, GROUP, ACCESS_GROUP, or ROLE");
    }
    return normalized;
  }

  private static String normalizeSource(String value) {
    String normalized = blank(value) ? "ADMIN" : value.toUpperCase(Locale.ROOT);
    if (!SOURCES.contains(normalized)) throw new IllegalArgumentException("unsupported resource source");
    return normalized;
  }

  private void audit(String actor, String event, String type, String key) {
    repository.appendAudit(actor, event, type, key);
    auditTrail.success("ACCESS_CONTROL", event.toLowerCase(Locale.ROOT).replace('_', '.'),
        null, null, type, key, key, event, null, Map.of("target", key));
  }

  private static boolean blank(String value) { return value == null || value.isBlank(); }
}

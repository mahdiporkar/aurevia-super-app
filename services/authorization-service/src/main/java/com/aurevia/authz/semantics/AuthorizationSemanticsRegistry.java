package com.aurevia.authz.semantics;

import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Component;

/** The only supported business-action mapping for grants and runtime checks. */
@Component
public final class AuthorizationSemanticsRegistry {
  private static final Map<ObjectType, Map<String, Semantics>> RULES = Map.of(
      ObjectType.APPLICATION, applicationRules(),
      ObjectType.RESOURCE, resourceRules(),
      ObjectType.EXTERNAL_RESOURCE, externalResourceRules());

  public Semantics resolve(String objectType, String action) {
    ObjectType type = ObjectType.from(objectType);
    String normalized = normalizeAction(action);
    Semantics result = RULES.get(type).get(normalized);
    if (result == null) {
      throw new IllegalArgumentException(
          "Action '" + normalized + "' is not valid for " + type.externalName);
    }
    return result;
  }

  public Semantics resolveObject(String object, String action) {
    if (object == null || !object.contains(":")) {
      throw new IllegalArgumentException("Canonical OpenFGA object is required");
    }
    return resolve(object.substring(0, object.indexOf(':')), action);
  }

  private static String normalizeAction(String action) {
    if (action == null || action.isBlank()) throw new IllegalArgumentException("Action is required");
    String normalized = action.trim().toLowerCase(Locale.ROOT);
    if (normalized.startsWith("can_")) {
      throw new IllegalArgumentException("Computed permissions are not business actions");
    }
    return normalized;
  }

  private static Semantics semantics(String relation, String permission) {
    return new Semantics(relation, permission);
  }

  private static Map<String, Semantics> applicationRules() {
    return Map.ofEntries(
        entry("access", "viewer", "can_view"),
        entry("view", "viewer", "can_view"),
        entry("list", "viewer", "can_view"),
        entry("view_api", "viewer", "can_view"),
        entry("view_errors", "viewer", "can_view"),
        entry("create", "manager", "can_create"),
        entry("import", "manager", "can_create"),
        entry("upload", "manager", "can_create"),
        entry("update", "manager", "can_edit"),
        entry("approve", "manager", "can_edit"),
        entry("reject", "manager", "can_edit"),
        entry("delete", "manager", "can_delete"),
        entry("share", "manager", "can_share"),
        entry("export", "manager", "can_export"),
        entry("download", "manager", "can_export"),
        entry("admin", "manager", "can_manage"),
        entry("manage", "manager", "can_manage"),
        entry("view_audit", "manager", "can_manage"),
        entry("activate", "manager", "can_manage"),
        entry("assign", "manager", "can_manage"),
        entry("execute", "manager", "can_manage"),
        entry("test", "manager", "can_manage"),
        entry("invalidate-token", "manager", "can_manage"),
        entry("update-credential-reference", "manager", "can_manage"));
  }

  private static Map<String, Semantics> resourceRules() {
    return Map.ofEntries(
        entry("access", "viewer", "can_view"),
        entry("view", "viewer", "can_view"),
        entry("list", "viewer", "can_view"),
        entry("view_api", "viewer", "can_view"),
        entry("view_errors", "viewer", "can_view"),
        entry("create", "creator", "can_create"),
        entry("import", "creator", "can_create"),
        entry("upload", "creator", "can_create"),
        entry("update", "editor", "can_edit"),
        entry("approve", "editor", "can_edit"),
        entry("reject", "editor", "can_edit"),
        entry("delete", "deleter", "can_delete"),
        entry("share", "manager", "can_share"),
        entry("export", "manager", "can_export"),
        entry("download", "manager", "can_export"),
        entry("admin", "manager", "can_manage"),
        entry("manage", "manager", "can_manage"),
        entry("view_audit", "manager", "can_manage"),
        entry("activate", "manager", "can_manage"),
        entry("assign", "manager", "can_manage"),
        entry("execute", "manager", "can_manage"),
        entry("test", "manager", "can_manage"),
        entry("invalidate-token", "manager", "can_manage"),
        entry("update-credential-reference", "manager", "can_manage"));
  }

  private static Map<String, Semantics> externalResourceRules() {
    return Map.ofEntries(
        entry("access", "viewer", "can_view"),
        entry("view", "viewer", "can_view"),
        entry("list", "viewer", "can_view"),
        entry("view_api", "viewer", "can_view"),
        entry("view_errors", "viewer", "can_view"),
        entry("create", "manager", "can_create"),
        entry("import", "manager", "can_create"),
        entry("upload", "manager", "can_create"),
        entry("update", "editor", "can_edit"),
        entry("approve", "editor", "can_edit"),
        entry("reject", "editor", "can_edit"),
        entry("delete", "manager", "can_delete"),
        entry("share", "sharer", "can_share"),
        entry("export", "exporter", "can_export"),
        entry("download", "exporter", "can_export"),
        entry("admin", "manager", "can_manage"),
        entry("manage", "manager", "can_manage"),
        entry("view_audit", "manager", "can_manage"),
        entry("activate", "manager", "can_manage"),
        entry("assign", "manager", "can_manage"),
        entry("execute", "manager", "can_manage"),
        entry("test", "manager", "can_manage"),
        entry("invalidate-token", "manager", "can_manage"),
        entry("update-credential-reference", "manager", "can_manage"));
  }

  private static Map.Entry<String, Semantics> entry(String action, String relation,
      String permission) {
    return Map.entry(action, semantics(relation, permission));
  }

  private enum ObjectType {
    APPLICATION("APPLICATION"), RESOURCE("RESOURCE"), EXTERNAL_RESOURCE("EXTERNAL_RESOURCE");

    private final String externalName;
    ObjectType(String externalName) { this.externalName = externalName; }

    static ObjectType from(String value) {
      if (value == null) throw new IllegalArgumentException("Object type is required");
      String normalized = value.trim().toUpperCase(Locale.ROOT);
      if (normalized.equals("APPLICATION")) return APPLICATION;
      if (normalized.equals("EXTERNAL_RESOURCE")) return EXTERNAL_RESOURCE;
      if (normalized.equals("RESOURCE") || normalized.equals("MODULE")
          || normalized.equals("PAGE") || normalized.equals("UI_COMPONENT")
          || normalized.equals("FIELD")
          || normalized.equals("API_RESOURCE") || normalized.equals("BUSINESS_RESOURCE")
          || normalized.equals("DATA_RESOURCE") || normalized.equals("DATA_GOVERNANCE_RESOURCE")) {
        return RESOURCE;
      }
      throw new IllegalArgumentException("Unsupported OpenFGA object type: " + value);
    }
  }

  public record Semantics(String relation, String permission) {}
}

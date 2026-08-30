package com.aurevia.authz.semantics;

import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Component;

/** The only supported business-action mapping for grants and runtime checks. */
@Component
public final class AuthorizationSemanticsRegistry {
  private static final Map<ObjectType, Map<String, Semantics>> RULES = Map.of(
      ObjectType.APPLICATION, Map.of(
          "view", semantics("viewer", "can_view"),
          "list", semantics("viewer", "can_view"),
          "admin", semantics("manager", "can_manage"),
          "manage", semantics("manager", "can_manage")),
      ObjectType.RESOURCE, Map.ofEntries(
          Map.entry("view", semantics("viewer", "can_view")),
          Map.entry("list", semantics("viewer", "can_view")),
          Map.entry("view_api", semantics("viewer", "can_view")),
          Map.entry("view_errors", semantics("viewer", "can_view")),
          Map.entry("view_audit", semantics("manager", "can_manage")),
          Map.entry("create", semantics("creator", "can_create")),
          Map.entry("update", semantics("editor", "can_edit")),
          Map.entry("approve", semantics("editor", "can_edit")),
          Map.entry("reject", semantics("editor", "can_edit")),
          Map.entry("delete", semantics("deleter", "can_delete")),
          Map.entry("admin", semantics("manager", "can_manage")),
          Map.entry("manage", semantics("manager", "can_manage"))),
      ObjectType.EXTERNAL_RESOURCE, Map.ofEntries(
          Map.entry("view", semantics("viewer", "can_view")),
          Map.entry("list", semantics("viewer", "can_view")),
          Map.entry("update", semantics("editor", "can_edit")),
          Map.entry("approve", semantics("editor", "can_edit")),
          Map.entry("reject", semantics("editor", "can_edit")),
          Map.entry("delete", semantics("manager", "can_delete")),
          Map.entry("share", semantics("sharer", "can_share")),
          Map.entry("export", semantics("exporter", "can_export")),
          Map.entry("admin", semantics("manager", "can_manage")),
          Map.entry("manage", semantics("manager", "can_manage"))));

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
          || normalized.equals("API_RESOURCE") || normalized.equals("BUSINESS_RESOURCE")) {
        return RESOURCE;
      }
      throw new IllegalArgumentException("Unsupported OpenFGA object type: " + value);
    }
  }

  public record Semantics(String relation, String permission) {}
}

package com.aurevia.authz.semantics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class AuthorizationSemanticsRegistryTest {
  private final AuthorizationSemanticsRegistry registry = new AuthorizationSemanticsRegistry();

  @ParameterizedTest
  @MethodSource("validMappings")
  void resolvesEverySupportedCombination(String type, String action, String relation,
      String permission) {
    assertThat(registry.resolve(type, action))
        .isEqualTo(new AuthorizationSemanticsRegistry.Semantics(relation, permission));
  }

  static Stream<Arguments> validMappings() {
    return Stream.of(
        Arguments.of("APPLICATION", "view", "viewer", "can_view"),
        Arguments.of("APPLICATION", "access", "viewer", "can_view"),
        Arguments.of("APPLICATION", "list", "viewer", "can_view"),
        Arguments.of("APPLICATION", "create", "manager", "can_create"),
        Arguments.of("APPLICATION", "share", "manager", "can_share"),
        Arguments.of("APPLICATION", "admin", "manager", "can_manage"),
        Arguments.of("APPLICATION", "manage", "manager", "can_manage"),
        Arguments.of("RESOURCE", "view", "viewer", "can_view"),
        Arguments.of("BUSINESS_RESOURCE", "create", "creator", "can_create"),
        Arguments.of("API_RESOURCE", "update", "editor", "can_edit"),
        Arguments.of("DATA_RESOURCE", "view", "viewer", "can_view"),
        Arguments.of("DATA_GOVERNANCE_RESOURCE", "admin", "manager", "can_manage"),
        Arguments.of("RESOURCE", "approve", "editor", "can_edit"),
        Arguments.of("RESOURCE", "reject", "editor", "can_edit"),
        Arguments.of("RESOURCE", "delete", "deleter", "can_delete"),
        Arguments.of("RESOURCE", "export", "manager", "can_export"),
        Arguments.of("API_RESOURCE", "test", "manager", "can_manage"),
        Arguments.of("API_RESOURCE", "activate", "manager", "can_manage"),
        Arguments.of("API_RESOURCE", "invalidate-token", "manager", "can_manage"),
        Arguments.of("RESOURCE", "admin", "manager", "can_manage"),
        Arguments.of("EXTERNAL_RESOURCE", "view", "viewer", "can_view"),
        Arguments.of("EXTERNAL_RESOURCE", "create", "manager", "can_create"),
        Arguments.of("EXTERNAL_RESOURCE", "update", "editor", "can_edit"),
        Arguments.of("EXTERNAL_RESOURCE", "share", "sharer", "can_share"),
        Arguments.of("EXTERNAL_RESOURCE", "export", "exporter", "can_export"),
        Arguments.of("EXTERNAL_RESOURCE", "download", "exporter", "can_export"),
        Arguments.of("EXTERNAL_RESOURCE", "delete", "manager", "can_delete"),
        Arguments.of("EXTERNAL_RESOURCE", "manage", "manager", "can_manage"));
  }

  @Test
  void rejectsUnknownAndInvalidCombinations() {
    assertThatIllegalArgumentException().isThrownBy(() -> registry.resolve("RESOURCE", "fly"));
    assertThatIllegalArgumentException().isThrownBy(() -> registry.resolve("RESOURCE", "can_view"));
    assertThatIllegalArgumentException().isThrownBy(() -> registry.resolve("UNKNOWN", "view"));
  }

  @Test
  void mapsTheCompleteBusinessActionCatalogForEveryOpenFgaObjectFamily() {
    List<String> actions = List.of("access", "activate", "admin", "approve", "assign",
        "create", "delete", "download", "execute", "export", "import",
        "invalidate-token", "list", "manage", "reject", "share", "test", "update",
        "update-credential-reference", "upload", "view", "view_api", "view_audit",
        "view_errors");

    for (String type : List.of("APPLICATION", "RESOURCE", "EXTERNAL_RESOURCE")) {
      for (String action : actions) {
        assertThat(registry.resolve(type, action).permission()).startsWith("can_");
      }
    }
  }

  @Test
  void derivesTypeFromCanonicalRuntimeObject() {
    assertThat(registry.resolveObject("external_resource:superset/dashboard/1", "share").relation())
        .isEqualTo("sharer");
    assertThat(registry.resolveObject("application:aurevia", "view").permission())
        .isEqualTo("can_view");
    assertThat(registry.resolveObject("resource:hr.employee", "approve").permission())
        .isEqualTo("can_edit");
  }
}

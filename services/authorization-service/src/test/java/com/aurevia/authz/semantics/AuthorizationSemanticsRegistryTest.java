package com.aurevia.authz.semantics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

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
        Arguments.of("APPLICATION", "list", "viewer", "can_view"),
        Arguments.of("APPLICATION", "admin", "manager", "can_manage"),
        Arguments.of("APPLICATION", "manage", "manager", "can_manage"),
        Arguments.of("RESOURCE", "view", "viewer", "can_view"),
        Arguments.of("BUSINESS_RESOURCE", "create", "creator", "can_create"),
        Arguments.of("API_RESOURCE", "update", "editor", "can_edit"),
        Arguments.of("RESOURCE", "approve", "editor", "can_edit"),
        Arguments.of("RESOURCE", "reject", "editor", "can_edit"),
        Arguments.of("RESOURCE", "delete", "deleter", "can_delete"),
        Arguments.of("RESOURCE", "admin", "manager", "can_manage"),
        Arguments.of("EXTERNAL_RESOURCE", "view", "viewer", "can_view"),
        Arguments.of("EXTERNAL_RESOURCE", "update", "editor", "can_edit"),
        Arguments.of("EXTERNAL_RESOURCE", "share", "sharer", "can_share"),
        Arguments.of("EXTERNAL_RESOURCE", "export", "exporter", "can_export"),
        Arguments.of("EXTERNAL_RESOURCE", "delete", "manager", "can_delete"),
        Arguments.of("EXTERNAL_RESOURCE", "manage", "manager", "can_manage"));
  }

  @Test
  void rejectsUnknownAndInvalidCombinations() {
    assertThatIllegalArgumentException().isThrownBy(() -> registry.resolve("RESOURCE", "fly"));
    assertThatIllegalArgumentException().isThrownBy(() -> registry.resolve("APPLICATION", "create"));
    assertThatIllegalArgumentException().isThrownBy(() -> registry.resolve("EXTERNAL_RESOURCE", "create"));
    assertThatIllegalArgumentException().isThrownBy(() -> registry.resolve("APPLICATION", "share"));
    assertThatIllegalArgumentException().isThrownBy(() -> registry.resolve("RESOURCE", "export"));
    assertThatIllegalArgumentException().isThrownBy(() -> registry.resolve("RESOURCE", "can_view"));
    assertThatIllegalArgumentException().isThrownBy(() -> registry.resolve("UNKNOWN", "view"));
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

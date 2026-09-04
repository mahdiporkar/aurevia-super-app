package com.aurevia.authz.semantics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import org.junit.jupiter.api.Test;

class ResourceObjectKeyTest {
  @Test
  void convertsTypedCatalogKeysToOpenFgaObjects() {
    assertThat(ResourceObjectKey.from("APPLICATION", "application:aurevia/admin"))
        .isEqualTo("application:aurevia/admin");
    assertThat(ResourceObjectKey.from("BUSINESS_RESOURCE", "business:hr.employee"))
        .isEqualTo("resource:business/hr.employee");
    assertThat(ResourceObjectKey.from("EXTERNAL_RESOURCE", "external_resource:superset:1"))
        .isEqualTo("external_resource:superset/1");
  }

  @Test
  void keepsUpgradeCompatibilityWithStableLegacyDottedKeys() {
    assertThat(ResourceObjectKey.from("BUSINESS_RESOURCE", "hr.employee"))
        .isEqualTo("resource:hr.employee");
    assertThat(ResourceObjectKey.from("API_RESOURCE", "proxy.route"))
        .isEqualTo("resource:proxy.route");
  }

  @Test
  void rejectsMissingTypeOrKey() {
    assertThatIllegalArgumentException().isThrownBy(() -> ResourceObjectKey.from("", "hr.employee"));
    assertThatIllegalArgumentException().isThrownBy(() -> ResourceObjectKey.from("RESOURCE", " "));
  }
}

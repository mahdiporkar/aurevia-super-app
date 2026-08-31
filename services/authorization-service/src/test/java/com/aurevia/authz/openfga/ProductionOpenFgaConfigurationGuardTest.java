package com.aurevia.authz.openfga;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.Test;

class ProductionOpenFgaConfigurationGuardTest {
  @Test
  void rejectsMissingOrBootstrapIdentifiers() {
    assertThrows(IllegalStateException.class,
        () -> ProductionOpenFgaConfigurationGuard.requireProductionValue("OPENFGA_STORE_ID", ""));
    assertThrows(IllegalStateException.class,
        () -> ProductionOpenFgaConfigurationGuard.requireProductionValue(
            "OPENFGA_MODEL_ID", "created-by-model-deployment"));
  }

  @Test
  void acceptsAnExplicitPinnedIdentifier() {
    assertDoesNotThrow(() -> ProductionOpenFgaConfigurationGuard.requireProductionValue(
        "OPENFGA_MODEL_ID", "01M17FCAYW1S6Z347FDR025TND"));
  }
}

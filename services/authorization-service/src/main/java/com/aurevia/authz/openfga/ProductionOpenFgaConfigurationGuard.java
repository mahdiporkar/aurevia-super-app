package com.aurevia.authz.openfga;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/** Prevents a production instance from starting against an implicit or mutable OpenFGA model. */
@Component
@Profile("prod")
final class ProductionOpenFgaConfigurationGuard {
  ProductionOpenFgaConfigurationGuard(
      @Value("${aurevia.openfga.base-url}") String baseUrl,
      @Value("${aurevia.openfga.store-id}") String storeId,
      @Value("${aurevia.openfga.model-id}") String modelId) {
    requireProductionValue("OPENFGA_STORE_ID", storeId);
    requireProductionValue("OPENFGA_MODEL_ID", modelId);
    if (!baseUrl.startsWith("https://")) {
      throw new IllegalStateException("OPENFGA_URL must use HTTPS in the prod profile");
    }
  }

  static void requireProductionValue(String name, String value) {
    if (value == null || value.isBlank() || value.contains("bootstrap-required")
        || value.contains("created-by-")) {
      throw new IllegalStateException(name + " must be an explicit pinned production value");
    }
  }
}

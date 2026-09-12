package com.aurevia.authz.openfga;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Prevents unsafe OpenFGA production configuration.
 *
 * <p>Production requires a pinned store/model and HTTPS. Startup reconciliation is additionally
 * protected because repair mode may delete OpenFGA tuples that are not present in the relational
 * source of truth.</p>
 */
@Component
@Profile("prod")
final class ProductionOpenFgaConfigurationGuard {

  ProductionOpenFgaConfigurationGuard(
      @Value("${aurevia.openfga.base-url}") String baseUrl,
      @Value("${aurevia.openfga.store-id}") String storeId,
      @Value("${aurevia.openfga.model-id}") String modelId,
      @Value("${aurevia.openfga.reconcile-on-startup:false}")
          boolean reconcileOnStartup,
      @Value("${aurevia.openfga.allow-destructive-reconcile-on-startup:false}")
          boolean allowDestructiveReconcileOnStartup
  ) {

    requireProductionValue(
        "OPENFGA_STORE_ID",
        storeId
    );

    requireProductionValue(
        "OPENFGA_MODEL_ID",
        modelId
    );

    if (!baseUrl.startsWith("https://")) {
      throw new IllegalStateException(
          "OPENFGA_URL must use HTTPS in the prod profile"
      );
    }

    requireSafeStartupReconciliation(
        reconcileOnStartup,
        allowDestructiveReconcileOnStartup
    );
  }

  static void requireProductionValue(
      String name,
      String value
  ) {

    if (value == null
        || value.isBlank()
        || value.contains("bootstrap-required")
        || value.contains("created-by-")) {

      throw new IllegalStateException(
          name + " must be an explicit pinned production value"
      );
    }
  }

  static void requireSafeStartupReconciliation(
      boolean reconcileOnStartup,
      boolean allowDestructiveReconcileOnStartup
  ) {

    if (reconcileOnStartup
        && !allowDestructiveReconcileOnStartup) {

      throw new IllegalStateException(
          "OPENFGA_RECONCILE_ON_STARTUP=true in prod requires "
              + "OPENFGA_ALLOW_DESTRUCTIVE_RECONCILE_ON_STARTUP=true "
              + "because startup repair may delete unexpected OpenFGA tuples"
      );
    }
  }
}

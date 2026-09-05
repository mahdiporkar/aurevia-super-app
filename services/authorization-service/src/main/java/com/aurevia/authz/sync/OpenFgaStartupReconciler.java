package com.aurevia.authz.sync;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Repairs projection drift when an environment explicitly declares the relational database as
 * the startup source of truth. Disabled by default so production operators keep full control of
 * reconciliation timing; the local Compose stack enables it for deterministic fresh installs.
 */
@Component
@ConditionalOnProperty(name="aurevia.openfga.reconcile-on-startup",havingValue="true")
public final class OpenFgaStartupReconciler {
  private static final Logger log=LoggerFactory.getLogger(OpenFgaStartupReconciler.class);
  private final OpenFgaReconciliationService reconciliation;

  public OpenFgaStartupReconciler(OpenFgaReconciliationService reconciliation) {
    this.reconciliation=reconciliation;
  }

  @EventListener(ApplicationReadyEvent.class)
  public void repairAndVerify() {
    var repair=reconciliation.reconcile(true);
    var verification=reconciliation.reconcile(false);
    if(!verification.missing().isEmpty()||!verification.unexpected().isEmpty()) {
      throw new IllegalStateException("OpenFGA startup reconciliation left projection drift: missing="
          +verification.missing().size()+", unexpected="+verification.unexpected().size());
    }
    log.info("OpenFGA startup reconciliation completed: expected={}, actual={}, repaired={}",
        verification.expectedCount(),verification.actualCount(),repair.repairedCount());
  }
}

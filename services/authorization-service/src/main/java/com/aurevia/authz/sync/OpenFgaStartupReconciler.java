package com.aurevia.authz.sync;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Repairs projection drift when an environment explicitly declares the
 * relational database as the startup source of truth.
 *
 * Disabled by default so production operators keep full control of
 * reconciliation timing; the local Compose stack enables it for
 * deterministic fresh installs.
 */
@Component
@ConditionalOnProperty(
    name = "aurevia.openfga.reconcile-on-startup",
    havingValue = "true"
)
public final class OpenFgaStartupReconciler {

  private static final Logger log =
      LoggerFactory.getLogger(OpenFgaStartupReconciler.class);

  private static final long EMPTY_BATCH_SLEEP_MS = 50L;

  private final OpenFgaReconciliationService reconciliation;
  private final OutboxReconciler outbox;
  private final OutboxMetricsRepository metrics;
  private final long startupOutboxTimeoutMs;

  public OpenFgaStartupReconciler(
      OpenFgaReconciliationService reconciliation,
      OutboxReconciler outbox,
      OutboxMetricsRepository metrics,
      @Value("${aurevia.openfga.startup-outbox-timeout-ms:60000}")
          long startupOutboxTimeoutMs
  ) {

    this.reconciliation = reconciliation;
    this.outbox = outbox;
    this.metrics = metrics;
    this.startupOutboxTimeoutMs =
        startupOutboxTimeoutMs;
  }

  @EventListener(ApplicationReadyEvent.class)
  public void repairAndVerify() {

    /*
     * A fresh database may contain historical bootstrap events.
     *
     * Drain them before treating relational tables as the source of
     * truth. Using a time-based deadline avoids the old fixed
     * "100 loops" behavior that could fail while another worker was
     * legitimately progressing.
     */
    drainStartupOutbox();

    /*
     * Database -> OpenFGA repair pass.
     */
    var repair =
        reconciliation.reconcile(true);

    /*
     * Verification pass must not modify OpenFGA.
     */
    var verification =
        reconciliation.reconcile(false);

    if (!verification.missing().isEmpty()
        || !verification.unexpected().isEmpty()) {

      throw new IllegalStateException(
          "OpenFGA startup reconciliation left projection drift: "
              + "missing="
              + verification.missing().size()
              + ", unexpected="
              + verification.unexpected().size()
      );
    }

    log.info(
        "OpenFGA startup reconciliation completed: "
            + "expected={}, actual={}, repaired={}",
        verification.expectedCount(),
        verification.actualCount(),
        repair.repairedCount()
    );
  }

  private void drainStartupOutbox() {

    long deadline =
        System.currentTimeMillis()
            + startupOutboxTimeoutMs;

    int batches = 0;
    long claimedEvents = 0;

    while (metrics.pending() > 0
        && metrics.deadLettered() == 0
        && System.currentTimeMillis() < deadline) {

      int claimed =
          outbox.reconcileBatch();

      batches++;
      claimedEvents += claimed;

      /*
       * If another thread/instance temporarily owns an earlier event,
       * avoid spinning through the loop hundreds of times in a few
       * milliseconds.
       */
      if (claimed == 0) {
        sleepBriefly();
      }
    }

    long pending =
        (long) metrics.pending();

    long deadLettered =
        (long) metrics.deadLettered();

    if (pending > 0 || deadLettered > 0) {

      throw new IllegalStateException(
          "OpenFGA startup outbox did not drain: "
              + "pending="
              + pending
              + ", deadLettered="
              + deadLettered
              + ", batches="
              + batches
              + ", claimedEvents="
              + claimedEvents
              + ", timeoutMs="
              + startupOutboxTimeoutMs
      );
    }

    log.info(
        "OpenFGA startup outbox drained: "
            + "batches={}, claimedEvents={}",
        batches,
        claimedEvents
    );
  }

  private static void sleepBriefly() {

    try {

      Thread.sleep(
          EMPTY_BATCH_SLEEP_MS
      );

    } catch (InterruptedException interrupted) {

      Thread.currentThread().interrupt();

      throw new IllegalStateException(
          "Interrupted while draining OpenFGA startup outbox",
          interrupted
      );
    }
  }
}

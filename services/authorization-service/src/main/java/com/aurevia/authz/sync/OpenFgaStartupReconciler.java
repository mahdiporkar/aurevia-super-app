package com.aurevia.authz.sync;

import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Repairs OpenFGA projection drift when an environment explicitly declares the relational database
 * as the startup source of truth.
 *
 * <p>The component is disabled by default. Local deterministic environments may enable it, while
 * production requires an explicit safety opt-in guarded by
 * ProductionOpenFgaConfigurationGuard.</p>
 *
 * <p>ApplicationRunner is intentionally used instead of ApplicationReadyEvent so Spring does not
 * declare the application ready before authorization projection verification has completed.</p>
 */
@Component
@ConditionalOnProperty(
    name = "aurevia.openfga.reconcile-on-startup",
    havingValue = "true"
)
public final class OpenFgaStartupReconciler implements ApplicationRunner {

  private static final Logger log =
      LoggerFactory.getLogger(OpenFgaStartupReconciler.class);

  private static final Duration EMPTY_BATCH_SLEEP =
      Duration.ofMillis(50);

  private final OpenFgaReconciliationService reconciliation;
  private final OutboxReconciler outbox;
  private final OutboxMetricsRepository metrics;
  private final Duration startupOutboxTimeout;

  public OpenFgaStartupReconciler(
      OpenFgaReconciliationService reconciliation,
      OutboxReconciler outbox,
      OutboxMetricsRepository metrics,
      @Value("${aurevia.openfga.startup-outbox-timeout:180s}")
          Duration startupOutboxTimeout,
      @Value("${aurevia.outbox.claim-timeout-seconds:120}")
          int claimTimeoutSeconds
  ) {

    if (startupOutboxTimeout == null
        || startupOutboxTimeout.isZero()
        || startupOutboxTimeout.isNegative()) {

      throw new IllegalArgumentException(
          "aurevia.openfga.startup-outbox-timeout must be positive"
      );
    }

    if (claimTimeoutSeconds < 0) {
      throw new IllegalArgumentException(
          "aurevia.outbox.claim-timeout-seconds must not be negative"
      );
    }

    /*
     * A previous process may die after claiming an event.
     * Startup must remain alive long enough for that stale claim to expire.
     */
    Duration claimTimeout =
        Duration.ofSeconds(claimTimeoutSeconds);

    if (startupOutboxTimeout.compareTo(claimTimeout) <= 0) {

      throw new IllegalArgumentException(
          "aurevia.openfga.startup-outbox-timeout must be greater than "
              + "aurevia.outbox.claim-timeout-seconds"
      );
    }

    this.reconciliation = reconciliation;
    this.outbox = outbox;
    this.metrics = metrics;
    this.startupOutboxTimeout = startupOutboxTimeout;
  }

  @Override
  public void run(ApplicationArguments args) {
    repairAndVerify();
  }

  void repairAndVerify() {

    /*
     * Drain historical/bootstrap events first.
     *
     * This prevents an old outbox event from being replayed after the full
     * relational DB -> OpenFGA repair.
     */
    drainStartupOutbox();

    /*
     * Repair:
     * relational authorization state is treated as the desired projection.
     */
    var repair =
        reconciliation.reconcile(true);

    /*
     * Verify:
     * second pass must observe zero remaining drift.
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

    /*
     * Only after a clean verification can the periodic outbox worker start.
     */
    outbox.markStartupReconciliationComplete();

    log.info(
        "OpenFGA startup reconciliation completed: "
            + "expected={}, actual={}, repaired={}",
        verification.expectedCount(),
        verification.actualCount(),
        repair.repairedCount()
    );
  }

  private void drainStartupOutbox() {

    long timeoutNanos =
        startupOutboxTimeout.toNanos();

    long started =
        System.nanoTime();

    int batches = 0;
    long claimedEvents = 0;

    while (metrics.pending() > 0
        && metrics.deadLettered() == 0
        && System.nanoTime() - started < timeoutNanos) {

      int claimed =
          outbox.reconcileBatch();

      batches++;
      claimedEvents += claimed;

      /*
       * Empty does not necessarily mean stuck.
       *
       * Another process may temporarily own the earliest event of an aggregate,
       * so avoid a CPU spin-loop and give stale claims / concurrent workers time
       * to make progress.
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
              + ", timeout="
              + startupOutboxTimeout
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
          EMPTY_BATCH_SLEEP.toMillis()
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

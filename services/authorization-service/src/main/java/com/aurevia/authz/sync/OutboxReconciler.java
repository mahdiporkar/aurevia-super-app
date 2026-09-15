package com.aurevia.authz.sync;

import com.aurevia.authz.openfga.RelationshipAuthorizationPort;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Projects relational authorization changes to OpenFGA through the transactional outbox.
 *
 * <p>Rows are claimed transactionally by {@link OutboxRepository}. Network I/O is intentionally
 * performed after the claim transaction has completed.</p>
 *
 * <p>When startup reconciliation is enabled, the normal scheduled worker remains disabled until
 * startup drain + full OpenFGA repair + verification have completed successfully. This avoids
 * startup/scheduler races inside the same JVM. Database locking continues to protect concurrent
 * application instances.</p>
 */
@Component
public final class OutboxReconciler {

  private static final int BATCH_SIZE = 50;

  private static final List<String> WRITES = List.of(
      "GRANT_WRITE",
      "ROLE_ASSIGNMENT_WRITE",
      "RESOURCE_PARENT_WRITE",
      "GROUP_MEMBERSHIP_WRITE",
      "ACCESS_GROUP_MEMBERSHIP_WRITE",
      "APPLICATION_GROUP_GRANT_WRITE"
  );

  private static final List<String> DELETES = List.of(
      "GRANT_DELETE",
      "ROLE_ASSIGNMENT_DELETE",
      "RESOURCE_PARENT_DELETE",
      "GROUP_MEMBERSHIP_DELETE",
      "ACCESS_GROUP_MEMBERSHIP_DELETE",
      "APPLICATION_GROUP_GRANT_DELETE"
  );

  private final OutboxRepository outbox;
  private final RelationshipAuthorizationPort relationships;
  private final int maxAttempts;
  private final int claimTimeoutSeconds;
  private final Timer projectionLatency;

  /**
   * False while startup reconciliation owns the outbox.
   * AtomicBoolean makes the transition safely visible to scheduler threads.
   */
  private final AtomicBoolean scheduledReconciliationEnabled;

  public OutboxReconciler(
      OutboxRepository outbox,
      RelationshipAuthorizationPort relationships,
      @Value("${aurevia.outbox.max-attempts:12}") int maxAttempts,
      @Value("${aurevia.outbox.claim-timeout-seconds:120}") int claimTimeoutSeconds,
      MeterRegistry metrics,
      @Value("${aurevia.openfga.reconcile-on-startup:false}") boolean reconcileOnStartup
  ) {
    this.outbox = outbox;
    this.relationships = relationships;
    this.maxAttempts = maxAttempts;
    this.claimTimeoutSeconds = claimTimeoutSeconds;
    this.projectionLatency =
        metrics.timer("aurevia.openfga.projection.latency");

    this.scheduledReconciliationEnabled =
        new AtomicBoolean(!reconcileOnStartup);
  }

  /**
   * Normal background projection worker.
   *
   * <p>If startup reconciliation is enabled, this method deliberately does nothing until
   * {@link OpenFgaStartupReconciler} completes successfully.</p>
   */
  @Scheduled(fixedDelayString = "${aurevia.outbox.interval-ms:5000}")
  public void reconcile() {

    if (!scheduledReconciliationEnabled.get()) {
      return;
    }

    reconcileBatch();
  }

  /**
   * Processes one claimable batch.
   *
   * <p>synchronized is defense-in-depth for calls inside a single JVM. PostgreSQL locking in the
   * repository remains responsible for coordination between different application instances.</p>
   *
   * @return number of rows claimed in this batch
   */
  public synchronized int reconcileBatch() {

    UUID owner = UUID.randomUUID();

    List<OutboxRepository.Event> events =
        outbox.claim(owner, claimTimeoutSeconds, BATCH_SIZE);

    for (OutboxRepository.Event event : events) {
      process(event, owner);
    }

    return events.size();
  }

  /**
   * Enables the normal scheduled worker after startup reconciliation has completed successfully.
   */
  void markStartupReconciliationComplete() {
    scheduledReconciliationEnabled.set(true);
  }

  private void process(
      OutboxRepository.Event event,
      UUID owner
  ) {

    /*
     * PANEL events intentionally have no OpenFGA projection.
     */
    if (event.eventType().startsWith("PANEL_")) {
      markApplied(event.id(), owner);
      return;
    }

    try {

      if (WRITES.contains(event.eventType())) {

        requireTuple(event);

        projectionLatency.record(() ->
            relationships.write(
                event.user(),
                event.relation(),
                event.object()
            )
        );

      } else if (DELETES.contains(event.eventType())) {

        requireTuple(event);

        projectionLatency.record(() ->
            relationships.delete(
                event.user(),
                event.relation(),
                event.object()
            )
        );

      } else {

        retry(
            event.id(),
            owner,
            "No projection adapter for event " + event.eventType()
        );

        return;
      }

    } catch (RuntimeException failure) {

      /*
       * OpenFGA / projection failure:
       * preserve the event and let the outbox retry policy handle it.
       */
      retry(
          event.id(),
          owner,
          safeMessage(failure)
      );

      return;
    }

    /*
     * Keep markApplied outside the projection exception handler.
     *
     * Losing ownership of an outbox claim is a coordination/integrity problem,
     * not an OpenFGA projection failure and must not be silently converted into
     * another retry.
     */
    markApplied(event.id(), owner);
  }

  private void markApplied(UUID id, UUID owner) {

    if (!outbox.markApplied(id, owner, maxAttempts)) {
      throw new IllegalStateException(
          "Outbox claim was lost before completion"
      );
    }
  }

  private void retry(
      UUID id,
      UUID owner,
      String error
  ) {

    outbox.markRetry(
        id,
        owner,
        error,
        maxAttempts
    );
  }

  private static void requireTuple(
      OutboxRepository.Event event
  ) {

    if (blank(event.user())
        || blank(event.relation())
        || blank(event.object())) {

      throw new IllegalStateException(
          "Outbox tuple payload is incomplete"
      );
    }
  }

  private static boolean blank(String value) {
    return value == null || value.isBlank();
  }

  private static String safeMessage(
      RuntimeException failure
  ) {

    String message =
        failure.getClass().getSimpleName()
            + ": "
            + failure.getMessage();

    return message.length() > 900
        ? message.substring(0, 900)
        : message;
  }
}

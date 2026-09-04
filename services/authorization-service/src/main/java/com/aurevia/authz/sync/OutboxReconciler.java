package com.aurevia.authz.sync;

import com.aurevia.authz.openfga.RelationshipAuthorizationPort;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Claims rows transactionally, then performs network I/O outside database transactions. */
@Component
public final class OutboxReconciler {
  private static final List<String> WRITES=List.of("GRANT_WRITE","ROLE_ASSIGNMENT_WRITE",
      "RESOURCE_PARENT_WRITE","GROUP_MEMBERSHIP_WRITE","ACCESS_GROUP_MEMBERSHIP_WRITE",
      "APPLICATION_GROUP_GRANT_WRITE");
  private static final List<String> DELETES=List.of("GRANT_DELETE","ROLE_ASSIGNMENT_DELETE",
      "RESOURCE_PARENT_DELETE","GROUP_MEMBERSHIP_DELETE","ACCESS_GROUP_MEMBERSHIP_DELETE",
      "APPLICATION_GROUP_GRANT_DELETE");

  private final OutboxRepository outbox;
  private final RelationshipAuthorizationPort relationships;
  private final int maxAttempts;
  private final int claimTimeoutSeconds;
  private final Timer projectionLatency;

  public OutboxReconciler(OutboxRepository outbox,RelationshipAuthorizationPort relationships,
      @Value("${aurevia.outbox.max-attempts:12}") int maxAttempts,
      @Value("${aurevia.outbox.claim-timeout-seconds:120}") int claimTimeoutSeconds,
      MeterRegistry metrics) {
    this.outbox=outbox;this.relationships=relationships;this.maxAttempts=maxAttempts;
    this.claimTimeoutSeconds=claimTimeoutSeconds;
    this.projectionLatency=metrics.timer("aurevia.openfga.projection.latency");
  }

  @Scheduled(fixedDelayString="${aurevia.outbox.interval-ms:5000}")
  public void reconcile() {
    UUID owner=UUID.randomUUID();
    for(OutboxRepository.Event event:outbox.claim(owner,claimTimeoutSeconds,50)) process(event,owner);
  }

  private void process(OutboxRepository.Event event,UUID owner) {
    try {
      if(event.eventType().startsWith("PANEL_")) {
        markApplied(event.id(),owner);
      } else if(WRITES.contains(event.eventType())) {
        requireTuple(event);
        projectionLatency.record(()->relationships.write(
            event.user(),event.relation(),event.object()));
        markApplied(event.id(),owner);
      } else if(DELETES.contains(event.eventType())) {
        requireTuple(event);
        projectionLatency.record(()->relationships.delete(
            event.user(),event.relation(),event.object()));
        markApplied(event.id(),owner);
      } else {
        retry(event.id(),owner,"No projection adapter for event "+event.eventType());
      }
    } catch(RuntimeException failure) {
      retry(event.id(),owner,safeMessage(failure));
    }
  }

  private void markApplied(UUID id,UUID owner) {
    if(!outbox.markApplied(id,owner,maxAttempts)) {
      throw new IllegalStateException("Outbox claim was lost before completion");
    }
  }

  private void retry(UUID id,UUID owner,String error) {
    outbox.markRetry(id,owner,error,maxAttempts);
  }

  private static void requireTuple(OutboxRepository.Event event) {
    if(blank(event.user())||blank(event.relation())||blank(event.object())) {
      throw new IllegalStateException("Outbox tuple payload is incomplete");
    }
  }
  private static boolean blank(String value) { return value==null||value.isBlank(); }
  private static String safeMessage(RuntimeException failure) {
    String message=failure.getClass().getSimpleName()+": "+failure.getMessage();
    return message.length()>900?message.substring(0,900):message;
  }

}

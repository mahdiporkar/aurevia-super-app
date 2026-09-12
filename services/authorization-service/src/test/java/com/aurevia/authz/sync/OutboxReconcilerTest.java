package com.aurevia.authz.sync;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.aurevia.authz.openfga.RelationshipAuthorizationPort;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class OutboxReconcilerTest {

  @Test
  void scheduledWorkerIsDeferredUntilStartupReconciliationCompletes() {

    OutboxRepository repository =
        mock(OutboxRepository.class);

    RelationshipAuthorizationPort relationships =
        mock(RelationshipAuthorizationPort.class);

    var reconciler =
        new OutboxReconciler(
            repository,
            relationships,
            12,
            120,
            new SimpleMeterRegistry(),
            true
        );

    /*
     * Startup reconciliation owns the outbox.
     */
    reconciler.reconcile();

    verifyNoInteractions(repository);

    when(
        repository.claim(
            any(UUID.class),
            eq(120),
            eq(50)
        )
    ).thenReturn(List.of());

    reconciler.markStartupReconciliationComplete();

    reconciler.reconcile();

    verify(repository).claim(
        any(UUID.class),
        eq(120),
        eq(50)
    );
  }

  @Test
  void scheduledWorkerRunsImmediatelyWhenStartupReconciliationIsDisabled() {

    OutboxRepository repository =
        mock(OutboxRepository.class);

    RelationshipAuthorizationPort relationships =
        mock(RelationshipAuthorizationPort.class);

    when(
        repository.claim(
            any(UUID.class),
            eq(120),
            eq(50)
        )
    ).thenReturn(List.of());

    var reconciler =
        new OutboxReconciler(
            repository,
            relationships,
            12,
            120,
            new SimpleMeterRegistry(),
            false
        );

    reconciler.reconcile();

    verify(repository).claim(
        any(UUID.class),
        eq(120),
        eq(50)
    );
  }
}

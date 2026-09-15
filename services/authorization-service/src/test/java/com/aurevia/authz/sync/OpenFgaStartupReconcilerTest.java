package com.aurevia.authz.sync;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

class OpenFgaStartupReconcilerTest {

  private final OpenFgaReconciliationService service =
      mock(OpenFgaReconciliationService.class);

  private final OutboxReconciler outbox =
      mock(OutboxReconciler.class);

  private final OutboxMetricsRepository metrics =
      mock(OutboxMetricsRepository.class);

  private final OpenFgaStartupReconciler reconciler =
      new OpenFgaStartupReconciler(
          service,
          outbox,
          metrics,
          Duration.ofSeconds(180),
          120
      );

  @Test
  void drainsOutboxRepairsVerifiesThenEnablesScheduledWorker() {

    when(metrics.pending())
        .thenReturn(2d, 0d, 0d);

    when(metrics.deadLettered())
        .thenReturn(0d);

    when(outbox.reconcileBatch())
        .thenReturn(2);

    when(service.reconcile(true))
        .thenReturn(
            report(
                false,
                List.of(),
                List.of(),
                4
            )
        );

    when(service.reconcile(false))
        .thenReturn(
            report(
                true,
                List.of(),
                List.of(),
                0
            )
        );

    reconciler.repairAndVerify();

    var order =
        inOrder(outbox, service);

    order.verify(outbox)
        .reconcileBatch();

    order.verify(service)
        .reconcile(true);

    order.verify(service)
        .reconcile(false);

    order.verify(outbox)
        .markStartupReconciliationComplete();
  }

  @Test
  void failsStartupWhenRepairLeavesDrift() {

    when(metrics.pending())
        .thenReturn(0d);

    when(metrics.deadLettered())
        .thenReturn(0d);

    var missing =
        new ReconciliationTuple(
            "application:aurevia",
            "parent",
            "application:aurevia/admin"
        );

    when(service.reconcile(true))
        .thenReturn(
            report(
                false,
                List.of(missing),
                List.of(),
                1
            )
        );

    when(service.reconcile(false))
        .thenReturn(
            report(
                true,
                List.of(missing),
                List.of(),
                0
            )
        );

    assertThatThrownBy(
        reconciler::repairAndVerify
    )
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("missing=1")
        .hasMessageContaining("unexpected=0");

    verify(outbox, never())
        .markStartupReconciliationComplete();
  }

  @Test
  void failsBeforeRepairWhenBootstrapOutboxCannotDrain() {

    var shortTimeout =
        new OpenFgaStartupReconciler(
            service,
            outbox,
            metrics,
            Duration.ofMillis(25),
            0
        );

    when(metrics.pending())
        .thenReturn(1d);

    when(metrics.deadLettered())
        .thenReturn(0d);

    when(outbox.reconcileBatch())
        .thenReturn(0);

    assertThatThrownBy(
        shortTimeout::repairAndVerify
    )
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("outbox did not drain")
        .hasMessageContaining("pending=1");

    verify(service, never())
        .reconcile(true);

    verify(outbox, never())
        .markStartupReconciliationComplete();
  }

  @Test
  void rejectsStartupTimeoutThatCannotOutliveAStaleClaim() {

    assertThatThrownBy(() ->
        new OpenFgaStartupReconciler(
            service,
            outbox,
            metrics,
            Duration.ofSeconds(60),
            120
        )
    )
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining(
            "startup-outbox-timeout must be greater"
        );
  }

  private static OpenFgaReconciliationService.Report report(
      boolean dryRun,
      List<ReconciliationTuple> missing,
      List<ReconciliationTuple> unexpected,
      int repaired
  ) {

    return new OpenFgaReconciliationService.Report(
        dryRun,
        10,
        10,
        missing,
        unexpected,
        repaired
    );
  }
}

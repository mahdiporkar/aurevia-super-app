package com.aurevia.authz.sync;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.Test;

class OpenFgaStartupReconcilerTest {
  private final OpenFgaReconciliationService service=mock(OpenFgaReconciliationService.class);
  private final OpenFgaStartupReconciler reconciler=new OpenFgaStartupReconciler(service);

  @Test
  void repairsThenVerifiesProjection() {
    when(service.reconcile(true)).thenReturn(report(false,List.of(),List.of(),4));
    when(service.reconcile(false)).thenReturn(report(true,List.of(),List.of(),0));

    reconciler.repairAndVerify();

    var order=inOrder(service);
    order.verify(service).reconcile(true);
    order.verify(service).reconcile(false);
  }

  @Test
  void failsStartupWhenRepairLeavesDrift() {
    var missing=new ReconciliationTuple("application:aurevia","parent",
        "application:aurevia/admin");
    when(service.reconcile(true)).thenReturn(report(false,List.of(missing),List.of(),1));
    when(service.reconcile(false)).thenReturn(report(true,List.of(missing),List.of(),0));

    assertThatThrownBy(reconciler::repairAndVerify)
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("missing=1")
        .hasMessageContaining("unexpected=0");
  }

  private static OpenFgaReconciliationService.Report report(boolean dryRun,
      List<ReconciliationTuple> missing,List<ReconciliationTuple> unexpected,int repaired) {
    return new OpenFgaReconciliationService.Report(dryRun,10,10,missing,unexpected,repaired);
  }
}

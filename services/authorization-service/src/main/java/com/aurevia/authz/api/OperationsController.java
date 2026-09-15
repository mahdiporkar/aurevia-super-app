package com.aurevia.authz.api;

import com.aurevia.authz.sync.OpenFgaReconciliationService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/v1/registry/operations")
public class OperationsController {
  private final OpenFgaReconciliationService reconciliation;
  public OperationsController(OpenFgaReconciliationService reconciliation){this.reconciliation=reconciliation;}

  @PostMapping("/openfga-reconcile")
  public OpenFgaReconciliationService.Report reconcile(
      @RequestParam(value="repair",defaultValue="false") boolean repair){
    return reconciliation.reconcile(repair);
  }
}

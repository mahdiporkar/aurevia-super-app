package com.aurevia.authz.api;

import com.aurevia.authz.diagnostics.AuthorizationDiagnostics.Explanation;
import com.aurevia.authz.diagnostics.AuthorizationDiagnosticsService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Administrator-only explanation of an effective authorization decision.
 *
 * <p>Registered under {@code /internal/v1/registry} so it inherits the administrative
 * authorization interceptor, which requires {@code can_manage} on this path. The endpoint is
 * read-only and returns control-plane metadata plus the live OpenFGA decision; it never returns
 * tokens, secrets, or request payloads.</p>
 */
@RestController
@RequestMapping("/internal/v1/registry/diagnostics")
public final class AuthorizationDiagnosticsController {

  private final AuthorizationDiagnosticsService diagnostics;

  public AuthorizationDiagnosticsController(AuthorizationDiagnosticsService diagnostics) {
    this.diagnostics = diagnostics;
  }

  @GetMapping("/authorization")
  public Explanation explain(
      @RequestParam("issuer") String issuer,
      @RequestParam("subject") String subject,
      @RequestParam("resource") String resource,
      @RequestParam("action") String action) {
    return diagnostics.explain(issuer, subject, resource, action);
  }
}

package com.aurevia.authz.api;

import static com.aurevia.authz.api.dto.AuthorizationDtos.*;

import com.aurevia.authz.authorization.AuthorizationDecisionService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/** HTTP adapter for runtime decisions and effective frontend manifests. */
@RestController
@RequestMapping("/internal/v1")
public final class AuthorizationController {
  private final AuthorizationDecisionService authorization;

  public AuthorizationController(AuthorizationDecisionService authorization) {
    this.authorization=authorization;
  }

  @PostMapping("/authorize/check")
  public Decision check(@Valid @RequestBody CheckRequest request) {
    var evaluation=authorization.check(request);
    observe(request,evaluation);
    return evaluation.decision();
  }

  @PostMapping("/authorize/check-batch")
  public List<Decision> checkBatch(@RequestBody List<@Valid CheckRequest> requests) {
    return requests.stream().map(this::check).toList();
  }

  @GetMapping("/subjects/{id}/manifest")
  public ResponseEntity<Manifest> manifest(@PathVariable("id") String id,
      @RequestParam("issuer") String issuer) {
    Manifest manifest=authorization.manifest(id,issuer);
    return ResponseEntity.ok().eTag('"'+manifest.version()+'"')
        .cacheControl(CacheControl.noCache()).body(manifest);
  }

  private static void observe(CheckRequest request,
      AuthorizationDecisionService.CheckEvaluation evaluation) {
    try {
      var attributes=(ServletRequestAttributes)RequestContextHolder.currentRequestAttributes();
      var servlet=attributes.getRequest();
      boolean allowed="ALLOW".equals(evaluation.decision().result());
      int separator=request.resource().indexOf(':');
      servlet.setAttribute("authorizationResult",allowed?"ALLOW":"DENY");
      servlet.setAttribute("resourceType",separator<0?"unknown":request.resource().substring(0,separator));
      servlet.setAttribute("resourceId",separator<0?request.resource():request.resource().substring(separator+1));
      servlet.setAttribute("businessAction",request.action());
      servlet.setAttribute("openfgaDurationMs",evaluation.openFgaDurationMs());
    } catch(IllegalStateException ignored) {
      // Unit tests and non-servlet invocations do not have request attributes.
    }
  }
}

package com.aurevia.bff.api;

import java.security.Principal;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestParam;
import reactor.core.publisher.Mono;
import com.aurevia.bff.security.SessionIdentity;

@RestController
@RequestMapping("/api/v1/reports")
public class ReportsController {
  private final AuthorizationServiceClient authorization;

  public ReportsController(AuthorizationServiceClient authorization) {
    this.authorization = authorization;
  }

  @GetMapping
  Mono<List<Map>> reports(Principal principal,
      @RequestParam(value="instance",required=false) String requestedInstance) {
    SessionIdentity identity = SessionIdentity.from(principal);
    String publicInstance=requestedInstance==null||requestedInstance.isBlank()
        ?null:requestedInstance;
    return authorization.resolveSupersetProxy(publicInstance)
        .flatMap(target->authorization.supersetAssets(identity.issuer(),identity.subject(),
            String.valueOf(target.get("operation_code"))));
  }
}

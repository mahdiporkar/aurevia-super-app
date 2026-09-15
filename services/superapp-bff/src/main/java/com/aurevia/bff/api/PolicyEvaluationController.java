package com.aurevia.bff.api;

import com.aurevia.bff.security.SessionIdentity;
import io.swagger.v3.oas.annotations.Operation;
import java.security.Principal;
import java.util.Map;
import java.util.UUID;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

/** Server-enforced evaluation for decisions that require runtime ABAC attributes. */
@RestController
public final class PolicyEvaluationController {
  private final AuthorizationServiceClient authorization;
  public PolicyEvaluationController(AuthorizationServiceClient authorization) {
    this.authorization=authorization;
  }

  @Operation(summary="ارزیابی پویای سیاست دسترسی برای کاربر جاری")
  @PostMapping("/api/v1/authorize/evaluate")
  Mono<Map> evaluate(@RequestBody Map<String,Object> input,Principal principal) {
    SessionIdentity identity=SessionIdentity.from(principal);
    Map<String,Object> request=Map.of(
        "subjectId",identity.subject(),"issuer",identity.issuer(),
        "resource",required(input,"resource"),"action",required(input,"action"),
        "context",input.getOrDefault("context",Map.of()),
        "correlationId",UUID.randomUUID().toString());
    return authorization.check(request);
  }

  private static String required(Map<String,Object> input,String key) {
    Object value=input.get(key);
    if(!(value instanceof String text)||text.isBlank())
      throw new IllegalArgumentException(key+" is required");
    return text;
  }
}

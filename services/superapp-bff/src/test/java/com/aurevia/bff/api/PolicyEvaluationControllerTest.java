package com.aurevia.bff.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import com.aurevia.bff.security.SessionIdentity;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class PolicyEvaluationControllerTest {
  @Test void ignoresCallerIdentityAndUsesAuthenticatedSession() {
    AuthorizationServiceClient authorization=mock(AuthorizationServiceClient.class);
    when(authorization.check(anyMap())).thenReturn(Mono.just(Map.of("result","ALLOW")));
    var controller=new PolicyEvaluationController(authorization);

    StepVerifier.create(controller.evaluate(Map.of("subjectId","attacker","issuer","spoofed",
        "resource","finance.invoice","action","approve","context",Map.of("amount",10)),
        new SessionIdentity("trusted-issuer","trusted-user","operator")))
        .expectNextMatches(result->"ALLOW".equals(result.get("result"))).verifyComplete();

    ArgumentCaptor<Map<String,Object>> request=ArgumentCaptor.forClass(Map.class);
    verify(authorization).check(request.capture());
    assertThat(request.getValue()).containsEntry("issuer","trusted-issuer")
        .containsEntry("subjectId","trusted-user");
  }
}

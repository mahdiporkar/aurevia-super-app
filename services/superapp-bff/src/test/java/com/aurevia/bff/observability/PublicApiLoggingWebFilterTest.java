package com.aurevia.bff.observability;
import com.aurevia.bff.api.AuthorizationServiceClient;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import static org.mockito.Mockito.*;

class PublicApiLoggingWebFilterTest {
  @ParameterizedTest @ValueSource(ints={403,404,502,503})
  void persistsActualRejectionStatusAndAuthorizationDecision(int status) {
    var logs=mock(AuthorizationServiceClient.class);
    when(logs.ingestApiLog(anyMap())).thenReturn(Mono.empty());
    var exchange=MockServerWebExchange.from(MockServerHttpRequest.get("/api/proxy/test-sso/api/test/whoami"));
    exchange.getAttributes().put("proxy.authorizationResult","DENY");
    StepVerifier.create(new PublicApiLoggingWebFilter(logs).filter(exchange,
      ignored->Mono.error(new ResponseStatusException(HttpStatus.valueOf(status)))))
      .expectError(ResponseStatusException.class).verify();
    verify(logs).ingestApiLog(argThat(entry->Integer.valueOf(status).equals(entry.get("statusCode"))
      && "DENY".equals(entry.get("authorizationResult"))));
  }
}

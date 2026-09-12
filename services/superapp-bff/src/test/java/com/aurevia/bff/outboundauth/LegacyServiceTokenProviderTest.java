package com.aurevia.bff.outboundauth;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.assertThat;

class LegacyServiceTokenProviderTest {
  @Test void missingCredentialFailsClosedWithSanitizedGatewayError() {
    var manager=mock(LegacyTokenManager.class);
    var target=new OutboundTokenProvider.ServiceTarget("target","profile",OutboundAuthMode.LEGACY_SERVICE_TOKEN,1);
    when(manager.resolve(target)).thenReturn(Mono.error(new IllegalStateException("private endpoint detail")));
    StepVerifier.create(new LegacyServiceTokenProvider(manager).resolve(target,null,null))
      .expectErrorSatisfies(error->{
        assertThat(error).isInstanceOf(ResponseStatusException.class);
        var status=(ResponseStatusException)error;
        assertThat(status.getStatusCode().value()).isEqualTo(502);
        assertThat(status.getReason()).isEqualTo("Legacy authentication unavailable");
        assertThat(status.getCause()).isNull();
      }).verify();
  }
}

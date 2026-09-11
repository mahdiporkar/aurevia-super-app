package com.aurevia.bff.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import com.aurevia.bff.api.AuthorizationServiceClient;
import com.aurevia.bff.api.AuthorizationServiceClient.IdentityProviderRuntime;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class DynamicClientRegistrationRepositoryTest {
  @Test void buildsRegistrationFromExternalOidcRegistryWithoutStartupConfiguration(){
    AuthorizationServiceClient authorization=mock(AuthorizationServiceClient.class);
    IdentityProviderSecretResolver secrets=mock(IdentityProviderSecretResolver.class);
    when(authorization.identityProvider("bank-a")).thenReturn(Mono.just(new IdentityProviderRuntime(
        "bank-a","Bank A","https://sso.bank-a.example/realms/main",
        "https://sso.bank-a.example/auth","https://sso.bank-a.example/token",
        "https://sso.bank-a.example/jwks","https://sso.bank-a.example/userinfo","aurevia-bff",
        "secret://identity/bank-a",List.of("openid","profile"),List.of("aurevia-api"),
        "sub","preferred_username","groups")));
    when(secrets.resolve("secret://identity/bank-a")).thenReturn(Mono.just("vault-secret"));
    var repository=new DynamicClientRegistrationRepository(authorization,secrets,
        Duration.ofSeconds(30),Duration.ofSeconds(2));
    StepVerifier.create(repository.findByRegistrationId("bank-a"))
        .assertNext(value->{assertThat(value.getProviderDetails().getIssuerUri())
          .isEqualTo("https://sso.bank-a.example/realms/main");
          assertThat(value.getProviderDetails().getJwkSetUri()).endsWith("/jwks");
          assertThat(value.getClientSecret()).isEqualTo("vault-secret");})
        .verifyComplete();
  }

  @Test void unknownOrMalformedProviderFailsClosed(){
    AuthorizationServiceClient authorization=mock(AuthorizationServiceClient.class);
    IdentityProviderSecretResolver secrets=mock(IdentityProviderSecretResolver.class);
    var repository=new DynamicClientRegistrationRepository(authorization,secrets,
        Duration.ofSeconds(30),Duration.ofSeconds(2));
    StepVerifier.create(repository.findByRegistrationId("../../fake")).verifyComplete();
  }
}

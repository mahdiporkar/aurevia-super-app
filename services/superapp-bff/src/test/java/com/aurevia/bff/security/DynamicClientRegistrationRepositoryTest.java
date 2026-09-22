package com.aurevia.bff.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verifyNoInteractions;
import com.aurevia.bff.api.AuthorizationServiceClient;
import com.aurevia.bff.api.AuthorizationServiceClient.IdentityProviderRuntime;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.AuthorizationGrantType;

class DynamicClientRegistrationRepositoryTest {
  @Test void buildsAdditionalRegistrationFromExternalOidcRegistry(){
    AuthorizationServiceClient authorization=mock(AuthorizationServiceClient.class);
    IdentityProviderSecretResolver secrets=mock(IdentityProviderSecretResolver.class);
    when(authorization.identityProvider("bank-a")).thenReturn(Mono.just(new IdentityProviderRuntime(
        "bank-a","Bank A","https://sso.bank-a.example/realms/main",
        "https://sso.bank-a.example/auth","https://sso.bank-a.example/token",
        "https://sso.bank-a.example/jwks","https://sso.bank-a.example/userinfo","aurevia-bff",
        "secret://identity/bank-a",List.of("openid","profile"),List.of("aurevia-api"),
        "sub","preferred_username","groups")));
    when(secrets.resolve("secret://identity/bank-a")).thenReturn(Mono.just("vault-secret"));
    var repository=new DynamicClientRegistrationRepository(authorization,secrets,primary(),
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
    var repository=new DynamicClientRegistrationRepository(authorization,secrets,primary(),
        Duration.ofSeconds(30),Duration.ofSeconds(2));
    StepVerifier.create(repository.findByRegistrationId("../../fake")).verifyComplete();
  }

  @Test void primaryNeverContactsDatabaseOrSecretReferenceResolverEvenAfterInvalidation(){
    AuthorizationServiceClient authorization=mock(AuthorizationServiceClient.class);
    IdentityProviderSecretResolver secrets=mock(IdentityProviderSecretResolver.class);
    var primary=primary();
    var repository=new DynamicClientRegistrationRepository(authorization,secrets,primary,
        Duration.ofSeconds(30),Duration.ofSeconds(2));
    StepVerifier.create(repository.findByRegistrationId("public-iam"))
        .expectNext(primary).verifyComplete();
    repository.invalidateAll();
    StepVerifier.create(repository.findByRegistrationId("public-iam"))
        .expectNext(primary).verifyComplete();
    verifyNoInteractions(authorization,secrets);
  }

  @Test void additionalRegistryCannotReconfigureThePrimaryIssuer(){
    AuthorizationServiceClient authorization=mock(AuthorizationServiceClient.class);
    IdentityProviderSecretResolver secrets=mock(IdentityProviderSecretResolver.class);
    when(authorization.identityProvider("shadow-primary")).thenReturn(Mono.just(new IdentityProviderRuntime(
        "shadow-primary","Shadow","https://primary.example/realms/main","https://wrong.example/auth",
        "https://wrong.example/token","https://wrong.example/jwks",null,"wrong-client",
        "secret://wrong",List.of("openid"),List.of(),"sub","preferred_username","groups")));
    var repository=new DynamicClientRegistrationRepository(authorization,secrets,primary(),
        Duration.ofSeconds(30),Duration.ofSeconds(2));
    StepVerifier.create(repository.findByRegistrationId("shadow-primary"))
        .expectErrorMessage("Primary OIDC issuer is controlled by runtime configuration").verify();
    verifyNoInteractions(secrets);
  }

  private static ClientRegistration primary(){
    return ClientRegistration.withRegistrationId("public-iam").clientId("aurevia-bff")
        .clientSecret("runtime-secret").authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
        .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
        .issuerUri("https://primary.example/realms/main").authorizationUri("https://primary.example/auth")
        .tokenUri("https://primary.example/token").jwkSetUri("https://primary.example/jwks")
        .userNameAttributeName("sub").build();
  }
}

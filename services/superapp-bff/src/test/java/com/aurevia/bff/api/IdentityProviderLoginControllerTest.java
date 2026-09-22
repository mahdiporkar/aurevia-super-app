package com.aurevia.bff.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class IdentityProviderLoginControllerTest {
  private final AuthorizationServiceClient authorization=mock(AuthorizationServiceClient.class);
  private final IdentityProviderLoginController controller=new IdentityProviderLoginController(authorization,
      ClientRegistration.withRegistrationId("public-iam").clientName("Aurevia").clientId("aurevia-bff")
          .clientSecret("never-in-response").authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
          .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}").issuerUri("https://primary.example/realm")
          .authorizationUri("https://primary.example/auth").tokenUri("https://primary.example/token")
          .jwkSetUri("https://primary.example/jwks").userNameAttributeName("sub").build());

  @Test void primaryLoginAndProviderListingWorkWithoutDatabaseCalls(){
    for(String provider:new String[]{null,"","public-iam"}) {
      StepVerifier.create(controller.login(provider,"old-tenant","old-domain"))
          .assertNext(response->{
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FOUND);
            assertThat(response.getHeaders().getLocation().toString()).isEqualTo("/oauth2/authorization/public-iam");
          }).verifyComplete();
    }
    StepVerifier.create(controller.providers(null,null,false)).assertNext(providers->{
      assertThat(providers).singleElement().satisfies(provider->{
        assertThat(provider.code()).isEqualTo("public-iam");
        assertThat(provider.issuerUrl()).isEqualTo("https://primary.example/realm");
        assertThat(provider.toString()).doesNotContain("never-in-response");
      });
    }).verifyComplete();
    verifyNoInteractions(authorization);
  }

  @Test void additionalProvidersRequireExplicitSelectionAndCannotShadowPrimary(){
    var additional=new AuthorizationServiceClient.IdentityProviderSummary("partner-sso","Partner","OIDC",
        "https://partner.example",null,List.of(),"UNKNOWN");
    var shadow=new AuthorizationServiceClient.IdentityProviderSummary("public-iam","Old database primary","OIDC",
        "https://old.example",null,List.of(),"UNKNOWN");
    when(authorization.identityProviders(null,null)).thenReturn(Mono.just(List.of(additional,shadow)));
    when(authorization.routeIdentityProvider("partner-sso",null,null)).thenReturn(Mono.just(additional));
    StepVerifier.create(controller.providers(null,null,true)).assertNext(providers->
        assertThat(providers).extracting(AuthorizationServiceClient.IdentityProviderSummary::code)
            .containsExactly("public-iam","partner-sso")).verifyComplete();
    StepVerifier.create(controller.login("partner-sso",null,null)).assertNext(response->
        assertThat(response.getHeaders().getLocation().toString()).isEqualTo("/oauth2/authorization/partner-sso"))
        .verifyComplete();
  }

  @Test void malformedProviderFailsWithoutCallingAuthorization(){
    StepVerifier.create(controller.login("../../bad",null,null)).expectErrorMatches(failure->
        failure instanceof org.springframework.web.server.ResponseStatusException status
            &&status.getStatusCode()==HttpStatus.BAD_REQUEST).verify();
    verifyNoInteractions(authorization);
  }
}

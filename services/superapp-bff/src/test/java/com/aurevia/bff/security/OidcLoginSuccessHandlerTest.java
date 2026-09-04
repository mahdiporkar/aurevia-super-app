package com.aurevia.bff.security;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aurevia.bff.api.AuthorizationServiceClient;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistration.Builder;
import org.springframework.security.oauth2.client.web.server.ServerOAuth2AuthorizedClientRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2RefreshToken;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.web.server.WebFilterExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class OidcLoginSuccessHandlerTest {
  @Test void removesAuthorizedClientAfterMovingTokensToVault() {
    ServerOAuth2AuthorizedClientRepository clients=mock(ServerOAuth2AuthorizedClientRepository.class);
    TokenVaultService vault=mock(TokenVaultService.class);
    AuthorizationServiceClient authorization=mock(AuthorizationServiceClient.class);
    ClientRegistration registration=registration().build();
    Instant now=Instant.now();
    var access=new OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER,"plain-access-token",
        now,now.plusSeconds(300));
    var authorized=new OAuth2AuthorizedClient(registration,"subject-1",access,
        new OAuth2RefreshToken("plain-refresh-token",now));
    var idToken=new OidcIdToken("plain-id-token",now,now.plusSeconds(300),Map.of(
        "sub","subject-1","iss","https://issuer.example","preferred_username","alice"));
    var user=new DefaultOidcUser(List.of(new SimpleGrantedAuthority("ROLE_USER")),idToken,"sub");
    var authentication=new OAuth2AuthenticationToken(user,user.getAuthorities(),"public-iam");
    var exchange=MockServerWebExchange.from(MockServerHttpRequest.get("/login/oauth2/code/public-iam").build());
    var webExchange=new WebFilterExchange(exchange,ignored->Mono.empty());
    when(clients.loadAuthorizedClient("public-iam",authentication,exchange))
        .thenReturn(Mono.just(authorized));
    when(clients.removeAuthorizedClient("public-iam",authentication,exchange))
        .thenReturn(Mono.empty());
    when(authorization.syncLogin(org.mockito.ArgumentMatchers.anyMap())).thenReturn(Mono.empty());
    when(vault.store(org.mockito.ArgumentMatchers.any())).thenReturn(Mono.just("handle-1"));

    StepVerifier.create(new OidcLoginSuccessHandler(clients,vault,authorization)
        .onAuthenticationSuccess(webExchange,authentication)).verifyComplete();

    verify(clients).removeAuthorizedClient("public-iam",authentication,exchange);
    verify(vault).store(org.mockito.ArgumentMatchers.argThat(tokens ->
        "plain-access-token".equals(tokens.accessToken())
            && "plain-refresh-token".equals(tokens.refreshToken())));
  }

  private static Builder registration() {
    return ClientRegistration.withRegistrationId("public-iam")
        .clientId("client").clientSecret("secret")
        .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
        .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
        .authorizationUri("https://issuer.example/auth")
        .tokenUri("https://issuer.example/token")
        .jwkSetUri("https://issuer.example/jwks")
        .issuerUri("https://issuer.example")
        .userNameAttributeName("sub")
        .clientName("test");
  }
}

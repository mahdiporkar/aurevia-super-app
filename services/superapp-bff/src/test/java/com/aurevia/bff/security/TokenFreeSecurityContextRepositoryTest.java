package com.aurevia.bff.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUserAuthority;
import reactor.test.StepVerifier;

class TokenFreeSecurityContextRepositoryTest {
  @Test void replacesOidcPrincipalWithTokenFreeSessionIdentity() {
    OidcIdToken idToken=new OidcIdToken("raw-id-token-must-not-be-persisted",
        Instant.now(),Instant.now().plusSeconds(300),Map.of(
            "sub","subject-1","iss","https://issuer.example",
            "preferred_username","alice"));
    var user=new DefaultOidcUser(List.of(
        new OidcUserAuthority(idToken), new SimpleGrantedAuthority("ROLE_USER")),idToken,"sub");
    var oauth=new OAuth2AuthenticationToken(user,user.getAuthorities(),"public-iam");
    var exchange=MockServerWebExchange.from(MockServerHttpRequest.get("/").build());
    var repository=new TokenFreeSecurityContextRepository();

    StepVerifier.create(repository.save(exchange,new SecurityContextImpl(oauth))
        .then(repository.load(exchange)))
        .assertNext(context->{
          assertThat(context.getAuthentication()).isNotInstanceOf(OAuth2AuthenticationToken.class);
          assertThat(context.getAuthentication().getPrincipal()).isEqualTo(
              new SessionIdentity("https://issuer.example","subject-1","alice"));
          assertThat(context.getAuthentication().getCredentials()).isNull();
          assertThat(context.getAuthentication().getAuthorities())
              .allMatch(authority -> authority.getClass().equals(SimpleGrantedAuthority.class))
              .extracting("authority")
              .containsExactlyInAnyOrder("OIDC_USER", "ROLE_USER");
          assertThat(context.getAuthentication().toString())
              .doesNotContain("raw-id-token-must-not-be-persisted");
        }).verifyComplete();
  }
}

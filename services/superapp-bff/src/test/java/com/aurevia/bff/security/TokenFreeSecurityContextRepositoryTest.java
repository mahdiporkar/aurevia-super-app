package com.aurevia.bff.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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
  @ParameterizedTest
  @ValueSource(strings={"sub","employee_id"})
  void replacesOidcPrincipalWithTokenFreeSessionIdentityUsingConfiguredSubject(String subjectClaim) {
    String expectedSubject="employee_id".equals(subjectClaim)?"employee-42":"subject-1";
    OidcIdToken idToken=new OidcIdToken("raw-id-token-must-not-be-persisted",
        Instant.now(),Instant.now().plusSeconds(300),Map.of(
            "sub","subject-1","employee_id","employee-42","iss","https://issuer.example",
            "preferred_username","alice"));
    var user=new DefaultOidcUser(List.of(
        new OidcUserAuthority(idToken), new SimpleGrantedAuthority("ROLE_USER")),idToken,subjectClaim);
    var oauth=new OAuth2AuthenticationToken(user,user.getAuthorities(),"public-iam");
    var exchange=MockServerWebExchange.from(MockServerHttpRequest.get("/").build());
    var repository=new TokenFreeSecurityContextRepository();

    StepVerifier.create(repository.save(exchange,new SecurityContextImpl(oauth))
        .then(repository.load(exchange)))
        .assertNext(context->{
          assertThat(context.getAuthentication()).isNotInstanceOf(OAuth2AuthenticationToken.class);
          assertThat(context.getAuthentication().getPrincipal()).isEqualTo(
              new SessionIdentity("https://issuer.example",expectedSubject,"alice"));
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

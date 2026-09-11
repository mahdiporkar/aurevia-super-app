package com.aurevia.bff.security;

import static org.assertj.core.api.Assertions.assertThat;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.client.oidc.authentication.OidcIdTokenValidator;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.jwt.Jwt;

class OidcIssuerValidationTest {
  @Test void wrongIssuerIsDeniedEvenWithExpectedAudienceAndUnexpiredToken(){
    var registration=ClientRegistration.withRegistrationId("bank-a").clientId("aurevia-bff")
        .clientSecret("secret").authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
        .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
        .authorizationUri("https://sso.bank-a.example/auth")
        .tokenUri("https://sso.bank-a.example/token").jwkSetUri("https://sso.bank-a.example/jwks")
        .issuerUri("https://sso.bank-a.example").userNameAttributeName("sub").clientName("Bank A").build();
    Instant now=Instant.now();
    var jwt=new Jwt("signed-token",now,now.plusSeconds(300),Map.of("alg","RS256"),Map.of(
        "iss","https://evil.example","sub","subject-1","aud",List.of("aurevia-bff"),
        "iat",now,"exp",now.plusSeconds(300)));
    assertThat(new OidcIdTokenValidator(registration).validate(jwt).hasErrors()).isTrue();
  }
}

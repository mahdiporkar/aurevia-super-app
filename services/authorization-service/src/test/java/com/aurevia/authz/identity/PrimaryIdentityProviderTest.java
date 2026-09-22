package com.aurevia.authz.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class PrimaryIdentityProviderTest {
  @Test void runtimeIssuerIsTheOnlyTrustAnchorForPrimaryLogins(){
    var primary=new PrimaryIdentityProvider(" https://idp.example/realms/aurevia ");
    assertThat(primary.issuer()).isEqualTo("https://idp.example/realms/aurevia");
    primary.verifyIssuer("https://idp.example/realms/aurevia");
    assertThatThrownBy(()->primary.verifyIssuer("https://other.example/realms/aurevia"))
        .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("OIDC_ISSUER_URI");
  }

  @Test void missingIssuerIsDiagnosedWhenFirstNeededNotAtConstruction(){
    var primary=new PrimaryIdentityProvider("");
    assertThatThrownBy(primary::issuer).hasMessageContaining("OIDC_ISSUER_URI is required");
  }

  @Test void malformedIssuerFailsStartup(){
    for(String value:new String[]{"ftp://idp.example/realm","https://user:pw@idp.example/realm",
        "https://idp.example/realm?x=1","https://idp.example/realm#frag","not a url"}){
      assertThatThrownBy(()->new PrimaryIdentityProvider(value)).as(value)
          .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("OIDC_ISSUER_URI");
    }
  }
}

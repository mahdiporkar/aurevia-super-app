package com.aurevia.bff.security;

import static org.assertj.core.api.Assertions.assertThat;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.security.oauth2.client.registration.ClientRegistration;

class PrimaryOidcConfigurationTest {
  private final ApplicationContextRunner context = new ApplicationContextRunner()
      .withUserConfiguration(PrimaryOidcConfiguration.class);

  @Test void missingRequiredConfigurationFailsStartupClearly(){
    context.run(application->{
      assertThat(application).hasFailed();
      assertThat(application.getStartupFailure()).hasRootCauseMessage(
          "OIDC configuration is incomplete. Missing required configuration: OIDC_ISSUER_URI, OIDC_CLIENT_ID, OIDC_CLIENT_SECRET");
    });
  }

  @Test void missingSecretFailsBeforeDiscoveryAndNeverIncludesOtherCredentials(){
    context.withPropertyValues("aurevia.oidc.issuer-uri=https://issuer.example/realms/main",
        "aurevia.oidc.client-id=aurevia-bff").run(application->{
      assertThat(application).hasFailed();
      assertThat(application.getStartupFailure()).hasRootCauseMessage(
          "OIDC configuration is incomplete. Missing required configuration: OIDC_CLIENT_SECRET");
    });
  }

  @Test void frameworkDiscoversEndpointsAndLogoutMetadataAtStartup() throws Exception {
    HttpServer server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
    String issuer="http://127.0.0.1:"+server.getAddress().getPort()+"/realms/main";
    AtomicInteger discoveries=new AtomicInteger();
    server.createContext("/realms/main/.well-known/openid-configuration",exchange->{
      discoveries.incrementAndGet();
      byte[] response=discovery(issuer).getBytes(StandardCharsets.UTF_8);
      exchange.getResponseHeaders().set("Content-Type","application/json");
      exchange.sendResponseHeaders(200,response.length);
      try(var body=exchange.getResponseBody()){body.write(response);}
    });
    server.start();
    try {
      configured(issuer).run(application->{
        assertThat(application).hasNotFailed();
        ClientRegistration registration=application.getBean("primaryClientRegistration",ClientRegistration.class);
        assertThat(registration.getRegistrationId()).isEqualTo("public-iam");
        assertThat(registration.getClientId()).isEqualTo("aurevia-bff");
        assertThat(registration.getClientSecret()).isEqualTo("runtime-secret");
        assertThat(registration.getScopes()).containsExactlyInAnyOrder("openid","profile","email");
        assertThat(registration.getClientSettings().isRequireProofKey()).isTrue();
        assertThat(registration.getProviderDetails().getIssuerUri()).isEqualTo(issuer);
        assertThat(registration.getProviderDetails().getAuthorizationUri()).isEqualTo(issuer+"/auth");
        assertThat(registration.getProviderDetails().getTokenUri()).isEqualTo(issuer+"/token");
        assertThat(registration.getProviderDetails().getJwkSetUri()).isEqualTo(issuer+"/jwks");
        assertThat(registration.getProviderDetails().getUserInfoEndpoint().getUri()).isEqualTo(issuer+"/userinfo");
        assertThat(registration.getProviderDetails().getConfigurationMetadata())
            .containsEntry("end_session_endpoint",issuer+"/logout");
      });
      assertThat(discoveries).hasValue(1);
    } finally {server.stop(0);}
  }

  @Test void discoveryFailureCannotSilentlyUseEndpointOverrides(){
    configured("http://127.0.0.1:1/realms/main")
        .withPropertyValues("aurevia.oidc.authorization-uri=https://issuer.example/auth",
            "aurevia.oidc.token-uri=https://issuer.example/token",
            "aurevia.oidc.jwk-set-uri=https://issuer.example/jwks",
            "aurevia.oidc.user-info-uri=https://issuer.example/userinfo")
        .run(application->{
          assertThat(application).hasFailed();
          assertThat(application.getStartupFailure()).hasRootCauseMessage(
              "OIDC discovery failed. Check OIDC_ISSUER_URI, issuer reachability, TLS trust and the issuer discovery document");
          assertThat(application.getStartupFailure().toString()).doesNotContain("runtime-secret");
        });
  }

  @Test void explicitSplitAddressingRequiresCompleteRuntimeEndpoints(){
    configured("https://issuer.example/realms/main")
        .withPropertyValues("aurevia.oidc.endpoint-overrides-enabled=true")
        .run(application->{
          assertThat(application).hasFailed();
          assertThat(application.getStartupFailure()).hasRootCauseMessage(
              "OIDC endpoint overrides are incomplete. Missing required configuration: OIDC_AUTHORIZATION_URI, OIDC_TOKEN_URI, OIDC_JWK_SET_URI, OIDC_USER_INFO_URI");
        });
  }

  @Test void explicitSplitAddressingUsesRuntimeEndpointsAndPreservesIssuer(){
    configured("http://localhost:8180/realms/aurevia")
        .withPropertyValues("aurevia.oidc.endpoint-overrides-enabled=true",
            "aurevia.oidc.authorization-uri=http://localhost:8180/auth",
            "aurevia.oidc.token-uri=http://host.docker.internal:8180/token",
            "aurevia.oidc.jwk-set-uri=http://host.docker.internal:8180/jwks",
            "aurevia.oidc.user-info-uri=http://host.docker.internal:8180/userinfo",
            "aurevia.oidc.end-session-uri=http://localhost:8180/logout")
        .run(application->{
          assertThat(application).hasNotFailed();
          var registration=application.getBean("primaryClientRegistration",ClientRegistration.class);
          assertThat(registration.getProviderDetails().getIssuerUri()).isEqualTo("http://localhost:8180/realms/aurevia");
          assertThat(registration.getProviderDetails().getTokenUri()).isEqualTo("http://host.docker.internal:8180/token");
          assertThat(registration.getProviderDetails().getConfigurationMetadata())
              .containsEntry("end_session_endpoint","http://localhost:8180/logout");
        });
  }

  @Test void invalidIssuerAndScopesFailBeforeNetworkAccess(){
    configured("https://user:do-not-log@issuer.example/realm").run(application->{
      assertThat(application).hasFailed();
      assertThat(application.getStartupFailure()).hasRootCauseMessage(
          "OIDC_ISSUER_URI must be an absolute HTTP(S) URL without credentials, query or fragment");
      assertThat(application.getStartupFailure().toString()).doesNotContain("do-not-log");
    });
    configured("https://issuer.example/realms/main").withPropertyValues("aurevia.oidc.scopes=email")
        .run(application->{
          assertThat(application).hasFailed();
          assertThat(application.getStartupFailure()).hasRootCauseMessage("OIDC_SCOPES must include openid");
        });
  }

  private ApplicationContextRunner configured(String issuer){
    return context.withPropertyValues("aurevia.oidc.issuer-uri="+issuer,
        "aurevia.oidc.client-id=aurevia-bff","aurevia.oidc.client-secret=runtime-secret");
  }

  private static String discovery(String issuer){
    return """
        {"issuer":"%1$s","authorization_endpoint":"%1$s/auth","token_endpoint":"%1$s/token",
         "jwks_uri":"%1$s/jwks","userinfo_endpoint":"%1$s/userinfo","end_session_endpoint":"%1$s/logout",
         "response_types_supported":["code"],"subject_types_supported":["public"],
         "id_token_signing_alg_values_supported":["RS256"]}
        """.formatted(issuer);
  }
}

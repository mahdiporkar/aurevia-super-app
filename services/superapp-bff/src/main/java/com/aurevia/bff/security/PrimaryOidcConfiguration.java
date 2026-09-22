package com.aurevia.bff.security;

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistration.ClientSettings;
import org.springframework.security.oauth2.client.registration.ClientRegistrations;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;

/** The primary login registration is validated eagerly and never reads the application registry. */
@Configuration(proxyBeanMethods = false)
public class PrimaryOidcConfiguration {
  public static final String REGISTRATION_ID = "public-iam";
  private static final String PREFIX = "aurevia.oidc.";

  @Bean("primaryClientRegistration")
  public ClientRegistration primaryClientRegistration(Environment environment) {
    List<String> missing = new ArrayList<>();
    String issuer = required(environment, "issuer-uri", "OIDC_ISSUER_URI", missing);
    String clientId = required(environment, "client-id", "OIDC_CLIENT_ID", missing);
    String secret = required(environment, "client-secret", "OIDC_CLIENT_SECRET", missing);
    if (!missing.isEmpty()) {
      throw new IllegalStateException("OIDC configuration is incomplete. Missing required configuration: "
          + String.join(", ", missing));
    }
    validateUri(issuer, "OIDC_ISSUER_URI");
    List<String> scopes = Arrays.stream(environment.getProperty(PREFIX + "scopes", "openid,profile,email")
        .split(",")).map(String::trim).filter(value -> !value.isEmpty()).distinct().toList();
    if (!scopes.contains("openid")) {
      throw new IllegalStateException("OIDC_SCOPES must include openid");
    }

    ClientRegistration.Builder registration;
    if (environment.getProperty(PREFIX + "endpoint-overrides-enabled", Boolean.class, false)) {
      // Explicit transport override for split browser/container addressing. It is disabled by default.
      String authorization = required(environment, "authorization-uri", "OIDC_AUTHORIZATION_URI", missing);
      String token = required(environment, "token-uri", "OIDC_TOKEN_URI", missing);
      String jwks = required(environment, "jwk-set-uri", "OIDC_JWK_SET_URI", missing);
      String userInfo = required(environment, "user-info-uri", "OIDC_USER_INFO_URI", missing);
      if (!missing.isEmpty()) {
        throw new IllegalStateException("OIDC endpoint overrides are incomplete. Missing required configuration: "
            + String.join(", ", missing));
      }
      validateUri(authorization, "OIDC_AUTHORIZATION_URI");
      validateUri(token, "OIDC_TOKEN_URI");
      validateUri(jwks, "OIDC_JWK_SET_URI");
      validateUri(userInfo, "OIDC_USER_INFO_URI");
      registration = ClientRegistration.withRegistrationId(REGISTRATION_ID)
          .issuerUri(issuer).authorizationUri(authorization).tokenUri(token)
          .jwkSetUri(jwks).userInfoUri(userInfo);
      String endSession = environment.getProperty(PREFIX + "end-session-uri", "");
      if (!endSession.isBlank()) {
        validateUri(endSession, "OIDC_END_SESSION_URI");
        registration.providerConfigurationMetadata(Map.of("end_session_endpoint", endSession));
      }
    } else {
      try {
        // Spring validates that the discovered issuer matches the configured issuer.
        registration = ClientRegistrations.fromIssuerLocation(issuer).registrationId(REGISTRATION_ID);
      } catch (RuntimeException failure) {
        // Do not propagate remote response bodies or URLs that could contain sensitive data.
        throw new IllegalStateException("OIDC discovery failed. Check OIDC_ISSUER_URI, issuer reachability, "
            + "TLS trust and the issuer discovery document");
      }
    }
    return registration.clientName("Aurevia").clientId(clientId).clientSecret(secret)
        .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
        .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
        .clientSettings(ClientSettings.builder().requireProofKey(true).build())
        .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
        .scope(scopes).userNameAttributeName("sub").build();
  }

  private static String required(Environment environment, String property, String variable, List<String> missing) {
    String value = environment.getProperty(PREFIX + property, "");
    if (value.isBlank()) missing.add(variable);
    return value;
  }

  private static void validateUri(String value, String variable) {
    try {
      URI uri = URI.create(value);
      if (!("http".equals(uri.getScheme()) || "https".equals(uri.getScheme()))
          || uri.getHost() == null || uri.getUserInfo() != null || uri.getRawQuery() != null
          || uri.getRawFragment() != null) throw new IllegalArgumentException();
    } catch (IllegalArgumentException failure) {
      throw new IllegalStateException(variable + " must be an absolute HTTP(S) URL without credentials, query or fragment");
    }
  }
}

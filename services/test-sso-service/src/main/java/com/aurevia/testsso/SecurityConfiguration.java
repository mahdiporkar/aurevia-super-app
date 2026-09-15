package com.aurevia.testsso;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;

@Configuration
class SecurityConfiguration {
  @Bean JwtDecoder decoder(@Value("${test-sso.issuer}") String issuer,
      @Value("${test-sso.jwks-uri}") String jwks, @Value("${test-sso.audience}") String audience) {
    if (audience.isBlank()) throw new IllegalArgumentException("An expected audience is required");
    NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(jwks).build();
    JwtTimestampValidator time = new JwtTimestampValidator(java.time.Duration.ZERO);
    OAuth2TokenValidator<Jwt> identity = jwt ->
        jwt.getSubject() != null && !jwt.getSubject().isBlank() && jwt.getAudience().contains(audience)
        ? OAuth2TokenValidatorResult.success()
        : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "Subject or audience missing", null));
    decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(time, new JwtIssuerValidator(issuer), identity));
    return decoder;
  }
  @Bean SecurityFilterChain security(HttpSecurity http) throws Exception {
    return http.csrf(csrf -> csrf.disable())
        .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(auth -> auth.requestMatchers("/health").permitAll().anyRequest().authenticated())
        .oauth2ResourceServer(server -> server.jwt(jwt -> {}).bearerTokenResolver(new DefaultBearerTokenResolver()))
        .build();
  }
}

package com.aurevia.bff.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.csrf.CsrfWebFilter;
import org.springframework.security.web.server.util.matcher.AndServerWebExchangeMatcher;
import org.springframework.security.web.server.util.matcher.NegatedServerWebExchangeMatcher;
import org.springframework.security.web.server.util.matcher.OrServerWebExchangeMatcher;
import org.springframework.security.web.server.util.matcher.PathPatternParserServerWebExchangeMatcher;
import org.springframework.security.web.server.SecurityWebFilterChain;

@Configuration
class SecurityConfig {
  @Bean SecurityWebFilterChain security(ServerHttpSecurity http, VaultLogoutHandler vaultLogout,
      OidcLoginSuccessHandler loginSuccess,
      TokenFreeSecurityContextRepository securityContexts) {
    var supersetProxy = new PathPatternParserServerWebExchangeMatcher("/api/v1/superset/**");
    var namedSupersetProxy = new PathPatternParserServerWebExchangeMatcher(
        "/api/v1/superset-instances/**");
    return http.authorizeExchange(a -> a
          .pathMatchers("/actuator/health/**", "/", "/auth/login", "/auth/callback").permitAll()
          .anyExchange().authenticated())
        // Superset validates its own CSRF token. All other BFF mutations retain Spring CSRF protection.
        .csrf(csrf -> csrf.requireCsrfProtectionMatcher(new AndServerWebExchangeMatcher(
            CsrfWebFilter.DEFAULT_CSRF_MATCHER,
            new NegatedServerWebExchangeMatcher(new OrServerWebExchangeMatcher(
                supersetProxy,namedSupersetProxy)))))
        .securityContextRepository(securityContexts)
        .oauth2Login(o -> o.authenticationSuccessHandler(loginSuccess))
        .logout(l -> l.logoutUrl("/auth/logout").logoutHandler(vaultLogout)).build();
  }
}

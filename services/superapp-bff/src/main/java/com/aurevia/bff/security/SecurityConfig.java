package com.aurevia.bff.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;

@Configuration
class SecurityConfig {
  @Bean SecurityWebFilterChain security(ServerHttpSecurity http, VaultLogoutHandler vaultLogout) {
    return http.authorizeExchange(a -> a
          .pathMatchers("/actuator/health/**", "/", "/auth/login", "/auth/callback").permitAll()
          .anyExchange().authenticated())
        // CSRF remains enabled. The cookie is HttpOnly; clients receive a separate CSRF token contract.
        .oauth2Login(o -> {}).logout(l -> l.logoutUrl("/auth/logout").logoutHandler(vaultLogout)).build();
  }
}

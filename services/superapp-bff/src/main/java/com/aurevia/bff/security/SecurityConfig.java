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
import org.springframework.http.HttpStatus;
import org.springframework.security.web.server.authentication.RedirectServerAuthenticationEntryPoint;
import org.springframework.security.web.server.authentication.logout.HttpStatusReturningServerLogoutSuccessHandler;

@Configuration
class SecurityConfig {
  @Bean SecurityWebFilterChain security(ServerHttpSecurity http, VaultLogoutHandler vaultLogout,
      OidcLoginSuccessHandler loginSuccess,
      TokenFreeSecurityContextRepository securityContexts) {
    var supersetProxy = new PathPatternParserServerWebExchangeMatcher("/api/v1/superset/**");
    var namedSupersetProxy = new PathPatternParserServerWebExchangeMatcher(
        "/api/v1/superset-instances/**");
    var integrationSupersetProxy = new PathPatternParserServerWebExchangeMatcher(
        "/api/integrations/superset/**");
    var loginEntryPoint=new RedirectServerAuthenticationEntryPoint(
        "/oauth2/authorization/public-iam");
    return http.authorizeExchange(a -> a
          .pathMatchers("/actuator/health/**", "/", "/auth/login", "/auth/callback").permitAll()
          .anyExchange().authenticated())
        // Fetch/XHR must receive 401; following an OAuth redirect inside fetch becomes a CORS error.
        .exceptionHandling(errors->errors.authenticationEntryPoint((exchange,failure)->{
          if(exchange.getRequest().getPath().value().startsWith("/api/")) {
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return exchange.getResponse().setComplete();
          }
          return loginEntryPoint.commence(exchange,failure);
        }))
        // Superset validates its own CSRF token. All other BFF mutations retain Spring CSRF protection.
        .csrf(csrf -> csrf.requireCsrfProtectionMatcher(new AndServerWebExchangeMatcher(
            CsrfWebFilter.DEFAULT_CSRF_MATCHER,
            new NegatedServerWebExchangeMatcher(new OrServerWebExchangeMatcher(
                supersetProxy,namedSupersetProxy,integrationSupersetProxy)))))
        .securityContextRepository(securityContexts)
        .oauth2Login(o -> o.authenticationSuccessHandler(loginSuccess))
        .logout(l -> l.logoutUrl("/auth/logout")
            .logoutHandler(vaultLogout)
            .logoutSuccessHandler(new HttpStatusReturningServerLogoutSuccessHandler(
                HttpStatus.NO_CONTENT))).build();
  }
}

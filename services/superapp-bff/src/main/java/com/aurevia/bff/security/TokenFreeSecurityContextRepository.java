package com.aurevia.bff.security;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.web.server.context.ServerSecurityContextRepository;
import org.springframework.security.web.server.context.WebSessionServerSecurityContextRepository;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/** Persists authentication without OIDC token values or the full OIDC principal. */
@Component
public final class TokenFreeSecurityContextRepository implements ServerSecurityContextRepository {
  private final WebSessionServerSecurityContextRepository delegate =
      new WebSessionServerSecurityContextRepository();

  @Override
  public Mono<Void> save(ServerWebExchange exchange, SecurityContext context) {
    if (context == null || context.getAuthentication() == null) {
      return delegate.save(exchange, context);
    }
    Authentication authentication = context.getAuthentication();
    if (authentication.getPrincipal() instanceof SessionIdentity) {
      return delegate.save(exchange, context);
    }
    SessionIdentity identity = SessionIdentity.from(authentication);
    Authentication tokenFree = UsernamePasswordAuthenticationToken.authenticated(
        identity, null, authentication.getAuthorities().stream()
            // OidcUserAuthority retains the OIDC ID token. Persist only the
            // authority name, never the framework's credential-bearing object.
            .map(GrantedAuthority::getAuthority)
            .filter(value -> value != null && !value.isBlank())
            .distinct()
            .map(SimpleGrantedAuthority::new)
            .toList());
    return delegate.save(exchange, new SecurityContextImpl(tokenFree));
  }

  @Override
  public Mono<SecurityContext> load(ServerWebExchange exchange) {
    return delegate.load(exchange);
  }
}

package com.aurevia.bff.security;
import org.springframework.security.core.Authentication;
import org.springframework.http.ResponseCookie;
import org.springframework.security.web.server.WebFilterExchange;
import org.springframework.security.web.server.authentication.logout.ServerLogoutHandler;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
@Component public class VaultLogoutHandler implements ServerLogoutHandler {
 public static final String HANDLE="TOKEN_VAULT_HANDLE"; private final TokenVaultService vault;
 public VaultLogoutHandler(TokenVaultService vault){this.vault=vault;}
 @Override public Mono<Void> logout(WebFilterExchange exchange,Authentication authentication){
  ServerWebExchange serverExchange=exchange.getExchange();
  expireCookie(serverExchange,"AUREVIA_OPERATION_SUPERSET");
  expireCookie(serverExchange,"AUREVIA_SESSION");
  return serverExchange.getSession().flatMap(session->{
   Object handle=session.getAttribute(HANDLE);
   Mono<?> deletion=handle instanceof String h?vault.delete(h):Mono.empty();
   // Clearing the persisted state and expiring its opaque client handle revokes access
   // without invalidating the reactive Redis session while WebFlux is still saving it.
   return deletion.doOnSuccess(ignored->session.getAttributes().clear()).then();
  });
 }

 static void expireSupersetSession(ServerWebExchange exchange) {
  expireCookie(exchange,"AUREVIA_OPERATION_SUPERSET");
 }

 private static void expireCookie(ServerWebExchange exchange,String name) {
  exchange.getResponse().addCookie(ResponseCookie
      .from(name, "")
      .httpOnly(true)
      .sameSite("Lax")
      .path("/")
      .maxAge(0)
      .build());
 }
}

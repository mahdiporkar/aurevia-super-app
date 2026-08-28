package com.aurevia.bff.security;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.server.WebFilterExchange;
import org.springframework.security.web.server.authentication.logout.ServerLogoutHandler;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
@Component public class VaultLogoutHandler implements ServerLogoutHandler {
 public static final String HANDLE="TOKEN_VAULT_HANDLE"; private final TokenVaultService vault;
 public VaultLogoutHandler(TokenVaultService vault){this.vault=vault;}
 @Override public Mono<Void> logout(WebFilterExchange exchange,Authentication authentication){return exchange.getExchange().getSession().flatMap(session->{Object handle=session.getAttribute(HANDLE);Mono<?> deletion=handle instanceof String h?vault.delete(h):Mono.empty();return deletion.then(session.invalidate());});}
}

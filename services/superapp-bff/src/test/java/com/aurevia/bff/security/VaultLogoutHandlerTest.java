package com.aurevia.bff.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.server.WebFilterExchange;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

class VaultLogoutHandlerTest {
  @Test
  void deletesCredentialHandleAndClearsTheTokenFreeSessionWithoutInvalidatingIt() {
    TokenVaultService vault=mock(TokenVaultService.class);
    when(vault.delete("vault-handle")).thenReturn(Mono.just(true));
    var exchange=MockServerWebExchange.from(MockServerHttpRequest.post("/auth/logout").build());
    var session=exchange.getSession().block();
    session.getAttributes().put(VaultLogoutHandler.HANDLE,"vault-handle");
    session.getAttributes().put("SPRING_SECURITY_CONTEXT","token-free-context");

    new VaultLogoutHandler(vault).logout(new WebFilterExchange(exchange,mock(WebFilterChain.class)),
        mock(Authentication.class)).block();

    verify(vault).delete("vault-handle");
    Object remainingHandle=session.getAttribute(VaultLogoutHandler.HANDLE);
    assertThat(remainingHandle).isNull();
    assertThat(session.getAttributes()).isEmpty();
    var expiredSessionCookie=exchange.getResponse().getCookies().getFirst("AUREVIA_SESSION");
    assertThat(expiredSessionCookie).isNotNull();
    assertThat(expiredSessionCookie.getMaxAge().isZero()).isTrue();
  }
}

package com.aurevia.bff.api;

import java.util.Map;
import org.springframework.security.web.server.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@RestController
class CsrfController {

  @GetMapping("/api/v1/csrf")
  Mono<Map<String, String>> csrf(ServerWebExchange exchange) {
    Mono<CsrfToken> token = exchange.getAttribute(CsrfToken.class.getName());
    if (token == null) {
      return Mono.error(new IllegalStateException("CSRF token is unavailable"));
    }

    return token.map(value -> Map.of(
        "headerName", value.getHeaderName(),
        "parameterName", value.getParameterName(),
        "token", value.getToken()));
  }
}

package com.aurevia.bff.api;

import com.aurevia.bff.proxy.RouteNormalizer;
import com.aurevia.bff.security.TokenRefreshService;
import com.aurevia.bff.security.TokenVaultService;
import com.aurevia.bff.security.VaultLogoutHandler;
import java.security.Principal;
import java.time.Duration;
import java.util.Map;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferLimitException;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/** Registry-driven operational proxy. The Public IAM bearer is forwarded unchanged. */
@RestController
public class OperationalProxyController {
  private final AuthorizationServiceClient authorization;
  private final TokenVaultService vault;
  private final TokenRefreshService tokenRefresh;
  private final WebClient gateway;

  public OperationalProxyController(AuthorizationServiceClient authorization,
      TokenVaultService vault, TokenRefreshService tokenRefresh,
      @Qualifier("operationGatewayClient") WebClient gateway) {
    this.authorization = authorization;
    this.vault = vault;
    this.tokenRefresh = tokenRefresh;
    this.gateway = gateway;
  }

  @RequestMapping("/{panelSlug}/{*path}")
  public Mono<Void> proxy(@PathVariable("panelSlug") String panelSlug,
      @PathVariable("path") String ignored, ServerWebExchange exchange,
      Principal principal) {
    String path = RouteNormalizer.normalizePath(exchange.getRequest().getPath().value());
    String method = exchange.getRequest().getMethod().name();
    return authorization.resolveRoute(path, method)
        .flatMap(route -> authorize(route, principal, exchange).thenReturn(route))
        .flatMap(route -> exchange.getSession().flatMap(session -> {
          String handle = session.getAttribute(VaultLogoutHandler.HANDLE);
          if (handle == null) return Mono.error(new ResponseStatusException(
              HttpStatus.UNAUTHORIZED, "Token vault session missing"));
          return vault.read(handle)
              .flatMap(tokens -> tokenRefresh.ensureFresh(handle, tokens))
              .flatMap(tokens -> forward(route, handle, tokens, exchange, principal.getName()));
        }));
  }

  private Mono<Void> authorize(Map route, Principal principal, ServerWebExchange exchange) {
    if (Boolean.FALSE.equals(route.get("authorizationRequired"))) return Mono.empty();
    Map<String, Object> request = Map.of(
        "subjectId", principal.getName(), "issuer", "public-iam",
        "resource", "resource:" + String.valueOf(route.get("resourceKey")).replace(':','/'),
        "action", route.get("actionKey"), "context", Map.of(
            "panel",route.get("panelSlug"),"httpMethod",exchange.getRequest().getMethod().name(),
            "normalizedPath",RouteNormalizer.normalizePath(exchange.getRequest().getPath().value()),
            "routeId",route.get("routeId"),"operationId",route.get("operationId")),
        "correlationId", correlationId(exchange));
    return authorization.check(request).flatMap(decision -> "ALLOW".equals(decision.get("result"))
        ? Mono.empty() : Mono.error(new ResponseStatusException(HttpStatus.FORBIDDEN,
            String.valueOf(decision.get("reasonCode")))));
  }

  private Mono<Void> forward(Map route, String handle, TokenVaultService.Tokens tokens,
      ServerWebExchange exchange, String subject) {
    long declaredLength = exchange.getRequest().getHeaders().getContentLength();
    long maxBody = ((Number) route.get("maxBodyBytes")).longValue();
    if (declaredLength > maxBody || maxBody > Integer.MAX_VALUE) return Mono.error(new ResponseStatusException(
        HttpStatus.PAYLOAD_TOO_LARGE));
    return readBody(exchange, (int) maxBody).flatMap(body ->
        call(route, tokens.accessToken(), exchange, body, subject)
            .flatMap(first -> first.status().value() == 401
                ? tokenRefresh.refresh(handle, tokens)
                    .flatMap(refreshed -> call(route, refreshed.accessToken(), exchange, body, subject))
                : Mono.just(first))
            .flatMap(response -> writeResponse(route, response, exchange)));
  }

  private Mono<byte[]> readBody(ServerWebExchange exchange, int maxBody) {
    return DataBufferUtils.join(exchange.getRequest().getBody(), maxBody)
        .map(buffer -> {
          byte[] bytes = new byte[buffer.readableByteCount()];
          buffer.read(bytes);
          DataBufferUtils.release(buffer);
          return bytes;
        })
        .defaultIfEmpty(new byte[0])
        .onErrorMap(DataBufferLimitException.class,
            error -> new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE));
  }

  private Mono<UpstreamResponse> call(Map route, String accessToken,
      ServerWebExchange exchange, byte[] body, String subject) {
    String query = exchange.getRequest().getURI().getRawQuery();
    String uri = upstreamPath(route,exchange.getRequest().getPath().value())
        + (query == null ? "" : "?" + query);
    var request = gateway.method(exchange.getRequest().getMethod()).uri(uri)
        .headers(headers -> {
          headers.setBearerAuth(accessToken);
          copy(exchange.getRequest().getHeaders(), headers, HttpHeaders.ACCEPT);
          copy(exchange.getRequest().getHeaders(), headers, HttpHeaders.CONTENT_TYPE);
          headers.set("X-Correlation-ID", correlationId(exchange));
          headers.set("X-Aurevia-Subject", subject);
        });
    Mono<UpstreamResponse> response = (body.length == 0 ? request : request.bodyValue(body))
        .exchangeToMono(upstream -> upstream.bodyToMono(byte[].class)
            .defaultIfEmpty(new byte[0])
            .map(bytes -> new UpstreamResponse(upstream.statusCode(),
                upstream.headers().asHttpHeaders(), bytes)));
    long timeout = ((Number) route.get("responseTimeoutMs")).longValue();
    return response.timeout(Duration.ofMillis(timeout));
  }

  private Mono<Void> writeResponse(Map route, UpstreamResponse response,
      ServerWebExchange exchange) {
    long maxResponse = ((Number) route.get("maxResponseBytes")).longValue();
    if (response.body().length > maxResponse) {
      return Mono.error(new ResponseStatusException(HttpStatus.BAD_GATEWAY,
          "Operation response exceeded the registered limit"));
    }
    exchange.getResponse().setStatusCode(response.status());
    copy(response.headers(), exchange.getResponse().getHeaders(), HttpHeaders.CONTENT_TYPE);
    copy(response.headers(), exchange.getResponse().getHeaders(), HttpHeaders.CONTENT_DISPOSITION);
    DataBuffer data = exchange.getResponse().bufferFactory().wrap(response.body());
    return exchange.getResponse().writeWith(Mono.just(data));
  }

  private static void copy(HttpHeaders source, HttpHeaders target, String name) {
    if (source.containsKey(name)) target.put(name, source.get(name));
  }
  private static String upstreamPath(Map route,String incoming) {
    String path=incoming;
    int strip=((Number)route.getOrDefault("stripPrefix",0)).intValue();
    String prefix=String.valueOf(route.get("pathPrefix"));
    if(strip>0) {
      String[] segments=prefix.substring(1).split("/");
      int index=0;
      for(int i=0;i<strip && i<segments.length;i++) index=path.indexOf('/',index+1);
      path=index<0?"/":path.substring(index);
    }
    String pattern=(String)route.get("rewritePattern");
    String replacement=(String)route.get("rewriteReplacement");
    if(pattern!=null && replacement!=null && pattern.startsWith("^/") && path.startsWith(pattern.substring(1)))
      path=replacement+path.substring(pattern.length()-1);
    return RouteNormalizer.normalizePath(path);
  }
  private static String correlationId(ServerWebExchange exchange) {
    String value = exchange.getRequest().getHeaders().getFirst("X-Correlation-ID");
    return value == null || value.isBlank() ? exchange.getRequest().getId() : value;
  }

  private record UpstreamResponse(HttpStatusCode status, HttpHeaders headers, byte[] body) {}
}

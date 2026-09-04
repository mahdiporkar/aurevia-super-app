package com.aurevia.bff.api;

import com.aurevia.bff.proxy.RouteNormalizer;
import com.aurevia.bff.security.TokenRefreshService;
import com.aurevia.bff.security.TokenVaultService;
import com.aurevia.bff.security.VaultLogoutHandler;
import com.aurevia.bff.security.SessionIdentity;
import com.aurevia.bff.outboundauth.*;
import com.aurevia.bff.observability.DevelopmentTokenEvidenceLogger;
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
  private final java.util.List<OutboundTokenProvider> outboundProviders;
  private final DevelopmentTokenEvidenceLogger tokenEvidence;

  public OperationalProxyController(AuthorizationServiceClient authorization,
      TokenVaultService vault, TokenRefreshService tokenRefresh,
      @Qualifier("operationGatewayClient") WebClient gateway,
      java.util.List<OutboundTokenProvider> outboundProviders,
      DevelopmentTokenEvidenceLogger tokenEvidence) {
    this.authorization = authorization;
    this.vault = vault;
    this.tokenRefresh = tokenRefresh;
    this.gateway = gateway;
    this.outboundProviders=outboundProviders;
    this.tokenEvidence=tokenEvidence;
  }

  @RequestMapping("/{panelSlug}/{*path}")
  public Mono<Void> proxy(@PathVariable("panelSlug") String panelSlug,
      @PathVariable("path") String ignored, ServerWebExchange exchange,
      Principal principal) {
    SessionIdentity identity = SessionIdentity.from(principal);
    String path = RouteNormalizer.normalizePath(exchange.getRequest().getPath().value());
    String method = exchange.getRequest().getMethod().name();
    return authorization.resolveRoute(path, method)
        .flatMap(route -> authorize(route, identity, exchange).thenReturn(route))
        .flatMap(route -> exchange.getSession().flatMap(session -> {
          String handle = session.getAttribute(VaultLogoutHandler.HANDLE);
          if (handle == null) return Mono.error(new ResponseStatusException(
              HttpStatus.UNAUTHORIZED, "Token vault session missing"));
          return vault.read(handle)
              .flatMap(tokens -> tokenRefresh.ensureFresh(handle, tokens))
              .flatMap(tokens -> forward(route, handle, tokens, exchange,
                  identity.subject(), identity.issuer()));
        }));
  }

  private Mono<Void> authorize(RouteResolution route, SessionIdentity identity,
      ServerWebExchange exchange) {
    if (!route.authorizationRequired()) return Mono.empty();
    Map<String, Object> request = Map.of(
        "subjectId", identity.subject(), "issuer", identity.issuer(),
        "resource", "resource:" + route.resourceKey().replace(':','/'),
        "action", route.actionKey(), "context", Map.of(
            "panel",route.panelSlug(),"httpMethod",exchange.getRequest().getMethod().name(),
            "normalizedPath",RouteNormalizer.normalizePath(exchange.getRequest().getPath().value()),
            "routeId",route.routeId(),"operationId",route.operationId()),
        "correlationId", correlationId(exchange));
    return authorization.check(request).flatMap(decision -> "ALLOW".equals(decision.get("result"))
        ? Mono.empty() : Mono.error(new ResponseStatusException(HttpStatus.FORBIDDEN,
            String.valueOf(decision.get("reasonCode")))));
  }

  private Mono<Void> forward(RouteResolution route, String handle, TokenVaultService.Tokens tokens,
      ServerWebExchange exchange, String subject, String issuer) {
    long declaredLength = exchange.getRequest().getHeaders().getContentLength();
    long maxBody = route.maxBodyBytes();
    if (declaredLength > maxBody || maxBody > Integer.MAX_VALUE) return Mono.error(new ResponseStatusException(
        HttpStatus.PAYLOAD_TOO_LARGE));
    OutboundAuthMode mode=OutboundAuthMode.valueOf(route.authMode());
    OutboundTokenProvider provider=outboundProviders.stream().filter(p->p.supports(mode)).findFirst()
        .orElseThrow(()->new ResponseStatusException(HttpStatus.BAD_GATEWAY,"Outbound authentication unavailable"));
    var target=new OutboundTokenProvider.ServiceTarget(route.targetId().toString(),
        route.authProfileId().toString(),mode,route.authProfileVersion());
    var session=new OutboundTokenProvider.AuthenticatedSession(subject,tokens.accessToken());
    var context=new OutboundTokenProvider.RequestContext(correlationId(exchange),
        route.routeId().toString(),route.operationId().toString());
    return readBody(exchange, (int) maxBody).flatMap(body -> provider.resolve(target,session,context).flatMap(credential->
        call(route, tokens.accessToken(), credential, exchange, body, subject, issuer)
            .flatMap(first -> first.status().value() == 401 && route.retryEnabled()
                && route.maxRetries()>0
                ? retryOnce(route,handle,tokens,provider,target,session,context,credential,
                    exchange,body,subject,issuer)
                : Mono.just(first)))
            .flatMap(response -> writeResponse(route, response, exchange)));
  }

  private Mono<UpstreamResponse> retryOnce(RouteResolution route,String handle,TokenVaultService.Tokens tokens,
      OutboundTokenProvider provider,OutboundTokenProvider.ServiceTarget target,
      OutboundTokenProvider.AuthenticatedSession session,OutboundTokenProvider.RequestContext context,
      OutboundCredential rejected,ServerWebExchange exchange,byte[] body,String subject,
      String issuer){
    if(rejected.legacy())return provider.invalidate(target,OutboundTokenProvider.InvalidationReason.UPSTREAM_REJECTED)
        .then(provider.resolve(target,session,context)).flatMap(fresh->call(route,tokens.accessToken(),
            fresh,exchange,body,subject,issuer));
    return tokenRefresh.refresh(handle,tokens).flatMap(refreshed->call(route,refreshed.accessToken(),
        new OutboundCredential("Bearer",refreshed.accessToken(),false),exchange,body,subject,issuer));
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

  private Mono<UpstreamResponse> call(RouteResolution route, String publicAccessToken, OutboundCredential outbound,
      ServerWebExchange exchange, byte[] body, String subject, String issuer) {
    String correlation=correlationId(exchange);
    tokenEvidence.dispatch(correlation,route.routeId().toString(),route.operationId().toString(),
        route.authMode(),subject,publicAccessToken,outbound);
    String query = exchange.getRequest().getURI().getRawQuery();
    String uri = upstreamPath(route,exchange.getRequest().getPath().value())
        + (query == null ? "" : "?" + query);
    var request = gateway.method(exchange.getRequest().getMethod()).uri(uri)
        .headers(headers -> {
          headers.remove("X-Internal-Legacy-Authorization");
          headers.setBearerAuth(publicAccessToken);
          if(outbound.legacy())headers.set("X-Internal-Legacy-Authorization",outbound.scheme()+" "+outbound.token());
          copy(exchange.getRequest().getHeaders(), headers, HttpHeaders.ACCEPT);
          copy(exchange.getRequest().getHeaders(), headers, HttpHeaders.CONTENT_TYPE);
          headers.set("X-Correlation-ID", correlation);
          headers.set("X-Aurevia-Subject", subject);
          headers.set("X-Aurevia-Issuer", issuer);
        });
    Mono<UpstreamResponse> response = (body.length == 0 ? request : request.bodyValue(body))
        .exchangeToMono(upstream -> upstream.bodyToMono(byte[].class)
            .defaultIfEmpty(new byte[0])
            .map(bytes -> new UpstreamResponse(upstream.statusCode(),
                upstream.headers().asHttpHeaders(), bytes)));
    long timeout = route.responseTimeoutMs();
    return response.timeout(Duration.ofMillis(timeout))
        .doOnNext(value->tokenEvidence.result(correlation,route.routeId().toString(),value.status().value()));
  }

  private Mono<Void> writeResponse(RouteResolution route, UpstreamResponse response,
      ServerWebExchange exchange) {
    long maxResponse = route.maxResponseBytes();
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
  private static String upstreamPath(RouteResolution route,String incoming) {
    String path=incoming;
    int strip=route.stripPrefix();
    String prefix=route.pathPrefix();
    if(strip>0) {
      String[] segments=prefix.substring(1).split("/");
      int index=0;
      for(int i=0;i<strip && i<segments.length;i++) index=path.indexOf('/',index+1);
      path=index<0?"/":path.substring(index);
    }
    String pattern=route.rewritePattern();
    String replacement=route.rewriteReplacement();
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

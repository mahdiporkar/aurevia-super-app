package com.aurevia.bff.api;

import com.aurevia.bff.proxy.RouteNormalizer;
import java.net.URI;
import java.security.Principal;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClient.RequestBodySpec;
import org.springframework.web.reactive.function.client.WebClient.RequestHeadersSpec;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import reactor.netty.http.client.HttpClient;
import reactor.core.publisher.Mono;

@RestController
public class OperationSupersetProxyController {
  private static final List<String> REQUEST_HEADERS = List.of(
      HttpHeaders.ACCEPT,
      HttpHeaders.ACCEPT_LANGUAGE,
      HttpHeaders.CONTENT_TYPE,
      HttpHeaders.COOKIE,
      HttpHeaders.IF_NONE_MATCH,
      "X-CSRFToken",
      "X-Superset-CSRFToken");

  private static final List<String> RESPONSE_HEADERS = List.of(
      HttpHeaders.CACHE_CONTROL,
      HttpHeaders.CONTENT_DISPOSITION,
      HttpHeaders.CONTENT_LANGUAGE,
      HttpHeaders.CONTENT_TYPE,
      HttpHeaders.ETAG,
      HttpHeaders.EXPIRES,
      HttpHeaders.LAST_MODIFIED,
      HttpHeaders.SET_COOKIE);

  private final WebClient gatewayClient;
  private final URI gatewayBaseUri;
  private final AuthorizationServiceClient authorization;

  public OperationSupersetProxyController(
      @Value("${aurevia.gateway.base-url}") String gatewayBaseUrl,
      AuthorizationServiceClient authorization) {
    this.authorization = authorization;
    URI allowlistedGateway = RouteNormalizer.allowlistedTarget(gatewayBaseUrl);
    this.gatewayBaseUri = allowlistedGateway;
    HttpClient httpClient = HttpClient.create().followRedirect(false);
    this.gatewayClient = WebClient.builder()
        .clientConnector(new ReactorClientHttpConnector(httpClient))
        .baseUrl(allowlistedGateway.toString())
        .build();
  }

  @RequestMapping("/api/v1/superset/{*path}")
  public Mono<Void> proxy(
      @PathVariable("path") String path,
      ServerWebExchange exchange,
      Principal principal) {
    String safePath = RouteNormalizer.normalizePath(path == null || path.isBlank() ? "/" : path);
    String rawQuery = exchange.getRequest().getURI().getRawQuery();
    return authorization.supersetAccess(principal.getName(), safePath,
            exchange.getRequest().getMethod().name(), rawQuery)
        .flatMap(decision -> "ALLOW".equals(decision.get("result"))
            ? forward(exchange, principal, safePath, rawQuery)
            : Mono.error(new ResponseStatusException(HttpStatus.FORBIDDEN,
                String.valueOf(decision.get("reasonCode")))));
  }

  private Mono<Void> forward(ServerWebExchange exchange, Principal principal,
      String safePath, String rawQuery) {
    String target = "/superset" + safePath + (rawQuery == null ? "" : "?" + rawQuery);
    RequestBodySpec outboundRequest = gatewayClient
        .method(exchange.getRequest().getMethod())
        // rawQuery already comes percent-encoded from the browser. The String
        // overload treats it as a URI template and encodes '%' again (%2F ->
        // %252F), which corrupts Superset's login `next` and Rison queries.
        .uri(gatewayBaseUri.resolve(URI.create(target)))
        .headers(headers -> {
          REQUEST_HEADERS.forEach(name -> copyHeader(exchange.getRequest().getHeaders(), headers, name));
          headers.set("X-Aurevia-Subject", principal.getName());
          headers.set("X-Correlation-ID", correlationId(exchange));
          headers.set("X-Forwarded-Proto", exchange.getRequest().getURI().getScheme());
          headers.set("X-Forwarded-Host", exchange.getRequest().getHeaders().getFirst(HttpHeaders.HOST));
        });

    RequestHeadersSpec<?> requestWithBody = supportsRequestBody(exchange.getRequest().getMethod())
        ? outboundRequest.body(exchange.getRequest().getBody(), DataBuffer.class)
        : outboundRequest;

    return requestWithBody
        .exchangeToMono(response -> {
          HttpHeaders responseHeaders = new HttpHeaders();
          RESPONSE_HEADERS.forEach(name -> copyHeader(response.headers().asHttpHeaders(), responseHeaders, name));
          copyRewrittenLocation(response.headers().asHttpHeaders(), responseHeaders);

          exchange.getResponse().setStatusCode(response.statusCode());
          exchange.getResponse().getHeaders().putAll(responseHeaders);

          // Consume the upstream body while the WebClient response is still alive.
          // Returning its Flux from exchangeToMono releases the response before
          // Spring subscribes to that Flux and results in an empty HTML page.
          return exchange.getResponse().writeWith(response.bodyToFlux(DataBuffer.class));
        });
  }

  private static boolean supportsRequestBody(HttpMethod method) {
    return HttpMethod.POST.equals(method)
        || HttpMethod.PUT.equals(method)
        || HttpMethod.PATCH.equals(method);
  }

  private static void copyHeader(HttpHeaders source, HttpHeaders target, String name) {
    List<String> values = source.get(name);
    if (values != null) {
      target.put(name, values);
    }
  }

  private static void copyRewrittenLocation(HttpHeaders source, HttpHeaders target) {
    String location = source.getFirst(HttpHeaders.LOCATION);
    if (location == null) {
      return;
    }
    if (location.startsWith("/")
        && !location.startsWith("/reports-runtime/")
        && !location.startsWith("/static/")) {
      location = "/reports-runtime" + location;
    }
    target.set(HttpHeaders.LOCATION, location);
  }

  private static String correlationId(ServerWebExchange exchange) {
    String correlationId = exchange.getRequest().getHeaders().getFirst("X-Correlation-ID");
    return correlationId == null || correlationId.isBlank()
        ? exchange.getRequest().getId()
        : correlationId;
  }
}

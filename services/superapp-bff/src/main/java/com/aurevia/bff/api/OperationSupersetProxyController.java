package com.aurevia.bff.api;

import com.aurevia.bff.proxy.RouteNormalizer;
import com.aurevia.bff.proxy.SupersetTargetPolicy;
import java.net.URI;
import java.security.Principal;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferLimitException;
import org.springframework.core.io.buffer.DataBufferUtils;
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
import com.aurevia.bff.security.SessionIdentity;

@RestController
public class OperationSupersetProxyController {
  private static final List<String> REQUEST_HEADERS = List.of(
      HttpHeaders.ACCEPT,
      HttpHeaders.ACCEPT_LANGUAGE,
      HttpHeaders.CONTENT_TYPE,
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
  private final SupersetTargetPolicy targetPolicy;
  private final SupersetRequestInspector requestInspector;
  private final String defaultPublicInstance;
  private static final String SELECTED_INSTANCE = "aurevia.superset.public-instance";

  public OperationSupersetProxyController(
      @Value("${aurevia.gateway.base-url}") String gatewayBaseUrl,
      @Value("${aurevia.superset.default-public-instance}") String defaultPublicInstance,
      AuthorizationServiceClient authorization,
      SupersetTargetPolicy targetPolicy,SupersetRequestInspector requestInspector) {
    this.authorization = authorization;
    this.targetPolicy = targetPolicy;
    this.requestInspector=requestInspector;
    this.defaultPublicInstance = defaultPublicInstance;
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
    return exchange.getSession().flatMap(session -> {
      String selected=session.getAttribute(SELECTED_INSTANCE);
      return proxyFor(selected==null?defaultPublicInstance:selected,path,exchange,principal);
    });
  }

  @RequestMapping("/api/v1/superset-instances/{publicInstance}/{*path}")
  public Mono<Void> proxyInstance(@PathVariable("publicInstance") String publicInstance,
      @PathVariable("path") String path, ServerWebExchange exchange, Principal principal) {
    if(!publicInstance.matches("[a-z][a-z0-9-]{2,79}")) {
      return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST,
          "Invalid Superset instance"));
    }
    return exchange.getSession().flatMap(session -> {
      session.getAttributes().put(SELECTED_INSTANCE,publicInstance);
      return proxyFor(publicInstance,path,exchange,principal);
    });
  }

  private Mono<Void> proxyFor(String publicInstance, String path,
      ServerWebExchange exchange, Principal principal) {
    SessionIdentity identity = SessionIdentity.from(principal);
    String safePath = RouteNormalizer.normalizePath(path == null || path.isBlank() ? "/" : path);
    String rawQuery = exchange.getRequest().getURI().getRawQuery();
    return readBody(exchange).flatMap(body->{
      SupersetRequestInspector.Hint hint=requestInspector.inspect(body);
      return authorization.resolveSupersetProxy(publicInstance).flatMap(target ->
          authorization.supersetAccess(identity.issuer(), identity.subject(),
              String.valueOf(target.get("operation_code")),safePath,
              exchange.getRequest().getMethod().name(),rawQuery,hint.type(),hint.id())
            .flatMap(decision -> "ALLOW".equals(decision.get("result"))
                ? forward(exchange,identity,target,safePath,rawQuery,body,publicInstance)
                : Mono.error(new ResponseStatusException(HttpStatus.FORBIDDEN,
                    String.valueOf(decision.get("reasonCode"))))));
    });
  }

  private Mono<Void> forward(ServerWebExchange exchange, SessionIdentity identity,
      Map target,String safePath,String rawQuery,byte[] body,String publicInstance) {
    URI origin;
    try {
      origin=targetPolicy.validate(String.valueOf(target.get("base_url")),
          Boolean.TRUE.equals(target.get("tls_required")));
    } catch(IllegalArgumentException rejected) {
      return Mono.error(new ResponseStatusException(HttpStatus.BAD_GATEWAY,
          "Superset target is not approved"));
    }
    String targetPath = "/superset-dynamic" + safePath
        + (rawQuery == null ? "" : "?" + rawQuery);
    RequestBodySpec outboundRequest = gatewayClient
        .method(exchange.getRequest().getMethod())
        // rawQuery already comes percent-encoded from the browser. The String
        // overload treats it as a URI template and encodes '%' again (%2F ->
        // %252F), which corrupts Superset's login `next` and Rison queries.
        .uri(gatewayBaseUri.resolve(URI.create(targetPath)))
        .headers(headers -> {
          REQUEST_HEADERS.forEach(name -> copyHeader(exchange.getRequest().getHeaders(), headers, name));
          copyNamespacedCookies(exchange,headers,publicInstance);
          headers.set("X-Aurevia-Subject", identity.subject());
          headers.set("X-Aurevia-Issuer", identity.issuer());
          headers.set("X-Internal-Superset-Origin", origin.toString());
          headers.set("X-Correlation-ID", correlationId(exchange));
          headers.set("X-Forwarded-Proto", exchange.getRequest().getURI().getScheme());
          headers.set("X-Forwarded-Host", exchange.getRequest().getHeaders().getFirst(HttpHeaders.HOST));
        });

    RequestHeadersSpec<?> requestWithBody = body.length>0
        ? outboundRequest.bodyValue(body)
        : outboundRequest;

    return requestWithBody
        .exchangeToMono(response -> {
          HttpHeaders responseHeaders = new HttpHeaders();
          RESPONSE_HEADERS.stream().filter(name->!HttpHeaders.SET_COOKIE.equals(name))
              .forEach(name -> copyHeader(response.headers().asHttpHeaders(), responseHeaders, name));
          copyNamespacedSetCookies(response.headers().asHttpHeaders(),responseHeaders,publicInstance);
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

  private static Mono<byte[]> readBody(ServerWebExchange exchange) {
    if(!supportsRequestBody(exchange.getRequest().getMethod())) return Mono.just(new byte[0]);
    return DataBufferUtils.join(exchange.getRequest().getBody(),2*1024*1024)
        .map(buffer->{
          byte[] bytes=new byte[buffer.readableByteCount()];
          buffer.read(bytes);DataBufferUtils.release(buffer);return bytes;
        }).defaultIfEmpty(new byte[0])
        .onErrorMap(DataBufferLimitException.class,error->new ResponseStatusException(
            HttpStatus.PAYLOAD_TOO_LARGE,"Superset request is too large"));
  }

  private static void copyNamespacedCookies(ServerWebExchange exchange,HttpHeaders target,
      String publicInstance) {
    String prefix=cookiePrefix(publicInstance);
    List<String> cookies=new java.util.ArrayList<>();
    exchange.getRequest().getCookies().forEach((name,values)->{
      if(name.startsWith(prefix)) {
        String upstreamName=name.substring(prefix.length());
        if(upstreamName.matches("[A-Za-z0-9!#$%&'*+.^_`|~-]{1,128}")) {
          values.forEach(cookie->cookies.add(upstreamName+"="+cookie.getValue()));
        }
      }
    });
    if(!cookies.isEmpty()) target.set(HttpHeaders.COOKIE,String.join("; ",cookies));
  }

  private static void copyNamespacedSetCookies(HttpHeaders source,HttpHeaders target,
      String publicInstance) {
    String prefix=cookiePrefix(publicInstance);
    List<String> values=source.get(HttpHeaders.SET_COOKIE);
    if(values==null) return;
    for(String value:values) {
      int separator=value.indexOf(';');
      String pair=separator<0?value:value.substring(0,separator);
      int equals=pair.indexOf('=');
      if(equals<=0) continue;
      String name=pair.substring(0,equals).trim();
      if(!name.matches("[A-Za-z0-9!#$%&'*+.^_`|~-]{1,128}")) continue;
      String attributes=separator<0?"":value.substring(separator);
      attributes=attributes.replaceAll("(?i);\\s*Domain=[^;]*","")
          .replaceAll("(?i);\\s*Path=[^;]*","");
      target.add(HttpHeaders.SET_COOKIE,prefix+name+pair.substring(equals)+attributes+"; Path=/");
    }
  }

  private static String cookiePrefix(String publicInstance) {
    return "AUREVIA_SS_"+publicInstance.replace('-','_')+"__";
  }

  private static void copyHeader(HttpHeaders source, HttpHeaders target, String name) {
    List<String> values = source.get(name);
    if (values != null) {
      target.put(name, values);
    }
  }

  static void copyRewrittenLocation(HttpHeaders source, HttpHeaders target) {
    String location = source.getFirst(HttpHeaders.LOCATION);
    if (location == null) {
      return;
    }
    // Older/upstream proxy configurations may have advertised the tunnel as
    // Superset's application prefix. Never allow that legacy prefix to leak
    // into the post-login dashboard destination.
    location = location.replace("/reports-runtime/superset/", "/superset/")
        .replaceAll("(?i)%2Freports-runtime%2Fsuperset%2F", "%2Fsuperset%2F");
    if (location.startsWith("/")
        && !location.startsWith("/reports-runtime/")
        && !location.startsWith("/superset/")
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

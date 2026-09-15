package com.aurevia.bff.proxy;
import java.util.function.Supplier;
import org.springframework.http.HttpStatusCode;
import reactor.core.publisher.Mono;

/** A 401 may refresh and retry exactly once. A 403 is authoritative and never refreshes. */
public final class ProxyRetryPolicy {
  private ProxyRetryPolicy() {}
  public static <T> Mono<T> execute(Supplier<Mono<Response<T>>> call,Supplier<Mono<Void>> refresh){
    return call.get().flatMap(first -> first.status().value()==401
      ? refresh.get().then(call.get()).flatMap(ProxyRetryPolicy::bodyOrError)
      : bodyOrError(first));
  }
  private static <T> Mono<T> bodyOrError(Response<T> r){return r.status().is2xxSuccessful()?Mono.justOrEmpty(r.body()):Mono.error(new ProxyStatusException(r.status()));}
  public record Response<T>(HttpStatusCode status,T body){}
  public static final class ProxyStatusException extends RuntimeException {public final HttpStatusCode status; public ProxyStatusException(HttpStatusCode s){super("upstream status "+s.value());status=s;}}
}

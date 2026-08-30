package com.aurevia.bff.observability;

import com.aurevia.bff.api.AuthorizationServiceClient;
import java.io.ByteArrayOutputStream;
import java.net.InetSocketAddress;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.reactivestreams.Publisher;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.server.reactive.ServerHttpResponseDecorator;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Component
public class PublicApiLoggingWebFilter implements WebFilter {
  private static final int CAPTURE_LIMIT=65_537;
  private final AuthorizationServiceClient logs;
  public PublicApiLoggingWebFilter(AuthorizationServiceClient logs){this.logs=logs;}
  @Override public Mono<Void> filter(ServerWebExchange exchange,WebFilterChain chain){
    if(exchange.getRequest().getPath().value().startsWith("/actuator"))return chain.filter(exchange);
    long started=System.nanoTime();String correlation=CorrelationIds.normalize(exchange.getRequest().getHeaders().getFirst(CorrelationIds.HEADER));
    exchange.getResponse().getHeaders().set(CorrelationIds.HEADER,correlation);ByteArrayOutputStream errorCapture=new ByteArrayOutputStream();
    var decorated=new ServerHttpResponseDecorator(exchange.getResponse()){
      @Override public Mono<Void> writeWith(Publisher<? extends DataBuffer> body){return super.writeWith(Flux.from(body).doOnNext(buffer->captureIfError(buffer,errorCapture,getStatusCode())));}
      @Override public Mono<Void> writeAndFlushWith(Publisher<? extends Publisher<? extends DataBuffer>> body){return writeWith(Flux.from(body).flatMapSequential(p->p));}
    };
    ServerWebExchange mutated=exchange.mutate().response(decorated).request(builder->builder.header(CorrelationIds.HEADER,correlation)).build();
    return chain.filter(mutated)
      .then(log(exchange,correlation,started,errorCapture,null))
      .onErrorResume(failure->log(exchange,correlation,started,errorCapture,failure).then(Mono.error(failure)))
      .contextWrite(context->context.put(CorrelationIds.CONTEXT_KEY,correlation));
  }
  private Mono<Void> log(ServerWebExchange exchange,String correlation,long started,ByteArrayOutputStream body,Throwable failure){return exchange.getPrincipal().map(java.security.Principal::getName).defaultIfEmpty("").flatMap(user->logs.ingestApiLog(entry(exchange,user,correlation,started,body,failure)).onErrorResume(unavailable->Mono.empty()));}
  private static void captureIfError(DataBuffer buffer,ByteArrayOutputStream output,HttpStatusCode status){if(status==null||status.value()<400||output.size()>=CAPTURE_LIMIT)return;var bytes=new byte[Math.min(buffer.readableByteCount(),CAPTURE_LIMIT-output.size())];int position=buffer.readPosition();buffer.read(bytes);buffer.readPosition(position);output.writeBytes(bytes);}
  private static Map<String,Object> entry(ServerWebExchange x,String user,String correlation,long started,ByteArrayOutputStream body,Throwable failure){Map<String,Object> e=new LinkedHashMap<>();int status=failure==null?(x.getResponse().getStatusCode()==null?200:x.getResponse().getStatusCode().value()):500;e.put("eventTime",Instant.now().toString());put(e,"userId",user.isBlank()?null:user);put(e,"actorType",user.isBlank()?null:"USER");e.put("serviceName","superapp-bff");e.put("httpMethod",x.getRequest().getMethod().name());e.put("routeTemplate",x.getRequest().getPath().value());e.put("statusCode",status);e.put("durationMs",(System.nanoTime()-started)/1_000_000);InetSocketAddress remote=x.getRequest().getRemoteAddress();put(e,"sourceIp",remote==null?null:remote.getAddress().getHostAddress());put(e,"userAgent",limit(x.getRequest().getHeaders().getFirst("User-Agent"),1000));e.put("correlationId",correlation);long requestSize=x.getRequest().getHeaders().getContentLength();put(e,"requestSizeBytes",requestSize<0?null:requestSize);put(e,"responseSizeBytes",x.getResponse().getHeaders().getContentLength()<0?null:x.getResponse().getHeaders().getContentLength());put(e,"errorContentType",x.getResponse().getHeaders().getContentType()==null?null:x.getResponse().getHeaders().getContentType().toString());put(e,"errorResponseBody",status>=400?body.toString(java.nio.charset.StandardCharsets.UTF_8):null);put(e,"errorType",failure==null?null:failure.getClass().getName());return e;}
  private static void put(Map<String,Object> map,String key,Object value){if(value!=null)map.put(key,value);}private static String limit(String v,int n){return v==null?null:v.substring(0,Math.min(v.length(),n));}
}

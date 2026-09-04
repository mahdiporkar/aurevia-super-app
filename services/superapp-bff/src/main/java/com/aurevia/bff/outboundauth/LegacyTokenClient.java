package com.aurevia.bff.outboundauth;

import io.netty.channel.ChannelOption;
import java.net.URI;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;
import reactor.netty.resources.ConnectionProvider;

@Component
final class LegacyTokenClient {
  private final LegacyTargetPolicy targetPolicy;
  private final Map<ClientKey,WebClient> clients=new ConcurrentHashMap<>();
  private final Map<String,EndpointState> endpointStates=new ConcurrentHashMap<>();
  private final ConnectionProvider pool=ConnectionProvider.builder("legacy-token-endpoints")
      .maxConnections(20).pendingAcquireTimeout(Duration.ofSeconds(2)).build();

  LegacyTokenClient(LegacyTargetPolicy targetPolicy) {
    this.targetPolicy=targetPolicy;
  }

  void validate(OutboundConnection connection) { targetPolicy.validate(connection); }

  Mono<byte[]> acquire(OutboundAuthProfile profile,OutboundConnection connection,
      SecretResolver.ResolvedSecret secret) {
    if(!connection.reference().equals(profile.connectionRef())) {
      return Mono.error(new IllegalStateException("Resolved Legacy connection does not match profile"));
    }
    URI origin=targetPolicy.validate(connection);
    EndpointState state=endpointStates.computeIfAbsent(connection.reference(),ignored->new EndpointState());
    if(System.currentTimeMillis()<state.circuitUntil.get()) {
      return Mono.error(new IllegalStateException("Legacy token endpoint unavailable"));
    }
    if(!state.rateAllowed()) {
      return Mono.error(new IllegalStateException("Legacy token acquisition rate limited"));
    }
    WebClient client=clients.computeIfAbsent(
        new ClientKey(origin.toString(),profile.connectTimeoutMs(),profile.maxSize()),this::client);
    WebClient.RequestBodySpec request=client.post().uri(profile.endpointPath())
        .accept(MediaType.APPLICATION_JSON);
    Mono<byte[]> response=switch(profile.requestFormat()) {
      case "JSON" -> request.contentType(MediaType.APPLICATION_JSON)
          .bodyValue(jsonCredentials(secret,profile)).exchangeToMono(this::response);
      case "HTTP_BASIC" -> {
        String username=first(secret.clientId(),secret.username());
        String password=first(secret.clientSecret(),secret.password());
        yield request.contentType(MediaType.APPLICATION_FORM_URLENCODED)
            .headers(headers->headers.setBasicAuth(required(username),required(password)))
            .body(BodyInserters.fromFormData(commonForm(profile))).exchangeToMono(this::response);
      }
      case "OAUTH_CLIENT_CREDENTIALS" -> {
        LinkedMultiValueMap<String,String> form=commonForm(profile);
        form.add("grant_type","client_credentials");
        form.add("client_id",required(secret.clientId()));
        form.add("client_secret",required(secret.clientSecret()));
        yield request.contentType(MediaType.APPLICATION_FORM_URLENCODED)
            .body(BodyInserters.fromFormData(form)).exchangeToMono(this::response);
      }
      case "FORM_URLENCODED" -> {
        LinkedMultiValueMap<String,String> form=commonForm(profile);
        form.add("username",required(secret.username()));
        form.add("password",required(secret.password()));
        yield request.contentType(MediaType.APPLICATION_FORM_URLENCODED)
            .body(BodyInserters.fromFormData(form)).exchangeToMono(this::response);
      }
      default -> Mono.error(new IllegalStateException("Unsupported Legacy request adapter"));
    };
    return response.timeout(Duration.ofMillis(profile.responseTimeoutMs()))
        .flatMap(bytes->bytes.length>profile.maxSize()
            ? Mono.error(new IllegalStateException("Token response too large")):Mono.just(bytes))
        .doOnSuccess(ignored->state.failures.set(0))
        .doOnError(ignored->state.failure());
  }

  private Mono<byte[]> response(ClientResponse response) {
    if(response.statusCode().is2xxSuccessful()) return response.bodyToMono(byte[].class);
    return response.releaseBody().then(Mono.error(
        new IllegalStateException("Legacy token endpoint rejected the request")));
  }

  private WebClient client(ClientKey key) {
    HttpClient http=HttpClient.create(pool).followRedirect(false)
        .option(ChannelOption.CONNECT_TIMEOUT_MILLIS,key.connectTimeoutMs());
    return WebClient.builder().clientConnector(new ReactorClientHttpConnector(http))
        .baseUrl(key.origin()).codecs(codecs->codecs.defaultCodecs()
            .maxInMemorySize(Math.toIntExact(key.maxResponseSize())))
        .build();
  }

  private static Map<String,String> jsonCredentials(SecretResolver.ResolvedSecret secret,
      OutboundAuthProfile profile) {
    Map<String,String> body=new LinkedHashMap<>();
    put(body,"username",secret.username());put(body,"password",secret.password());
    put(body,"client_id",secret.clientId());put(body,"client_secret",secret.clientSecret());
    put(body,"scope",profile.scope());put(body,"audience",profile.audience());
    return body;
  }

  private static LinkedMultiValueMap<String,String> commonForm(OutboundAuthProfile profile) {
    LinkedMultiValueMap<String,String> form=new LinkedMultiValueMap<>();
    if(profile.scope()!=null) form.add("scope",profile.scope());
    if(profile.audience()!=null) form.add("audience",profile.audience());
    return form;
  }

  private static void put(Map<String,String> target,String key,String value) {
    if(value!=null) target.put(key,value);
  }
  private static String first(String preferred,String fallback) {
    return preferred==null||preferred.isBlank()?fallback:preferred;
  }
  private static String required(String value) {
    if(value==null||value.isBlank()) throw new IllegalStateException("Legacy credential incomplete");
    return value;
  }

  private record ClientKey(String origin,int connectTimeoutMs,long maxResponseSize) {}
  private static final class EndpointState {
    private final AtomicInteger failures=new AtomicInteger();
    private final AtomicLong circuitUntil=new AtomicLong();
    private final AtomicLong rateWindow=new AtomicLong();
    private final AtomicInteger rateCount=new AtomicInteger();
    private boolean rateAllowed() {
      long second=System.currentTimeMillis()/1000;
      long previous=rateWindow.get();
      if(previous!=second&&rateWindow.compareAndSet(previous,second)) rateCount.set(0);
      return rateCount.incrementAndGet()<=10;
    }
    private void failure() {
      if(failures.incrementAndGet()>=5) circuitUntil.set(System.currentTimeMillis()+30_000);
    }
  }
}

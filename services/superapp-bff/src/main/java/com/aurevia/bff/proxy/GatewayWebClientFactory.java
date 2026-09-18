package com.aurevia.bff.proxy;

import io.netty.channel.ChannelOption;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.IntFunction;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

/** Creates pooled Gateway clients that honor each registered target's connect timeout. */
public final class GatewayWebClientFactory {
  private static final int MAX_RESPONSE_BYTES=100*1024*1024;
  private final IntFunction<WebClient> clients;

  GatewayWebClientFactory(HttpClient baseClient) {
    var cache=new ConcurrentHashMap<Integer,WebClient>();
    clients=timeout->cache.computeIfAbsent(timeout,value->WebClient.builder()
        .codecs(codecs->codecs.defaultCodecs().maxInMemorySize(MAX_RESPONSE_BYTES))
        .clientConnector(new ReactorClientHttpConnector(
            baseClient.option(ChannelOption.CONNECT_TIMEOUT_MILLIS,value))).build());
  }

  private GatewayWebClientFactory(IntFunction<WebClient> clients) {
    this.clients=clients;
  }

  public WebClient client(int connectTimeoutMs) {
    if(connectTimeoutMs<100||connectTimeoutMs>30000) {
      throw new IllegalArgumentException("Invalid Gateway connect timeout");
    }
    return clients.apply(connectTimeoutMs);
  }

  public static GatewayWebClientFactory fixed(WebClient client) {
    return new GatewayWebClientFactory(ignored->client);
  }
}

package com.aurevia.bff.api;

import io.netty.channel.ChannelOption;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;
import reactor.netty.resources.ConnectionProvider;

@Configuration
class MfeArtifactWebClientConfiguration {
  @Bean
  @Qualifier("mfeArtifactWebClient")
  WebClient mfeArtifactWebClient(
      @Value("${aurevia.mfe-proxy.connect-timeout-ms:3000}") int connectTimeoutMs,
      @Value("${aurevia.mfe-proxy.response-timeout-ms:10000}") int responseTimeoutMs,
      @Value("${aurevia.mfe-proxy.max-response-bytes:8388608}") int maxResponseBytes) {
    if(connectTimeoutMs<100||connectTimeoutMs>30_000||responseTimeoutMs<100
        ||responseTimeoutMs>60_000||maxResponseBytes<1024||maxResponseBytes>64*1024*1024) {
      throw new IllegalArgumentException("MFE proxy timeout/size limits are invalid");
    }
    ConnectionProvider pool=ConnectionProvider.builder("mfe-artifacts")
        .maxConnections(100).pendingAcquireMaxCount(200)
        .pendingAcquireTimeout(Duration.ofSeconds(2)).build();
    HttpClient client=HttpClient.create(pool).followRedirect(false)
        .option(ChannelOption.CONNECT_TIMEOUT_MILLIS,connectTimeoutMs)
        .responseTimeout(Duration.ofMillis(responseTimeoutMs));
    return WebClient.builder().clientConnector(new ReactorClientHttpConnector(client))
        .codecs(configurer->configurer.defaultCodecs().maxInMemorySize(maxResponseBytes)).build();
  }
}

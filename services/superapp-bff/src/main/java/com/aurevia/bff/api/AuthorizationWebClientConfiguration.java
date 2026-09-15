package com.aurevia.bff.api;

import com.aurevia.bff.proxy.RouteNormalizer;
import com.aurevia.bff.observability.CorrelationIds;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.channel.ChannelOption;
import java.io.FileInputStream;
import java.time.Duration;
import java.security.KeyStore;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.TrustManagerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.ClientRequest;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;
import reactor.netty.resources.ConnectionProvider;

@Configuration
class AuthorizationWebClientConfiguration {
  @Bean
  @Qualifier("authorizationWebClient")
  WebClient authorizationWebClient(@Value("${aurevia.authorization-service.base-url}") String url,
      @Value("${aurevia.authorization-service.authentication-mode:basic}") String mode,
      @Value("${aurevia.authorization-service.username:bff}") String username,
      @Value("${aurevia.authorization-service.password:change-me}") String password,
      @Value("${aurevia.authorization-service.mtls-key-store:}") String keyStore,
      @Value("${aurevia.authorization-service.mtls-key-store-password:}") String keyPassword,
      @Value("${aurevia.authorization-service.mtls-trust-store:}") String trustStore,
      @Value("${aurevia.authorization-service.mtls-trust-store-password:}") String trustPassword)
      throws Exception {
    RouteNormalizer.allowlistedTarget(url);
    WebClient.Builder builder = WebClient.builder().baseUrl(url).filter((request,next) -> Mono.deferContextual(context -> {
      String correlation=context.getOrDefault(CorrelationIds.CONTEXT_KEY,"");
      if(correlation.isBlank())return next.exchange(request);
      return next.exchange(ClientRequest.from(request).header(CorrelationIds.HEADER,correlation).build());
    }));
    ConnectionProvider pool=ConnectionProvider.builder("authorization-service")
        .maxConnections(100).pendingAcquireMaxCount(250)
        .pendingAcquireTimeout(Duration.ofSeconds(2)).build();
    HttpClient client=HttpClient.create(pool).followRedirect(false)
        .option(ChannelOption.CONNECT_TIMEOUT_MILLIS,3000)
        .responseTimeout(Duration.ofSeconds(15));
    if ("basic".equals(mode)) return builder
        .clientConnector(new ReactorClientHttpConnector(client))
        .defaultHeaders(h -> h.setBasicAuth(username,password)).build();
    if (!"mtls".equals(mode) || keyStore.isBlank() || trustStore.isBlank()) {
      throw new IllegalStateException("Authorization Service workload mTLS is not configured");
    }
    KeyManagerFactory keys = keyManagers(keyStore,keyPassword);
    TrustManagerFactory trust = trustManagers(trustStore,trustPassword);
    var ssl = SslContextBuilder.forClient().keyManager(keys).trustManager(trust).build();
    return builder.clientConnector(new ReactorClientHttpConnector(
        client.secure(spec -> spec.sslContext(ssl)))).build();
  }

  private static KeyManagerFactory keyManagers(String path,String password) throws Exception {
    KeyStore store=load(path,password);KeyManagerFactory result=KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());result.init(store,password.toCharArray());return result;
  }
  private static TrustManagerFactory trustManagers(String path,String password) throws Exception {
    KeyStore store=load(path,password);TrustManagerFactory result=TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());result.init(store);return result;
  }
  private static KeyStore load(String path,String password) throws Exception {
    KeyStore store=KeyStore.getInstance("PKCS12");try(var input=new FileInputStream(path)){store.load(input,password.toCharArray());}return store;
  }
}

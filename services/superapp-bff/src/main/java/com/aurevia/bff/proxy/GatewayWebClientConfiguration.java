package com.aurevia.bff.proxy;

import java.io.FileInputStream;
import java.security.KeyStore;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.TrustManagerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;
import reactor.netty.tcp.SslProvider;

@Configuration
class GatewayWebClientConfiguration {
  @Bean("operationGatewayClient")
  WebClient operationGatewayClient(@Value("${aurevia.gateway.base-url}") String baseUrl,
      @Value("${aurevia.gateway.mtls-key-store:}") String keyStorePath,
      @Value("${aurevia.gateway.mtls-key-store-password:}") String password,
      @Value("${aurevia.gateway.mtls-trust-store:}") String trustStorePath,
      @Value("${aurevia.gateway.mtls-trust-store-password:}") String trustPassword,
      @Value("${aurevia.gateway.require-mtls:false}") boolean requireMtls) throws Exception {
    RouteNormalizer.allowlistedTarget(baseUrl);
    if(requireMtls && (keyStorePath.isBlank() || trustStorePath.isBlank() || !baseUrl.startsWith("https://")))
      throw new IllegalStateException("Production Gateway requires HTTPS, client identity, and an explicit trust store");
    var provider=reactor.netty.resources.ConnectionProvider.builder("operation-gateway")
        .maxConnections(200).pendingAcquireMaxCount(500).pendingAcquireTimeout(java.time.Duration.ofSeconds(2)).build();
    HttpClient client = HttpClient.create(provider).followRedirect(false)
        .option(io.netty.channel.ChannelOption.CONNECT_TIMEOUT_MILLIS,3000)
        .responseTimeout(java.time.Duration.ofSeconds(30));
    if (!keyStorePath.isBlank() || !trustStorePath.isBlank()) {
      if(keyStorePath.isBlank() || trustStorePath.isBlank())throw new IllegalStateException("Both Gateway key store and trust store are required");
      KeyStore keyStore = load(keyStorePath,password);
      KeyManagerFactory managers = KeyManagerFactory.getInstance(
          KeyManagerFactory.getDefaultAlgorithm());
      managers.init(keyStore, password.toCharArray());
      KeyStore trustStore=load(trustStorePath,trustPassword);
      TrustManagerFactory trust=TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
      trust.init(trustStore);
      var sslContext = io.netty.handler.ssl.SslContextBuilder.forClient()
          .keyManager(managers).trustManager(trust).build();
      client = client.secure(spec -> spec.sslContext(sslContext));
    }
    return WebClient.builder().baseUrl(baseUrl)
        .codecs(codecs -> codecs.defaultCodecs().maxInMemorySize(10 * 1024 * 1024))
        .clientConnector(new ReactorClientHttpConnector(client)).build();
  }
  private static KeyStore load(String path,String password)throws Exception{KeyStore store=KeyStore.getInstance("PKCS12");try(var stream=new FileInputStream(path)){store.load(stream,password.toCharArray());}return store;}
}

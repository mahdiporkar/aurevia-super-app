package com.aurevia.bff.proxy;

import java.io.FileInputStream;
import java.security.KeyStore;
import javax.net.ssl.KeyManagerFactory;
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
      @Value("${aurevia.gateway.mtls-key-store-password:}") String password) throws Exception {
    RouteNormalizer.allowlistedTarget(baseUrl);
    HttpClient client = HttpClient.create().followRedirect(false);
    if (!keyStorePath.isBlank()) {
      KeyStore keyStore = KeyStore.getInstance("PKCS12");
      try (var stream = new FileInputStream(keyStorePath)) {
        keyStore.load(stream, password.toCharArray());
      }
      KeyManagerFactory managers = KeyManagerFactory.getInstance(
          KeyManagerFactory.getDefaultAlgorithm());
      managers.init(keyStore, password.toCharArray());
      var sslContext = io.netty.handler.ssl.SslContextBuilder.forClient()
          .keyManager(managers).build();
      client = client.secure(spec -> spec.sslContext(sslContext));
    }
    return WebClient.builder().baseUrl(baseUrl)
        .codecs(codecs -> codecs.defaultCodecs().maxInMemorySize(10 * 1024 * 1024))
        .clientConnector(new ReactorClientHttpConnector(client)).build();
  }
}

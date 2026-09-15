package com.aurevia.bff.api;

import io.netty.channel.ChannelOption;
import io.netty.handler.ssl.SslContextBuilder;
import java.io.File;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

/** Server-only TLS identity for external Superset; normal JVM TLS remains the default. */
@Configuration
class SupersetWebClientConfiguration {
  @Bean("supersetWebClient")
  WebClient supersetWebClient(
      @Value("${aurevia.superset.connect-timeout-ms:3000}") int connectTimeoutMs,
      @Value("${aurevia.superset.response-timeout-ms:10000}") long responseTimeoutMs,
      @Value("${aurevia.superset.tls.ca-cert-file:}") String caCert,
      @Value("${aurevia.superset.tls.client-cert-file:}") String clientCert,
      @Value("${aurevia.superset.tls.client-key-file:}") String clientKey,
      @Value("${aurevia.superset.tls.require-mtls:false}") boolean requireMtls,
      @Value("${aurevia.superset.allow-http:false}") boolean allowHttp) throws Exception {
    if (clientCert.isBlank() != clientKey.isBlank())
      throw new IllegalStateException("Superset client certificate and private key must be configured together");
    if (requireMtls && (caCert.isBlank() || clientCert.isBlank() || allowHttp))
      throw new IllegalStateException("Superset mTLS requires an explicit CA, client identity, and HTTP disabled");
    HttpClient client = HttpClient.create().followRedirect(false)
        .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, connectTimeoutMs)
        .responseTimeout(Duration.ofMillis(responseTimeoutMs));
    if (!caCert.isBlank() || !clientCert.isBlank()) {
      var builder = SslContextBuilder.forClient();
      if (!caCert.isBlank()) builder.trustManager(new File(caCert));
      if (!clientCert.isBlank()) builder.keyManager(new File(clientCert), new File(clientKey));
      var context = builder.build();
      client = client.secure(spec -> spec.sslContext(context).handlerConfigurator(handler -> {
        var parameters = handler.engine().getSSLParameters();
        parameters.setEndpointIdentificationAlgorithm("HTTPS");
        handler.engine().setSSLParameters(parameters);
      }));
    }
    return WebClient.builder().clientConnector(new ReactorClientHttpConnector(client)).build();
  }
}

package com.aurevia.authz.openfga;

import dev.openfga.sdk.api.client.OpenFgaClient;
import dev.openfga.sdk.api.configuration.ClientConfiguration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class OpenFgaConfiguration {
  @Bean OpenFgaClient openFgaClient(@Value("${aurevia.openfga.base-url}") String apiUrl,
      @Value("${aurevia.openfga.store-id}") String storeId,
      @Value("${aurevia.openfga.model-id:}") String modelId) throws Exception {
    var configuration = new ClientConfiguration().apiUrl(apiUrl).storeId(storeId).maxRetries(3);
    if (!modelId.isBlank()) configuration.authorizationModelId(modelId);
    return new OpenFgaClient(configuration);
  }
}

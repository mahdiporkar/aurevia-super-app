package com.aurevia.bff.api;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
@Component class AuthorizationServiceClient {
 private final WebClient client;
 AuthorizationServiceClient(WebClient.Builder builder,@Value("${aurevia.authorization-service.base-url}") String url,@Value("${aurevia.authorization-service.username:bff}") String user,@Value("${aurevia.authorization-service.password:change-me}") String password){client=builder.baseUrl(url).defaultHeaders(h->h.setBasicAuth(user,password)).build();}
 Mono<Map> manifest(String subject){return client.get().uri("/internal/v1/subjects/{id}/manifest",subject).accept(MediaType.APPLICATION_JSON).retrieve().bodyToMono(Map.class);}
}

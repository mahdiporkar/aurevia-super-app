package com.aurevia.bff.api;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
@Component public class AuthorizationServiceClient {
 private final WebClient client;
 public AuthorizationServiceClient(WebClient.Builder builder,@Value("${aurevia.authorization-service.base-url}") String url,@Value("${aurevia.authorization-service.username:bff}") String user,@Value("${aurevia.authorization-service.password:change-me}") String password){client=builder.baseUrl(url).defaultHeaders(h->h.setBasicAuth(user,password)).build();}
 Mono<Map> manifest(String subject){return client.get().uri("/internal/v1/subjects/{id}/manifest",subject).accept(MediaType.APPLICATION_JSON).retrieve().bodyToMono(Map.class);}
 public Mono<Void> syncLogin(Map<String,Object> identity){return client.post().uri("/internal/v1/identity/login-sync").contentType(MediaType.APPLICATION_JSON).bodyValue(identity).retrieve().bodyToMono(Void.class);}
 public Mono<Map> resolveRoute(String path,String method){return client.get().uri(builder->builder.path("/internal/v1/routes/resolve").queryParam("path",path).queryParam("method",method).build()).retrieve().bodyToMono(Map.class);}
 public Mono<Map> check(Map<String,Object> request){return client.post().uri("/internal/v1/authorize/check").contentType(MediaType.APPLICATION_JSON).bodyValue(request).retrieve().bodyToMono(Map.class);}
}

package com.aurevia.bff.api;
import java.util.Map;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
@Component public class AuthorizationServiceClient {
 private final WebClient client;
 public AuthorizationServiceClient(@Qualifier("authorizationWebClient") WebClient client){this.client=client;}
 Mono<Map> manifest(String subject){return client.get().uri("/internal/v1/subjects/{id}/manifest",subject).accept(MediaType.APPLICATION_JSON).retrieve().bodyToMono(Map.class);}
 public Mono<Void> syncLogin(Map<String,Object> identity){return client.post().uri("/internal/v1/identity/login-sync").contentType(MediaType.APPLICATION_JSON).bodyValue(identity).retrieve().bodyToMono(Void.class);}
 public Mono<Map> resolveRoute(String path,String method){return client.get().uri(builder->builder.path("/internal/v1/routes/resolve").queryParam("path",path).queryParam("method",method).build()).retrieve().bodyToMono(Map.class);}
 public Mono<Map> outboundAuthProfile(String id){return client.get().uri("/internal/v1/outbound-auth-profiles/{id}",id).retrieve().bodyToMono(Map.class);}
 public Mono<Map> check(Map<String,Object> request){return client.post().uri("/internal/v1/authorize/check").contentType(MediaType.APPLICATION_JSON).bodyValue(request).retrieve().bodyToMono(Map.class);}
 public Mono<Map> supersetAccess(String subject,String path,String method,String query){return client.get().uri(builder->builder.path("/internal/v1/registry/subjects/{subject}/superset-access").queryParam("path",path).queryParam("method",method).queryParam("query",query==null?"":query).build(subject)).retrieve().bodyToMono(Map.class);}
 public Mono<Void> ingestApiLog(Map<String,Object> entry){return client.post().uri("/internal/v1/logging/api").contentType(MediaType.APPLICATION_JSON).bodyValue(entry).retrieve().bodyToMono(Void.class);}
}

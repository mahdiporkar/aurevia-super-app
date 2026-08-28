package com.aurevia.bff.api;

import java.security.Principal;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/v1/reports")
public class ReportsController {
  private final WebClient authorizationClient;

  public ReportsController(
      WebClient.Builder builder,
      @Value("${aurevia.authorization-service.base-url}") String baseUrl,
      @Value("${aurevia.authorization-service.username}") String username,
      @Value("${aurevia.authorization-service.password}") String password) {
    this.authorizationClient = builder
        .baseUrl(baseUrl)
        .defaultHeaders(headers -> headers.setBasicAuth(username, password))
        .build();
  }

  @GetMapping
  Mono<List<Map>> reports(Principal principal) {
    return authorizationClient.get()
        .uri("/internal/v1/registry/subjects/{subject}/superset-assets", principal.getName())
        .retrieve()
        .bodyToFlux(Map.class)
        .collectList();
  }
}

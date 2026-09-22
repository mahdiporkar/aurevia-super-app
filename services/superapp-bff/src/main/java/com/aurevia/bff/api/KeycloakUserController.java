package com.aurevia.bff.api;

import com.aurevia.bff.identity.KeycloakAdminException;
import com.aurevia.bff.identity.KeycloakAdminUserService;
import com.aurevia.bff.security.SessionIdentity;
import jakarta.validation.Valid;
import java.security.Principal;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import static com.aurevia.bff.identity.KeycloakUserModels.*;

@RestController
public class KeycloakUserController {
  private final WebClient authorization;
  private final KeycloakAdminUserService users;

  public KeycloakUserController(@Qualifier("authorizationWebClient") WebClient authorization,
      KeycloakAdminUserService users) {
    this.authorization=authorization;
    this.users=users;
  }

  @PostMapping("/api/v1/admin/keycloak-users")
  public Mono<ResponseEntity<CreatedUser>> create(@Valid @RequestBody CreateUser request,
      Principal principal, ServerWebExchange exchange) {
    SessionIdentity identity=SessionIdentity.from(principal);
    exchange.getAttributes().put("proxy.businessAction","create_keycloak_user");
    exchange.getAttributes().put("proxy.resourceId","application:aurevia");
    Map<String,Object> check=Map.of("subjectId",identity.subject(),"issuer",identity.issuer(),
        "resource","application:aurevia","action","admin","context",Map.of(),
        "correlationId",UUID.randomUUID().toString());
    return authorization.post().uri("/internal/v1/authorize/check")
        .contentType(MediaType.APPLICATION_JSON).bodyValue(check).retrieve().bodyToMono(Map.class)
        .timeout(Duration.ofSeconds(10))
        .onErrorMap(error -> new KeycloakAdminException(HttpStatus.SERVICE_UNAVAILABLE,
            "AUTHORIZATION_UNAVAILABLE","Administrative permission could not be verified."))
        .filter(decision -> "ALLOW".equals(decision.get("result")))
        .switchIfEmpty(Mono.defer(() -> {
          exchange.getAttributes().put("proxy.authorizationResult","DENY");
          return Mono.error(new KeycloakAdminException(HttpStatus.FORBIDDEN,"ACCESS_DENIED",
              "Creating users requires platform administration permission."));
        }))
        .doOnNext(decision -> exchange.getAttributes().put("proxy.authorizationResult","ALLOW"))
        .then(Mono.defer(() -> users.create(request)))
        .map(user -> ResponseEntity.status(HttpStatus.CREATED).body(user));
  }

  // Errors are rendered by BffExceptionHandler as the canonical ApiError body.
}

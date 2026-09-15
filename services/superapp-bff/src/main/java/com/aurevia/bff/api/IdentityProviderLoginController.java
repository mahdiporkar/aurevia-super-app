package com.aurevia.bff.api;

import java.net.URI;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

/** Same-origin provider discovery and safe login routing; no caller-controlled redirect URI. */
@RestController
public class IdentityProviderLoginController {
  private final AuthorizationServiceClient authorization;
  public IdentityProviderLoginController(AuthorizationServiceClient authorization){this.authorization=authorization;}
  @GetMapping("/auth/providers")
  public Mono<List<AuthorizationServiceClient.IdentityProviderSummary>> providers(
      @RequestParam(required=false) String tenant,@RequestParam(required=false) String domain){
    return authorization.identityProviders(tenant,domain);}
  @GetMapping("/auth/login")
  public Mono<ResponseEntity<Void>> login(@RequestParam(required=false) String provider,
      @RequestParam(required=false) String tenant,@RequestParam(required=false) String domain){
    return authorization.routeIdentityProvider(provider,tenant,domain).map(selected->ResponseEntity
        .status(HttpStatus.FOUND).header(HttpHeaders.LOCATION,
            URI.create("/oauth2/authorization/"+selected.code()).toASCIIString()).<Void>build());
  }
}

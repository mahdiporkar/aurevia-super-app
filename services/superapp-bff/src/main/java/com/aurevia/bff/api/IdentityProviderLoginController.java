package com.aurevia.bff.api;

import java.net.URI;
import java.util.List;
import java.util.ArrayList;
import com.aurevia.bff.security.PrimaryOidcConfiguration;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.web.server.ResponseStatusException;
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
  private final AuthorizationServiceClient.IdentityProviderSummary primary;
  public IdentityProviderLoginController(AuthorizationServiceClient authorization,
      @Qualifier("primaryClientRegistration") ClientRegistration registration){
    this.authorization=authorization;
    this.primary=new AuthorizationServiceClient.IdentityProviderSummary(
        PrimaryOidcConfiguration.REGISTRATION_ID,registration.getClientName(),"OIDC",
        registration.getProviderDetails().getIssuerUri(),null,List.of(),"CONFIGURED");
  }
  @GetMapping("/login")
  public ResponseEntity<Void> loginLanding() {
    return ResponseEntity.status(HttpStatus.FOUND).location(URI.create("/")).build();
  }
  @GetMapping("/auth/providers")
  public Mono<List<AuthorizationServiceClient.IdentityProviderSummary>> providers(
      @RequestParam(required=false) String tenant,@RequestParam(required=false) String domain,
      @RequestParam(defaultValue="false") boolean includeAdditional){
    if(!includeAdditional)return Mono.just(List.of(primary));
    return authorization.identityProviders(tenant,domain).map(additional->{
      List<AuthorizationServiceClient.IdentityProviderSummary> result=new ArrayList<>();
      result.add(primary);
      additional.stream().filter(value->!primary.code().equals(value.code())
          &&!primary.issuerUrl().equals(value.issuerUrl())).forEach(result::add);
      return List.copyOf(result);
    });
  }
  @GetMapping("/auth/login")
  public Mono<ResponseEntity<Void>> login(@RequestParam(required=false) String provider,
      @RequestParam(required=false) String tenant,@RequestParam(required=false) String domain){
    if(provider==null||provider.isBlank()||primary.code().equals(provider))return Mono.just(redirect(primary.code()));
    if(!provider.matches("[a-z][a-z0-9-]{2,79}"))return Mono.error(new ResponseStatusException(
        HttpStatus.BAD_REQUEST,"Invalid additional identity provider code"));
    return authorization.routeIdentityProvider(provider,tenant,domain).map(selected->{
      if(!provider.equals(selected.code())||primary.issuerUrl().equals(selected.issuerUrl()))
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Additional identity provider selection is invalid");
      return redirect(selected.code());
    });
  }
  private static ResponseEntity<Void> redirect(String code){
    return ResponseEntity.status(HttpStatus.FOUND).header(HttpHeaders.LOCATION,
        URI.create("/oauth2/authorization/"+code).toASCIIString()).build();
  }
}

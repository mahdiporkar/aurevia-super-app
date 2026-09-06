package com.aurevia.bff.api;

import java.security.Principal;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.*;
import reactor.core.publisher.Mono;
import com.aurevia.bff.security.SessionIdentity;

@RestController
class MeController {
  private final AuthorizationServiceClient authorization;
  MeController(AuthorizationServiceClient authorization){this.authorization=authorization;}
  @GetMapping("/api/v1/me") Mono<Map<String,Object>> me(Principal principal) {
    SessionIdentity identity = SessionIdentity.from(principal);
    return Mono.just(Map.of("issuer", identity.issuer(), "subject", identity.subject(),
        "username", identity.username(), "groups", List.of()));
  }
  @GetMapping("/api/v1/me/manifest") Mono<ResponseEntity<Map<String,Object>>> manifest(Principal principal) {
    SessionIdentity identity = SessionIdentity.from(principal);
    return authorization.manifest(identity.issuer(), identity.subject())
        .map(body -> ResponseEntity.ok()
            .cacheControl(CacheControl.noCache().cachePrivate())
            .eTag("\"" + body.get("version") + "\"")
            .body(body));
  }

  @GetMapping("/api/ui/catalog") Mono<ResponseEntity<Map<String,Object>>> uiCatalog(
      Principal principal) {
    SessionIdentity identity=SessionIdentity.from(principal);
    return authorization.manifest(identity.issuer(),identity.subject()).map(body->{
      Object value=body.get("uiCatalog");
      if(!(value instanceof Map<?,?> raw))throw new IllegalStateException(
          "authorization response does not contain uiCatalog");
      @SuppressWarnings("unchecked") Map<String,Object> catalog=(Map<String,Object>)raw;
      String version=String.valueOf(catalog.getOrDefault("catalogVersion",body.get("version")));
      return ResponseEntity.ok().cacheControl(CacheControl.noCache().cachePrivate())
          .eTag("\""+version+"\"").body(catalog);
    });
  }
}

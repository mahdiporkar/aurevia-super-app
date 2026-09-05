package com.aurevia.bff.api;

import java.security.Principal;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.*;
import reactor.core.publisher.Mono;
import com.aurevia.bff.security.SessionIdentity;

@RestController
@RequestMapping("/api/v1")
class MeController {
  private final AuthorizationServiceClient authorization;
  MeController(AuthorizationServiceClient authorization){this.authorization=authorization;}
  @GetMapping("/me") Mono<Map<String,Object>> me(Principal principal) {
    SessionIdentity identity = SessionIdentity.from(principal);
    return Mono.just(Map.of("issuer", identity.issuer(), "subject", identity.subject(),
        "username", identity.username(), "groups", List.of()));
  }
  @GetMapping("/me/manifest") Mono<ResponseEntity<Map<String,Object>>> manifest(Principal principal) {
    SessionIdentity identity = SessionIdentity.from(principal);
    return authorization.manifest(identity.issuer(), identity.subject())
        .map(body -> ResponseEntity.ok()
            .cacheControl(CacheControl.noCache().cachePrivate())
            .eTag("\"" + body.get("version") + "\"")
            .body(body));
  }
}

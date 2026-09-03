package com.aurevia.bff.api;

import java.security.Principal;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.*;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/v1")
class MeController {
  private final AuthorizationServiceClient authorization;
  MeController(AuthorizationServiceClient authorization){this.authorization=authorization;}
  @GetMapping("/me") Mono<Map<String,Object>> me(Principal principal) {
    return Mono.just(Map.of("subject", principal.getName(), "groups", List.of()));
  }
  @GetMapping("/me/manifest") Mono<ResponseEntity<Map>> manifest(Principal principal) {return authorization.manifest(principal.getName()).map(body->ResponseEntity.ok().cacheControl(CacheControl.noCache().cachePrivate()).eTag("\""+body.get("version")+"\"").body(body));}
}

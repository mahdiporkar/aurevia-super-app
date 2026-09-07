package com.aurevia.bff.api;

import com.aurevia.bff.security.SessionIdentity;
import io.swagger.v3.oas.annotations.Operation;
import java.security.Principal;
import java.util.List;
import java.util.Map;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;
import static org.springframework.http.HttpStatus.NOT_FOUND;

/** Session-authorized same-origin facade for MFE metadata and executable artifacts. */
@RestController
public final class MicroFrontendArtifactController {
  private final AuthorizationServiceClient authorization;
  private final WebClient artifacts;
  private final MicroFrontendArtifactTargetResolver targets;

  public MicroFrontendArtifactController(AuthorizationServiceClient authorization,
      WebClient.Builder builder,MicroFrontendArtifactTargetResolver targets) {
    this.authorization=authorization;
    this.targets=targets;
    this.artifacts=builder.codecs(configurer->configurer.defaultCodecs()
        .maxInMemorySize(8*1024*1024)).build();
  }

  @Operation(summary="دریافت مانیفست امن میکروفرانت مجاز")
  @GetMapping("/api/mfe/{moduleKey}/manifest.json")
  Mono<ResponseEntity<Map<String,Object>>> manifest(@PathVariable String moduleKey,
      Principal principal) {
    return allowedModule(moduleKey,principal).map(module->ResponseEntity.ok()
        .cacheControl(CacheControl.noCache().cachePrivate()).body(module));
  }

  @Operation(summary="دریافت فایل اجرایی امن میکروفرانت مجاز")
  @GetMapping("/api/mfe/{moduleKey}/{*assetPath}")
  Mono<ResponseEntity<byte[]>> artifact(@PathVariable String moduleKey,
      @PathVariable String assetPath,Principal principal) {
    return allowedModule(moduleKey,principal).flatMap(module->{
      @SuppressWarnings("unchecked") Map<String,Object> remote=(Map<String,Object>)module.get("remote");
      var target=targets.resolveAsset(String.valueOf(remote.get("remoteEntryUrl")),assetPath);
      return artifacts.get().uri(target).exchangeToMono(response->response.bodyToMono(byte[].class)
          .defaultIfEmpty(new byte[0]).map(bytes->{
            MediaType contentType=response.headers().contentType()
                .orElse(MediaType.APPLICATION_OCTET_STREAM);
            return ResponseEntity.status(response.statusCode())
                .cacheControl(CacheControl.noCache().cachePrivate())
                .contentType(contentType).body(bytes);
          }));
    });
  }

  private Mono<Map<String,Object>> allowedModule(String moduleKey,Principal principal) {
    SessionIdentity identity=SessionIdentity.from(principal);
    return authorization.manifest(identity.issuer(),identity.subject()).flatMap(body->
        modules(body).stream().filter(module->moduleKey.equals(module.get("moduleKey"))).findFirst()
            .map(Mono::just).orElseGet(()->Mono.error(new ResponseStatusException(NOT_FOUND))));
  }

  @SuppressWarnings("unchecked")
  private static List<Map<String,Object>> modules(Map<String,Object> body) {
    Object catalog=body.get("uiCatalog");
    if(!(catalog instanceof Map<?,?> map))return List.of();
    Object modules=map.get("modules");
    return modules instanceof List<?> list?list.stream().filter(Map.class::isInstance)
        .map(value->(Map<String,Object>)value).toList():List.of();
  }
}

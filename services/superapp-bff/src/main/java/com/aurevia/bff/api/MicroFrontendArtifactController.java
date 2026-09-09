package com.aurevia.bff.api;

import com.aurevia.bff.security.SessionIdentity;
import io.swagger.v3.oas.annotations.Operation;
import java.security.Principal;
import java.util.Locale;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeoutException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.buffer.DataBufferLimitException;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import static org.springframework.http.HttpStatus.BAD_GATEWAY;
import static org.springframework.http.HttpStatus.GATEWAY_TIMEOUT;
import static org.springframework.http.HttpStatus.NOT_FOUND;

/** Session-authorized same-origin facade for MFE metadata and executable artifacts. */
@RestController
public final class MicroFrontendArtifactController {
  private final AuthorizationServiceClient authorization;
  private final WebClient artifacts;
  private final MicroFrontendArtifactTargetResolver targets;

  public MicroFrontendArtifactController(AuthorizationServiceClient authorization,
      @Qualifier("mfeArtifactWebClient") WebClient artifacts,
      MicroFrontendArtifactTargetResolver targets) {
    this.authorization=authorization;
    this.targets=targets;
    this.artifacts=artifacts;
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
      return Mono.fromCallable(()->targets.resolveAsset(
              String.valueOf(remote.get("remoteEntryUrl")),assetPath))
          .subscribeOn(Schedulers.boundedElastic())
          .onErrorMap(IllegalArgumentException.class,error->new ResponseStatusException(
              BAD_GATEWAY,"registered MFE target was rejected by network policy",error))
          .flatMap(target->artifacts.get().uri(target).exchangeToMono(response->{
            if(response.statusCode().is3xxRedirection())return response.releaseBody().then(
                Mono.error(new ResponseStatusException(BAD_GATEWAY,
                    "MFE artifact redirects are not permitted")));
            MediaType contentType=response.headers().contentType()
                .orElse(MediaType.APPLICATION_OCTET_STREAM);
            if(response.statusCode().is2xxSuccessful())validateContentType(assetPath,contentType);
            return response.bodyToMono(byte[].class).defaultIfEmpty(new byte[0]).map(bytes->
                ResponseEntity.status(response.statusCode())
                    .cacheControl(CacheControl.noCache().cachePrivate())
                    .contentType(contentType).body(bytes));
          }))
          .onErrorMap(DataBufferLimitException.class,error->new ResponseStatusException(
              BAD_GATEWAY,"MFE artifact exceeded the configured response-size limit",error))
          .onErrorMap(WebClientRequestException.class,error->new ResponseStatusException(
              isTimeout(error)?GATEWAY_TIMEOUT:BAD_GATEWAY,
              isTimeout(error)?"MFE artifact request timed out":"MFE artifact is unavailable",error));
    });
  }

  private static void validateContentType(String assetPath,MediaType contentType) {
    String path=assetPath.toLowerCase(Locale.ROOT);
    String value=contentType.toString().toLowerCase(Locale.ROOT);
    boolean valid=!path.endsWith(".js")||(value.contains("javascript")||value.contains("ecmascript"));
    valid=valid&&(!path.endsWith(".json")||value.contains("json"));
    valid=valid&&(!path.endsWith(".css")||value.startsWith("text/css"));
    if(!valid)throw new ResponseStatusException(BAD_GATEWAY,
        "MFE artifact response has an unexpected content type");
  }

  private static boolean isTimeout(Throwable failure) {
    for(Throwable cursor=failure;cursor!=null;cursor=cursor.getCause()) {
      if(cursor instanceof TimeoutException||cursor.getClass().getSimpleName().contains("Timeout"))
        return true;
    }
    return false;
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

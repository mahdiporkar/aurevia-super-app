package com.aurevia.bff.api;

import java.security.Principal;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.*;
import reactor.core.publisher.Mono;
import com.aurevia.bff.security.SessionIdentity;
import io.swagger.v3.oas.annotations.Operation;

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

  /** Canonical, single-fetch browser contract. Legacy /api/v1/me/manifest remains supported. */
  @Operation(summary="دریافت زمینه مؤثر و یکپارچه کاربر")
  @GetMapping("/api/me/context") Mono<ResponseEntity<Map<String,Object>>> context(
      Principal principal) {
    SessionIdentity identity=SessionIdentity.from(principal);
    return authorization.manifest(identity.issuer(),identity.subject()).map(body->{
      Map<String,Object> context=new LinkedHashMap<>(body);
      Map<String,Object> effectiveCatalog=proxyCatalog(body);
      @SuppressWarnings("unchecked")
      List<Map<String,Object>> modules=(List<Map<String,Object>>)effectiveCatalog
          .getOrDefault("modules",List.of());
      Map<String,Object> identityView=Map.of("issuer",identity.issuer(),
          "subject",identity.subject(),"username",identity.username());
      context.put("contractVersion","1.0");
      context.put("identity",identityView);
      context.put("tenant",Map.of("id","default"));
      context.put("organizations",List.of());
      context.put("allowedApplications",modules.stream()
          .map(module->String.valueOf(module.get("moduleKey"))).toList());
      context.put("allowedMicros",modules);
      context.put("dynamicRoutes",modules.stream().flatMap(module->list(module,"routes").stream()
          .map(route->{Map<String,Object> value=new LinkedHashMap<>(route);
            value.put("moduleKey",module.get("moduleKey"));return value;})).toList());
      context.put("navigation",modules.stream().flatMap(module->list(module,"navigation").stream())
          .toList());
      context.put("resources",body.getOrDefault("resourceTree",List.of()));
      context.put("actions",body.getOrDefault("permissions",Map.of()));
      // Only policy obligations required for rendering belong here; policy definitions stay server-side.
      context.put("policies",body.getOrDefault("presentation",Map.of()));
      context.put("uiCatalog",effectiveCatalog);
      return ResponseEntity.ok().cacheControl(CacheControl.noCache().cachePrivate())
          .eTag("\""+body.get("version")+"\"").body(context);
    });
  }

  @SuppressWarnings("unchecked")
  private static List<Map<String,Object>> list(Map<String,Object> source,String key) {
    Object value=source.get(key);return value instanceof List<?> values
        ?values.stream().filter(Map.class::isInstance).map(item->(Map<String,Object>)item).toList()
        :List.of();
  }

  @SuppressWarnings("unchecked")
  private static Map<String,Object> proxyCatalog(Map<String,Object> body) {
    Object value=body.get("uiCatalog");
    if(!(value instanceof Map<?,?> raw))throw new IllegalStateException(
        "authorization response does not contain uiCatalog");
    Map<String,Object> catalog=new LinkedHashMap<>((Map<String,Object>)raw);
    List<Map<String,Object>> modules=list(catalog,"modules").stream().map(module->{
      Map<String,Object> copy=new LinkedHashMap<>(module);
      Map<String,Object> remote=new LinkedHashMap<>((Map<String,Object>)module.get("remote"));
      String moduleKey=String.valueOf(module.get("moduleKey"));
      remote.put("remoteEntryUrl","/api/mfe/"+moduleKey+"/remoteEntry.js");
      copy.put("remote",remote);
      copy.put("manifestUrl","/api/mfe/"+moduleKey+"/manifest.json");
      return copy;
    }).toList();
    catalog.put("modules",modules);
    return catalog;
  }
}

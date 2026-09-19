package com.aurevia.bff.api;

import java.security.Principal;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import com.aurevia.bff.security.SessionIdentity;

@RestController
@RequestMapping("/api/v1/reports")
public class ReportsController {
  private final AuthorizationServiceClient authorization;

  public ReportsController(AuthorizationServiceClient authorization) {
    this.authorization = authorization;
  }

  @GetMapping
  Mono<List<Map>> reports(Principal principal,
      @RequestParam(value="instance",required=false) String requestedInstance) {
    SessionIdentity identity = SessionIdentity.from(principal);
    String publicInstance=requestedInstance==null||requestedInstance.isBlank()
        ?null:requestedInstance.trim();
    if(publicInstance!=null) {
      if(!publicInstance.matches("[a-z][a-z0-9-]{2,79}")) {
        return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST,
            "Invalid Superset instance"));
      }
      return reportsForMapping(identity,publicInstance);
    }
    // The normal catalog has no selected instance. Enumerate only integrations
    // visible to this subject, then retain the asset-level checks per operation.
    return authorization.supersetIntegrations(identity.issuer(),identity.subject())
        .flatMapMany(Flux::fromIterable)
        .map(integration->mappingCode(integration.get("key")))
        .distinct()
        .concatMap(instance->reportsForMapping(identity,instance))
        .flatMapIterable(assets->assets)
        // Several public mappings can expose the same operation catalog. Keep
        // its first (default-preferred) mapping without merging different assets.
        .distinct(asset->asset.getOrDefault("id",asset))
        .collectList();
  }

  private Mono<List<Map>> reportsForMapping(SessionIdentity identity,String publicInstance) {
    return authorization.resolveSupersetProxy(publicInstance)
        .flatMap(target->{
          String operationCode=mappingCode(target.get("operation_code"));
          String publicCode=mappingCode(target.get("public_code"));
          return authorization.supersetAssets(identity.issuer(),identity.subject(),operationCode)
              .map(assets->bindToMapping(assets,publicCode));
        });
  }

  private static String mappingCode(Object value) {
    if(!(value instanceof String code)||!code.matches("[a-z][a-z0-9-]{2,79}")) {
      throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
          "Invalid Superset mapping returned by authorization service");
    }
    return code;
  }

  static List<Map> bindToMapping(List<Map> assets,String publicInstance) {
    String prefix="/api/v1/superset-instances/"+publicInstance;
    return assets.stream().map(asset->{
      var result=new java.util.LinkedHashMap<>(asset);
      Object path=asset.get("url_path");
      if(path instanceof String value&&value.startsWith("/")&&!value.startsWith("//")) {
        result.put("url_path",prefix+value);
      }
      return (Map)result;
    }).toList();
  }
}

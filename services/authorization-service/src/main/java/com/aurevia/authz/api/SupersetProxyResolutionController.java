package com.aurevia.authz.api;

import com.aurevia.authz.superset.SupersetProxyResolutionService;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Internal data-plane lookup; never returns credentials. */
@RestController
@RequestMapping("/internal/v1/superset-proxy")
public final class SupersetProxyResolutionController {
  private final SupersetProxyResolutionService mappings;

  public SupersetProxyResolutionController(SupersetProxyResolutionService mappings) {
    this.mappings=mappings;
  }

  @GetMapping("/resolve")
  public Map<String,Object> resolve(@RequestParam("publicInstance") String publicInstance) {
    return mappings.resolve(publicInstance);
  }
}

package com.aurevia.authz.api;

import com.aurevia.authz.superset.SupersetProxyResolutionService;
import com.aurevia.authz.superset.SupersetInstanceService;
import com.aurevia.authz.api.dto.SupersetInstanceDtos.IntegrationView;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/** Internal data-plane lookup; never returns credentials. */
@RestController
@RequestMapping("/internal/v1/superset-proxy")
public final class SupersetProxyResolutionController {
  private final SupersetProxyResolutionService mappings;
  private final SupersetInstanceService instances;

  public SupersetProxyResolutionController(SupersetProxyResolutionService mappings,
      SupersetInstanceService instances) {
    this.mappings=mappings;this.instances=instances;
  }

  @GetMapping("/resolve")
  public Map<String,Object> resolve(
      @RequestParam(value="publicInstance",required=false) String publicInstance) {
    return mappings.resolve(publicInstance);
  }

  @GetMapping("/resolve-integration")
  public Map<String,Object> resolveIntegration(@RequestParam("instance") String instance) {
    return mappings.resolveIntegration(instance);
  }

  @GetMapping("/subjects/{subject}/integrations")
  public List<IntegrationView> integrations(
      @PathVariable String subject,@RequestParam String issuer) {
    return instances.integrationsForSubject(issuer,subject);
  }

  @PostMapping("/{code}/health")
  public void health(@PathVariable String code,@RequestParam String status) {
    instances.updateHealth(code,status);
  }
}

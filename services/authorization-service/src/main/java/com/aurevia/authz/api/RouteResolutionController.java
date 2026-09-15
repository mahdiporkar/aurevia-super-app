package com.aurevia.authz.api;

import com.aurevia.authz.routing.ResolvedRoute;
import com.aurevia.authz.routing.RouteResolutionService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/v1/routes")
public final class RouteResolutionController {
  private final RouteResolutionService routes;

  public RouteResolutionController(RouteResolutionService routes) { this.routes=routes; }

  @GetMapping("/resolve")
  public ResolvedRoute resolve(@RequestParam String path,@RequestParam String method) {
    return routes.resolve(path,method);
  }
}

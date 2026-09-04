package com.aurevia.authz.api;

import static com.aurevia.authz.api.dto.ProxyRouteDtos.*;

import com.aurevia.authz.routing.ProxyRouteAdministrationService;
import com.aurevia.authz.routing.ResolvedRoute;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Control-plane HTTP adapter. Credentials and tokens are never accepted by this API. */
@RestController
@RequestMapping("/internal/v1/registry")
public final class ProxyRouteAdminController {
  private final ProxyRouteAdministrationService routes;
  public ProxyRouteAdminController(ProxyRouteAdministrationService routes) { this.routes=routes; }

  @GetMapping("/service-targets")
  public List<Map<String,Object>> targets(@RequestParam(defaultValue="") String search) {
    return routes.targets(search);
  }
  @GetMapping("/service-targets/{id}")
  public Map<String,Object> target(@PathVariable UUID id) { return routes.target(id); }
  @PostMapping("/service-targets") @ResponseStatus(HttpStatus.CREATED)
  public Map<String,Object> createTarget(@Valid @RequestBody TargetRequest request,
      @RequestHeader("X-Actor") String actor) { return routes.createTarget(request,actor); }
  @PutMapping("/service-targets/{id}")
  public Map<String,Object> updateTarget(@PathVariable UUID id,@RequestParam long version,
      @Valid @RequestBody TargetRequest request,@RequestHeader("X-Actor") String actor) {
    return routes.updateTarget(id,version,request,actor);
  }
  @PatchMapping("/service-targets/{id}/status")
  public Map<String,Object> targetStatus(@PathVariable UUID id,@RequestParam long version,
      @Valid @RequestBody StatusRequest request,@RequestHeader("X-Actor") String actor) {
    return routes.updateTargetStatus(id,version,request,actor);
  }

  @GetMapping("/proxy-routes")
  public List<Map<String,Object>> routes(@RequestParam(defaultValue="") String search,
      @RequestParam(required=false) UUID panelId,@RequestParam(required=false) UUID targetId,
      @RequestParam(required=false) Boolean active) {
    return routes.routes(search,panelId,targetId,active);
  }
  @GetMapping("/proxy-routes/{id}")
  public Map<String,Object> route(@PathVariable UUID id) { return routes.route(id); }
  @PostMapping("/proxy-routes/validate")
  public ValidationResponse validate(@Valid @RequestBody RouteRequest request) {
    return routes.validate(request);
  }
  @PostMapping("/proxy-routes/preview")
  public PreviewResponse preview(@Valid @RequestBody PreviewRequest request) {
    return routes.preview(request);
  }
  @PostMapping("/proxy-routes/resolve-test")
  public ResolvedRoute resolveTest(@Valid @RequestBody MatchRequest request) {
    return routes.resolveTest(request);
  }
  @PostMapping("/proxy-routes") @ResponseStatus(HttpStatus.CREATED)
  public Map<String,Object> createRoute(@Valid @RequestBody RouteRequest request,
      @RequestHeader("X-Actor") String actor) { return routes.createRoute(request,actor); }
  @PutMapping("/proxy-routes/{id}")
  public Map<String,Object> updateRoute(@PathVariable UUID id,@RequestParam long version,
      @Valid @RequestBody RouteRequest request,@RequestHeader("X-Actor") String actor) {
    return routes.updateRoute(id,version,request,actor);
  }
  @PatchMapping("/proxy-routes/{id}/status")
  public Map<String,Object> routeStatus(@PathVariable UUID id,@RequestParam long version,
      @Valid @RequestBody StatusRequest request,@RequestHeader("X-Actor") String actor) {
    return routes.updateRouteStatus(id,version,request,actor);
  }

  @GetMapping("/proxy-routes/{routeId}/operations")
  public List<Map<String,Object>> operations(@PathVariable UUID routeId) {
    return routes.operations(routeId);
  }
  @PostMapping("/proxy-routes/{routeId}/operations") @ResponseStatus(HttpStatus.CREATED)
  public Map<String,Object> createOperation(@PathVariable UUID routeId,
      @Valid @RequestBody OperationRequest request,@RequestHeader("X-Actor") String actor) {
    return routes.createOperation(routeId,request,actor);
  }
  @PutMapping("/proxy-routes/{routeId}/operations/{id}")
  public Map<String,Object> updateOperation(@PathVariable UUID routeId,@PathVariable UUID id,
      @RequestParam long version,@Valid @RequestBody OperationRequest request,
      @RequestHeader("X-Actor") String actor) {
    return routes.updateOperation(routeId,id,version,request,actor);
  }
  @DeleteMapping("/proxy-routes/{routeId}/operations/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void deleteOperation(@PathVariable UUID routeId,@PathVariable UUID id,
      @RequestParam long version) { routes.deleteOperation(routeId,id,version); }
  @PostMapping("/proxy-routes/{routeId}/operations/match-test")
  public Map<String,Object> match(@PathVariable UUID routeId,
      @Valid @RequestBody MatchRequest request) { return routes.match(routeId,request); }
}

package com.aurevia.authz.api;

import static com.aurevia.authz.access.AccessModels.GrantResult;
import static com.aurevia.authz.api.dto.SupersetAssetDtos.*;
import static com.aurevia.authz.superset.SupersetAssetModels.*;

import com.aurevia.authz.superset.SupersetAssetService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/internal/v1/registry")
public class SupersetAssetController {
  private final SupersetAssetService service;
  public SupersetAssetController(SupersetAssetService service) { this.service = service; }

  @GetMapping("/superset-assets")
  public List<AssetView> assets() { return service.assets(); }

  @GetMapping("/superset-assets/access-options")
  public AccessOptions accessOptions() { return service.accessOptions(); }

  @GetMapping("/subjects/{subject}/superset-assets")
  public List<AssetView> assetsForSubject(@PathVariable String subject,
      @RequestParam String issuer,
      @RequestParam(value = "instance", defaultValue = "operation-default") String instance) {
    return service.assetsForSubject(issuer, subject, instance);
  }

  @GetMapping("/subjects/{subject}/superset-access")
  public RuntimeAccess accessForSubject(@PathVariable String subject, @RequestParam String issuer,
      @RequestParam(value = "instance", defaultValue = "operation-default") String instance,
      @RequestParam String path,
      @RequestParam(value = "method", defaultValue = "GET") String method,
      @RequestParam(value = "query", defaultValue = "") String query,
      @RequestParam(value = "assetType", defaultValue = "") String assetType,
      @RequestParam(value = "assetId", defaultValue = "") String assetId) {
    return service.accessForSubject(issuer, subject, instance, path, method, query, assetType,
        assetId);
  }

  @GetMapping("/superset-assets/{assetId}/grants")
  public List<AssetGrantView> grants(@PathVariable UUID assetId) { return service.grants(assetId); }

  @PostMapping("/superset-assets")
  @ResponseStatus(HttpStatus.CREATED)
  public CreateResult create(@Valid @RequestBody AssetRequest request,
      @RequestHeader("X-Actor") String actor) {
    return service.create(request.toCommand(), actor);
  }

  @PostMapping("/superset-assets/{assetId}/grants")
  @ResponseStatus(HttpStatus.CREATED)
  public GrantResult grant(@PathVariable UUID assetId,
      @Valid @RequestBody AssetGrantRequest request, @RequestHeader("X-Actor") String actor) {
    return service.grant(assetId, request.subjectType(), request.subjectId(), request.level(), actor);
  }

  @DeleteMapping("/superset-assets/{assetId}/grants/{grantId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void revoke(@PathVariable UUID assetId, @PathVariable UUID grantId,
      @RequestHeader("X-Actor") String actor) {
    service.revoke(assetId, grantId, actor);
  }
}

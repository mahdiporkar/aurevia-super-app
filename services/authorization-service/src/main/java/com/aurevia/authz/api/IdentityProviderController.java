package com.aurevia.authz.api;

import static com.aurevia.authz.api.dto.IdentityProviderDtos.*;
import static com.aurevia.authz.identityprovider.IdentityProviderModels.*;
import com.aurevia.authz.identityprovider.IdentityProviderService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
public class IdentityProviderController {
  private final IdentityProviderService providers;
  public IdentityProviderController(IdentityProviderService providers){this.providers=providers;}

  @GetMapping("/internal/v1/identity-providers")
  public List<ProviderSummary> available(@RequestParam(required=false) String tenant,
      @RequestParam(required=false) String domain){return providers.available(tenant,domain);}
  @GetMapping("/internal/v1/identity-providers/route")
  public ProviderSummary route(@RequestParam(required=false) String code,
      @RequestParam(required=false) String tenant,@RequestParam(required=false) String domain){
    return providers.route(code,tenant,domain);}
  @GetMapping("/internal/v1/identity-providers/{code}")
  public RuntimeProvider runtime(@PathVariable String code){return providers.runtime(code);}

  @GetMapping("/internal/v1/registry/identity-providers")
  public List<ProviderView> list(){return providers.list();}
  @PostMapping("/internal/v1/registry/identity-providers") @ResponseStatus(HttpStatus.CREATED)
  public MutationResult create(@Valid @RequestBody ProviderRequest request,
      @RequestHeader("X-Actor") String actor){return providers.create(request.toCommand(),actor);}
  @PutMapping("/internal/v1/registry/identity-providers/{id}")
  public MutationResult update(@PathVariable UUID id,@RequestParam long version,
      @Valid @RequestBody ProviderRequest request,@RequestHeader("X-Actor") String actor){
    return providers.update(id,version,request.toCommand(),actor);}
  @PatchMapping("/internal/v1/registry/identity-providers/{id}/status")
  public MutationResult enabled(@PathVariable UUID id,@RequestParam long version,
      @RequestBody EnabledRequest request,@RequestHeader("X-Actor") String actor){
    return providers.enabled(id,version,request.enabled(),actor);}
  @PostMapping("/internal/v1/registry/identity-providers/{id}/health-check")
  public HealthResult health(@PathVariable UUID id,@RequestHeader("X-Actor") String actor){
    return providers.health(id,actor);}
}

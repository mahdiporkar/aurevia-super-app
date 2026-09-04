package com.aurevia.authz.api;
import static com.aurevia.authz.api.dto.OutboundRegistryDtos.*;
import static com.aurevia.authz.outbound.OutboundModels.*;
import com.aurevia.authz.outbound.OutboundRegistryService;
import jakarta.validation.Valid;
import java.util.List;import java.util.UUID;
import org.springframework.http.HttpStatus;import org.springframework.web.bind.annotation.*;
/** Metadata-only API: neither credential values nor access tokens cross this service boundary. */
@RestController public class OutboundAuthProfileController {
 private final OutboundRegistryService service;
 public OutboundAuthProfileController(OutboundRegistryService service){this.service=service;}
 @GetMapping("/internal/v1/registry/outbound-auth-profiles") public List<ProfileView> list(@RequestParam(defaultValue="") String search){return service.profiles(search);}
 @GetMapping("/internal/v1/registry/outbound-auth-profiles/{id}") public ProfileView one(@PathVariable UUID id){return service.profile(id);}
 @GetMapping("/internal/v1/outbound-auth-profiles/{id}") public RuntimeProfileView runtime(@PathVariable UUID id){return service.runtimeProfile(id);}
 @PostMapping("/internal/v1/registry/outbound-auth-profiles") @ResponseStatus(HttpStatus.CREATED)
 public ProfileView create(@Valid @RequestBody ProfileRequest request,@RequestHeader("X-Actor") String actor){return service.createProfile(request.toCommand(),actor);}
 @PutMapping("/internal/v1/registry/outbound-auth-profiles/{id}") public ProfileView update(@PathVariable UUID id,
   @RequestParam long version,@Valid @RequestBody ProfileRequest request,@RequestHeader("X-Actor") String actor){return service.updateProfile(id,version,request.toCommand(),actor);}
 @PatchMapping("/internal/v1/registry/outbound-auth-profiles/{id}/status") public ProfileView status(@PathVariable UUID id,
   @RequestParam long version,@RequestBody StatusRequest request,@RequestHeader("X-Actor") String actor){return service.updateProfileStatus(id,version,request.active(),actor);}
 @GetMapping("/internal/v1/registry/outbound-auth-profiles/{id}/usage") public List<UsageView> usage(@PathVariable UUID id){return service.usage(id);}
}

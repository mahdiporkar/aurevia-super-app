package com.aurevia.authz.api;

import com.aurevia.authz.identity.ExternalIdentityAdministrationService;
import com.aurevia.authz.identity.ExternalIdentityAdministrationService.ExternalIdentityView;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/internal/v1/registry/users/{userId}/external-identities")
public class ExternalIdentityAdminController {
  private final ExternalIdentityAdministrationService identities;
  public ExternalIdentityAdminController(ExternalIdentityAdministrationService identities){this.identities=identities;}
  @GetMapping public List<ExternalIdentityView> list(@PathVariable UUID userId){return identities.list(userId);}
  @PostMapping @ResponseStatus(HttpStatus.CREATED)
  public ExternalIdentityView link(@PathVariable UUID userId,@Valid @RequestBody LinkRequest request,
      @RequestHeader("X-Actor") String actor){return identities.link(userId,request.providerCode(),request.subject(),actor);}
  @DeleteMapping("/{identityId}") @ResponseStatus(HttpStatus.NO_CONTENT)
  public void unlink(@PathVariable UUID userId,@PathVariable UUID identityId,
      @RequestHeader("X-Actor") String actor){identities.unlink(userId,identityId,actor);}
  public record LinkRequest(@NotBlank String providerCode,@NotBlank String subject) {}
}

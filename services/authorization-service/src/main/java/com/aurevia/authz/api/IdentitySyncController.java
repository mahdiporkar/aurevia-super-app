package com.aurevia.authz.api;

import static com.aurevia.authz.api.dto.IdentitySyncDtos.*;

import com.aurevia.authz.identity.IdentitySyncService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/v1/identity")
public final class IdentitySyncController {
  private final IdentitySyncService identities;

  public IdentitySyncController(IdentitySyncService identities) { this.identities=identities; }

  @PostMapping("/login-sync")
  public LoginIdentityResponse loginSync(@Valid @RequestBody LoginIdentityRequest request) {
    return identities.sync(request);
  }
}

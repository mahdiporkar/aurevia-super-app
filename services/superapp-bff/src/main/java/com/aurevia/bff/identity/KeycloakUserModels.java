package com.aurevia.bff.identity;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class KeycloakUserModels {
  private KeycloakUserModels() {}

  public record CreateUser(
      @NotBlank @Size(max=255) @Pattern(regexp="[^\\s\\p{Cntrl}]+") String username,
      @NotBlank @Size(max=255) String firstName,
      @NotBlank @Size(max=255) String lastName,
      @NotBlank @Email @Size(max=254) String email,
      @NotNull Boolean enabled,
      @NotBlank @Size(max=1024)
      @JsonProperty(access=JsonProperty.Access.WRITE_ONLY) String initialPassword) {
    // Bean-validation failures and framework diagnostics must never stringify a password.
    @Override public String toString() { return "CreateUser[redacted]"; }
  }

  /** Keycloak ID is stable; the existing local subject is resolved on first login. */
  public record CreatedUser(String id, String username, String firstName, String lastName,
      String email, boolean enabled) {}

  /** Minimal read model for startup diagnostics; never returned to browsers. */
  public record KeycloakUser(String id, String username, boolean enabled) {}
}

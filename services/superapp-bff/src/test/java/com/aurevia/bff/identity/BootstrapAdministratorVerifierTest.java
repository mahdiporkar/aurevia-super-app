package com.aurevia.bff.identity;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.http.HttpStatus;
import reactor.core.publisher.Mono;

class BootstrapAdministratorVerifierTest {
  private final KeycloakAdminUserService users=mock(KeycloakAdminUserService.class);

  @Test void verifiesTheConfiguredSubOnlyWhenTheServiceAccountExists(){
    when(users.configured()).thenReturn(true);
    when(users.lookup("8c604f37-33d2-42e4-a982-35bd5613e974")).thenReturn(Mono.just(
        new KeycloakUserModels.KeycloakUser("8c604f37-33d2-42e4-a982-35bd5613e974","administrator",true)));
    new BootstrapAdministratorVerifier(users," 8c604f37-33d2-42e4-a982-35bd5613e974 ").run(new DefaultApplicationArguments());
    verify(users).lookup("8c604f37-33d2-42e4-a982-35bd5613e974");
  }

  @Test void neverFailsStartupAndSkipsWithoutConfiguration(){
    new BootstrapAdministratorVerifier(users,"").run(new DefaultApplicationArguments());
    when(users.configured()).thenReturn(false);
    new BootstrapAdministratorVerifier(users,"some-sub").run(new DefaultApplicationArguments());
    verify(users,never()).lookup(any());
    when(users.configured()).thenReturn(true);
    when(users.lookup("unknown")).thenReturn(Mono.error(new KeycloakAdminException(HttpStatus.NOT_FOUND,
        "KEYCLOAK_USER_NOT_FOUND","No Keycloak user has this id in the realm.")));
    new BootstrapAdministratorVerifier(users,"unknown").run(new DefaultApplicationArguments());
    when(users.lookup("down")).thenReturn(Mono.error(new KeycloakAdminException(HttpStatus.SERVICE_UNAVAILABLE,
        "KEYCLOAK_UNAVAILABLE","Keycloak is unavailable.")));
    new BootstrapAdministratorVerifier(users,"down").run(new DefaultApplicationArguments());
  }
}

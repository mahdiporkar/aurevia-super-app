package com.aurevia.authz.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aurevia.authz.access.AccessAdministrationService;
import com.aurevia.authz.access.AccessModels.GrantCommand;
import com.aurevia.authz.access.AccessModels.GrantResult;
import com.aurevia.authz.identity.PrimaryIdentityProvider;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class FirstAdministratorBootstrapServiceTest {
  private static final String ISSUER="https://idp.example/realms/aurevia";
  private static final String SUB="8c604f37-33d2-42e4-a982-35bd5613e974";
  private final FirstAdministratorRepository repository=mock(FirstAdministratorRepository.class);
  private final AccessAdministrationService access=mock(AccessAdministrationService.class);
  private final PrimaryIdentityProvider primary=new PrimaryIdentityProvider(ISSUER);

  @Test void grantsTheExistingAdminPermissionOnceAndRecordsCompletionInTheSameUnitOfWork(){
    UUID user=UUID.randomUUID(), resource=UUID.randomUUID(), action=UUID.randomUUID();
    when(repository.completed()).thenReturn(false);
    when(repository.resolveUser(ISSUER,SUB)).thenReturn(user);
    when(repository.adminTarget()).thenReturn(new FirstAdministratorRepository.AdminTarget(resource,action));
    when(access.grant(any(),eq("FIRST_ADMIN_BOOTSTRAP"))).thenReturn(new GrantResult(UUID.randomUUID(),0,false));

    assertThat(service(SUB).provision()).isEqualTo(FirstAdministratorBootstrapService.Outcome.COMPLETED);

    var command=ArgumentCaptor.forClass(GrantCommand.class);
    var order=inOrder(repository,access);
    order.verify(repository).lock();
    order.verify(repository).completed();
    order.verify(repository).resolveUser(ISSUER,SUB);
    order.verify(access).grant(command.capture(),eq("FIRST_ADMIN_BOOTSTRAP"));
    order.verify(repository).markCompleted();
    assertThat(command.getValue().subjectType()).isEqualTo("USER");
    assertThat(command.getValue().subjectId()).isEqualTo(user);
    assertThat(command.getValue().resourceId()).isEqualTo(resource);
    assertThat(command.getValue().actionId()).isEqualTo(action);
    assertThat(command.getValue().expiresAt()).isNull();
  }

  @Test void completedBootstrapNeverGrantsAgainEvenWhenTheVariableIsStillConfigured(){
    when(repository.completed()).thenReturn(true);
    assertThat(service(SUB).provision()).isEqualTo(FirstAdministratorBootstrapService.Outcome.ALREADY_COMPLETED);
    verify(repository).lock();
    verify(repository,never()).resolveUser(any(),any());
    verify(access,never()).grant(any(),any());
    verify(repository,never()).markCompleted();
  }

  @Test void missingConfigurationLeavesTheSystemUnbootstrappedWithoutAnyWrite(){
    when(repository.completed()).thenReturn(false);
    for(String value:new String[]{null,""," "}){
      assertThat(service(value).provision()).isEqualTo(FirstAdministratorBootstrapService.Outcome.NOT_CONFIGURED);
    }
    verify(access,never()).grant(any(),any());
    verify(repository,never()).markCompleted();
  }

  @Test void failuresBeforeCompletionLeaveNoMarkerSoARetryConverges(){
    when(repository.completed()).thenReturn(false);
    when(repository.resolveUser(ISSUER,SUB)).thenThrow(new IllegalStateException(
        "Bootstrap administrator is linked to an inactive application user"));
    assertThatThrownBy(()->service(SUB).provision()).hasMessageContaining("inactive application user");
    verify(repository,never()).markCompleted();
    verify(access,never()).grant(any(),any());
  }

  @Test void malformedSubjectIsRejectedAtStartupWithAClearMessage(){
    assertThatThrownBy(()->service("bad\u0000sub")).isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("AUREVIA_BOOTSTRAP_ADMIN_SUB");
    assertThatThrownBy(()->service("x".repeat(256))).hasMessageContaining("at most 255");
    assertThatThrownBy(()->service("has whitespace")).hasMessageContaining("AUREVIA_BOOTSTRAP_ADMIN_SUB");
  }

  @Test void bootstrapRequiresTheRuntimeIssuerRatherThanAnyRegistryRow(){
    when(repository.completed()).thenReturn(false);
    var withoutIssuer=new FirstAdministratorBootstrapService(repository,access,new PrimaryIdentityProvider(""),SUB);
    assertThatThrownBy(withoutIssuer::provision).hasMessageContaining("OIDC_ISSUER_URI");
    verify(repository,never()).markCompleted();
  }

  private FirstAdministratorBootstrapService service(String subject){
    return new FirstAdministratorBootstrapService(repository,access,primary,subject);
  }
}

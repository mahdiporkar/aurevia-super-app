package com.aurevia.authz.identity;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import com.aurevia.authz.observability.AuditTrail;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.dao.OptimisticLockingFailureException;

class IdentityAdministrationServiceTest {
  private final IdentityRepository repository = mock(IdentityRepository.class);
  private final IdentityAdministrationService service =
      new IdentityAdministrationService(repository, mock(AuditTrail.class));

  @Test void deactivationAndReactivationProjectTheNewVersionAfterUpdatingTheRole() {
    UUID id = UUID.randomUUID();
    when(repository.role(id)).thenReturn(Optional.of(new IdentityModels.RoleSnapshot("reader", 3)));
    when(repository.updateRoleStatus(id, 3, false)).thenReturn(1);
    when(repository.updateRoleStatus(id, 4, true)).thenReturn(1);

    service.updateRoleStatus(id, 3, false, "admin");
    service.updateRoleStatus(id, 4, true, "admin");

    var order = inOrder(repository);
    order.verify(repository).updateRoleStatus(id, 3, false);
    order.verify(repository).enqueueRoleStatus(id, 4, false);
    order.verify(repository).updateRoleStatus(id, 4, true);
    order.verify(repository).enqueueRoleStatus(id, 5, true);
  }

  @Test void staleRoleUpdateCannotQueueProjectionChanges() {
    UUID id = UUID.randomUUID();
    when(repository.role(id)).thenReturn(Optional.of(new IdentityModels.RoleSnapshot("reader", 3)));

    assertThatThrownBy(() -> service.updateRoleStatus(id, 2, false, "admin"))
        .isInstanceOf(OptimisticLockingFailureException.class);

    verify(repository, never()).enqueueRoleStatus(any(), anyLong(), anyBoolean());
  }
}

package com.aurevia.authz.superset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.aurevia.authz.access.AccessAdministrationService;
import com.aurevia.authz.observability.AuditTrail;
import com.aurevia.authz.identity.CanonicalIdentityResolver;
import com.aurevia.authz.openfga.RelationshipAuthorizationPort;
import org.junit.jupiter.api.Test;

class SupersetAssetServiceAuthorizationTest {
  @Test void deniesEveryRuntimePathWhenIntegrationPermissionIsMissing() {
    SupersetInstanceService integrations=mock(SupersetInstanceService.class);
    when(integrations.canAccess("https://issuer.example","subject-1","superset-operation",
        "superset-operation")).thenReturn(false);
    var service=new SupersetAssetService(mock(SupersetAssetRepository.class),
        mock(RelationshipAuthorizationPort.class),mock(AccessAdministrationService.class),
        mock(AuditTrail.class),integrations,mock(CanonicalIdentityResolver.class));

    var decision=service.accessForSubject("https://issuer.example","subject-1",
        "superset-operation","superset-operation","/superset/dashboard/42/","GET","",
        "DASHBOARD","42");

    assertThat(decision.result()).isEqualTo("DENY");
    assertThat(decision.reasonCode()).isEqualTo("SUPERSET_INTEGRATION_DENIED");
  }
}

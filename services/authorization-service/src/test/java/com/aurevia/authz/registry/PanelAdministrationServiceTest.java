package com.aurevia.authz.registry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.aurevia.authz.observability.AuditTrail;
import com.aurevia.authz.ui.UiArtifactPolicy;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class PanelAdministrationServiceTest {
  @Test void registersDynamicExternalMfeWithoutAnOriginEnvironmentListAndAuditsLocation() {
    PanelRepository repository=mock(PanelRepository.class);
    AuditTrail audit=mock(AuditTrail.class);
    var service=new PanelAdministrationService(repository,
        new UiArtifactPolicy("PRODUCTION_INTERNET",false,false,"",""),audit);
    String remote="https://example-mfe.company.com/remoteEntry.js";
    var command=new PanelModels.PanelCommand("CRM","مشتریان","CRM",null,"crm","crm",
        "company_crm","home",remote,"./plugin","/crm","1.0.0","1.0",null,
        "HYBRID","REAL","https://example-mfe.company.com/mf-manifest.json",
        "https://example-mfe.company.com/resource-manifest.json",true,50);

    var created=service.create(command,"security-admin");

    assertThat(created.version()).isZero();
    verify(repository).create(eq(created.id()),eq(command),any(PanelModels.PanelSettings.class));
    @SuppressWarnings("unchecked") ArgumentCaptor<Map<String,Object>> after=ArgumentCaptor.forClass(Map.class);
    verify(audit).success(eq("UI_REGISTRY"),eq("panel.created"),isNull(),isNull(),
        eq("PANEL"),eq(created.id().toString()),eq("CRM"),eq("panel.created"),isNull(),
        after.capture());
    assertThat(after.getValue()).containsEntry("remoteEntryUrl",remote)
        .containsEntry("active",true).containsEntry("actor","security-admin");
  }
}

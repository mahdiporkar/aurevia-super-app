package com.aurevia.authz.ui;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aurevia.authz.authorization.DemoDataPolicy;
import java.util.List;
import org.junit.jupiter.api.Test;

class UiArtifactProductionGuardTest {
  @Test void productionStartupIgnoresExcludedDemoCatalogLocationsWithoutFetchingThem() {
    UiPluginRepository repository=mock(UiPluginRepository.class);
    UiArtifactPolicy policy=mock(UiArtifactPolicy.class);
    when(repository.activeArtifactTargets()).thenReturn(List.of(
        new UiPluginRepository.ArtifactTarget(
            "http://localhost:3001/remoteEntry.js",null,"DEMO")));

    new UiArtifactProductionGuard(repository,policy,new DemoDataPolicy(false))
        .validateActiveArtifacts();

    verify(policy,never()).validate("http://localhost:3001/remoteEntry.js",null);
  }
}

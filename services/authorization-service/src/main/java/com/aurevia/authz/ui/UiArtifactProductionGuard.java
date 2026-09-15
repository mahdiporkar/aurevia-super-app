package com.aurevia.authz.ui;

import com.aurevia.authz.authorization.DemoDataPolicy;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/** Fails a hardened deployment if an effective, non-excluded artifact violates policy. */
@Component
public final class UiArtifactProductionGuard {
  private final UiPluginRepository plugins;
  private final UiArtifactPolicy policy;
  private final DemoDataPolicy demoData;
  public UiArtifactProductionGuard(UiPluginRepository plugins,UiArtifactPolicy policy,
      DemoDataPolicy demoData) {
    this.plugins=plugins;this.policy=policy;this.demoData=demoData;
  }

  @EventListener(ApplicationReadyEvent.class)
  public void validateActiveArtifacts() {
    for(var artifact:plugins.activeArtifactTargets()) {
      if(demoData.allows(artifact.classification()))
        policy.validate(artifact.remoteEntryUrl(),artifact.integrity());
    }
  }
}

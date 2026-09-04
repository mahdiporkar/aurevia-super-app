package com.aurevia.authz.ui;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/** Fails a hardened deployment if an already-active executable artifact violates policy. */
@Component
public final class UiArtifactProductionGuard {
  private final UiPluginRepository plugins;
  private final UiArtifactPolicy policy;
  public UiArtifactProductionGuard(UiPluginRepository plugins,UiArtifactPolicy policy) {
    this.plugins=plugins;this.policy=policy;
  }

  @EventListener(ApplicationReadyEvent.class)
  public void validateActiveArtifacts() {
    for(var artifact:plugins.activeArtifactTargets()) {
      policy.validate(artifact.remoteEntryUrl(),artifact.integrity());
    }
  }
}

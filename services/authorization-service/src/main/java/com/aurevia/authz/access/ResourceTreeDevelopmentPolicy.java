package com.aurevia.authz.access;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

/** Explicit safety gate for development-only resource-tree mutations. */
@Component
public class ResourceTreeDevelopmentPolicy {
  private final boolean enabled;

  @Autowired
  public ResourceTreeDevelopmentPolicy(Environment environment,
      @Value("${aurevia.resource-tree.development-mutations-enabled:false}") boolean requested) {
    this.enabled = requested && environment.acceptsProfiles(Profiles.of("dev"));
  }

  ResourceTreeDevelopmentPolicy(boolean enabled) {
    this.enabled = enabled;
  }

  public boolean enabled() { return enabled; }
}

package com.aurevia.authz.authorization;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Single environment policy used by catalog queries to exclude demo registrations in production. */
@Component
public final class DemoDataPolicy {
  private final boolean enabled;
  public DemoDataPolicy(@Value("${aurevia.demo-data.enabled:true}") boolean enabled) {
    this.enabled=enabled;
  }
  public boolean enabled(){return enabled;}
  public boolean allows(String classification){return enabled||!"DEMO".equals(classification);}
}

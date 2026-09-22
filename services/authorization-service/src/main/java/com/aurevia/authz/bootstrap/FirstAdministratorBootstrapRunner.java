package com.aurevia.authz.bootstrap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/** Runs before the optional startup reconciliation; projection remains the ordinary outbox flow. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class FirstAdministratorBootstrapRunner implements ApplicationRunner {
  private static final Logger log = LoggerFactory.getLogger(FirstAdministratorBootstrapRunner.class);
  private final FirstAdministratorBootstrapService bootstrap;
  public FirstAdministratorBootstrapRunner(FirstAdministratorBootstrapService bootstrap) {
    this.bootstrap = bootstrap;
  }
  @Override public void run(ApplicationArguments args) {
    FirstAdministratorBootstrapService.Outcome outcome;
    try {
      outcome = bootstrap.provision();
    } catch (RuntimeException failure) {
      // Fail fast with an operator-readable reason; nothing was committed, so a corrected restart converges.
      throw new IllegalStateException("First administrator bootstrap failed: " + failure.getMessage()
          + ". Check AUREVIA_BOOTSTRAP_ADMIN_SUB (the Keycloak user id / sub) and OIDC_ISSUER_URI", failure);
    }
    switch (outcome) {
      case NOT_CONFIGURED -> log.warn("First administrator bootstrap is not completed: configure AUREVIA_BOOTSTRAP_ADMIN_SUB with the Keycloak user ID to provision the initial administrator");
      case ALREADY_COMPLETED -> log.info("First administrator bootstrap already completed; existing grants are unchanged");
      case COMPLETED -> log.info("First administrator bootstrap completed; the admin grant is committed for OpenFGA outbox projection");
    }
  }
}

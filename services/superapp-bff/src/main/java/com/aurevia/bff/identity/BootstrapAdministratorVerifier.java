package com.aurevia.bff.identity;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Startup diagnostic for the First Administrator Bootstrap: when the Keycloak admin service account
 * is configured, the configured stable user id is looked up in Keycloak so a typo or a username
 * pasted instead of a sub is visible in the logs immediately. The Authorization Service performs the
 * one-time grant; this check never fails startup and never logs credentials or tokens.
 */
@Component
public class BootstrapAdministratorVerifier implements ApplicationRunner {
  private static final Logger log = LoggerFactory.getLogger(BootstrapAdministratorVerifier.class);
  private final KeycloakAdminUserService users;
  private final String subject;

  public BootstrapAdministratorVerifier(KeycloakAdminUserService users,
      @Value("${aurevia.bootstrap.admin-sub:}") String subject) {
    this.users = users;
    this.subject = subject == null ? "" : subject.trim();
  }

  @Override public void run(ApplicationArguments args) {
    if (subject.isEmpty()) return;
    if (!users.configured()) {
      log.info("AUREVIA_BOOTSTRAP_ADMIN_SUB is set but KEYCLOAK_ADMIN_CLIENT_ID/SECRET are not; skipping Keycloak verification of the bootstrap administrator");
      return;
    }
    users.lookup(subject).subscribe(
        user -> {
          if (user.enabled()) log.info("Bootstrap administrator verified in Keycloak: sub={} username={}", user.id(), user.username());
          else log.warn("Bootstrap administrator exists in Keycloak but is disabled: sub={} username={}", user.id(), user.username());
        },
        failure -> {
          String reason = failure instanceof KeycloakAdminException error ? error.code() : "UNEXPECTED";
          if ("KEYCLOAK_USER_NOT_FOUND".equals(reason))
            log.error("AUREVIA_BOOTSTRAP_ADMIN_SUB={} does not match any Keycloak user in the primary realm. Use the user's stable id (sub) from Keycloak, not the username; the bootstrap grant cannot be used until a Keycloak user with this id logs in", subject);
          else
            log.warn("Could not verify AUREVIA_BOOTSTRAP_ADMIN_SUB against Keycloak ({}): {}", reason, failure.getMessage());
        });
  }
}

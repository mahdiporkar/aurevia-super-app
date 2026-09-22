package com.aurevia.authz.bootstrap;

import com.aurevia.authz.access.AccessAdministrationService;
import com.aurevia.authz.access.AccessModels.GrantCommand;
import com.aurevia.authz.identity.PrimaryIdentityProvider;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * One-time First Administrator Bootstrap.
 *
 * <p>The configured stable Keycloak user id ({@code AUREVIA_BOOTSTRAP_ADMIN_SUB}) is resolved to the
 * canonical Aurevia subject and receives the existing {@code admin} permission on
 * {@code application:aurevia} through the ordinary grant/outbox path. The identity, the grant, its
 * audit entry and the durable completion marker commit in one transaction, so an interrupted start
 * converges on retry and a completed bootstrap is never repeated: revoking the grant later is final.</p>
 */
@Service
public class FirstAdministratorBootstrapService {
  private static final Logger log = LoggerFactory.getLogger(FirstAdministratorBootstrapService.class);
  private final FirstAdministratorRepository repository;
  private final AccessAdministrationService access;
  private final PrimaryIdentityProvider primary;
  private final String subject;

  public FirstAdministratorBootstrapService(FirstAdministratorRepository repository,
      AccessAdministrationService access, PrimaryIdentityProvider primary,
      @Value("${aurevia.bootstrap.admin-sub:}") String subject) {
    this.repository = repository;
    this.access = access;
    this.primary = primary;
    this.subject = subject == null ? "" : subject.trim();
    if (this.subject.length() > 255 || this.subject.chars().anyMatch(
        c -> Character.isISOControl(c) || Character.isWhitespace(c)))
      throw new IllegalArgumentException("AUREVIA_BOOTSTRAP_ADMIN_SUB must be a stable Keycloak user id (sub) "
          + "of at most 255 characters without whitespace or control characters");
    if (!this.subject.isEmpty() && !this.subject.matches("[0-9a-fA-F]{8}(-[0-9a-fA-F]{4}){3}-[0-9a-fA-F]{12}"))
      log.warn("AUREVIA_BOOTSTRAP_ADMIN_SUB is not a UUID; Keycloak user ids are UUIDs, verify the value is the user's sub and not a username");
  }

  @Transactional
  public Outcome provision() {
    repository.lock();
    if (repository.completed()) return Outcome.ALREADY_COMPLETED;
    if (subject.isEmpty()) return Outcome.NOT_CONFIGURED;
    UUID user = repository.resolveUser(primary.issuer(), subject);
    var target = repository.adminTarget();
    access.grant(new GrantCommand(null, "USER", user, target.resourceId(), target.actionId(), null),
        "FIRST_ADMIN_BOOTSTRAP");
    repository.markCompleted();
    return Outcome.COMPLETED;
  }

  public enum Outcome { NOT_CONFIGURED, ALREADY_COMPLETED, COMPLETED }
}

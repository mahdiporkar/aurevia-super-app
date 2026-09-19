package com.aurevia.authz.sync;

import com.aurevia.authz.access.AccessRepository;
import com.aurevia.authz.identity.IdentityRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Makes {@code expires_at} effective without a restart.
 *
 * <p>Reconciliation already excludes expired grants and role assignments from the expected
 * OpenFGA state, but reconciliation only runs at startup. Between restarts an expired record
 * kept its tuple and therefore stayed effective. This sweeper turns each expiry into the same
 * outbox projection that a manual revoke produces, so OpenFGA, {@code /api/me/context} and
 * runtime checks converge within one sweep interval.</p>
 *
 * <p>It reuses the revoke projection code paths rather than restating the tuple mapping.</p>
 */
@Component
public class ExpirationSweeper {
  private static final Logger log = LoggerFactory.getLogger(ExpirationSweeper.class);

  private final ExpirationRepository expirations;
  private final AccessRepository grants;
  private final IdentityRepository roles;
  private final Counter expiredGrants;
  private final Counter expiredAssignments;

  public ExpirationSweeper(ExpirationRepository expirations, AccessRepository grants,
      IdentityRepository roles, MeterRegistry metrics) {
    this.expirations = expirations;
    this.grants = grants;
    this.roles = roles;
    this.expiredGrants = Counter.builder("aurevia.authorization.expired.grants")
        .description("Grants archived because expires_at passed").register(metrics);
    this.expiredAssignments = Counter.builder("aurevia.authorization.expired.role_assignments")
        .description("Role assignments removed because expires_at passed").register(metrics);
  }

  @Scheduled(fixedDelayString = "${aurevia.expiration.sweep-interval-ms:30000}")
  public void scheduledSweep() { sweep(); }

  /** @return number of expired records projected in this pass */
  @Transactional
  public int sweep() {
    int count = 0;
    for (var grant : expirations.expiredGrants()) {
      // Same ordering as AccessAdministrationService.revoke: enqueue, then archive.
      grants.enqueueGrant(grant.id(), "GRANT_DELETE", grant.version() + 1);
      if (expirations.archiveGrant(grant.id()) == 1) {
        expiredGrants.increment();
        count++;
        log.info("AUTHORIZATION_EXPIRED kind=grant grantId={}", grant.id());
      }
    }
    for (var assignment : expirations.expiredRoleAssignments()) {
      // Same ordering as IdentityAdministrationService.revokeRole.
      roles.enqueueRoleAssignment(assignment.subjectType(), assignment.subjectId(),
          assignment.roleId(), "ROLE_ASSIGNMENT_DELETE", assignment.version() + 1);
      if (roles.deleteRoleAssignment(assignment.subjectType(), assignment.subjectId(),
          assignment.roleId()) == 1) {
        expiredAssignments.increment();
        count++;
        log.info("AUTHORIZATION_EXPIRED kind=role-assignment subjectType={} subjectId={} roleId={}",
            assignment.subjectType(), assignment.subjectId(), assignment.roleId());
      }
    }
    return count;
  }
}

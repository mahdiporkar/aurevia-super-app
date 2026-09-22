package com.aurevia.authz.bootstrap;

import java.util.UUID;

interface FirstAdministratorRepository {
  void lock();
  boolean completed();
  UUID resolveUser(String issuer, String subject);
  AdminTarget adminTarget();
  void markCompleted();
  record AdminTarget(UUID resourceId, UUID actionId) {}
}

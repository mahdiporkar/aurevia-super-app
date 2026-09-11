package com.aurevia.authz.identity;

import com.aurevia.authz.identity.ExternalIdentityAdministrationService.ExternalIdentityView;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface CanonicalIdentityRepository {
  Optional<String> canonicalUserId(String issuer,String subject);
  List<ExternalIdentityView> externalIdentities(UUID userId);
  Optional<ProviderIdentity> provider(String code);
  Optional<String> canonicalUserId(UUID userId);
  UUID link(UUID userId,UUID providerId,String issuer,String subject,String actor);
  long identityCount(UUID userId);
  int unlink(UUID userId,UUID identityId);
  record ProviderIdentity(UUID id,String code,String issuer) {}
}

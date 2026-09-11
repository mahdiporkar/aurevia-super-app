package com.aurevia.authz.identity;

import com.aurevia.authz.observability.AuditTrail;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Explicitly manages aliases; it never rewrites the canonical OpenFGA user id. */
@Service
public class ExternalIdentityAdministrationService {
  private final CanonicalIdentityRepository identities;
  private final AuditTrail audit;
  public ExternalIdentityAdministrationService(CanonicalIdentityRepository identities,AuditTrail audit){
    this.identities=identities;this.audit=audit;}

  public List<ExternalIdentityView> list(UUID userId){return identities.externalIdentities(userId);}

  @Transactional public ExternalIdentityView link(UUID userId,String providerCode,String subject,
      String actor){
    if(subject==null||subject.isBlank()||subject.length()>512)throw new IllegalArgumentException("Invalid external subject");
    CanonicalIdentityRepository.ProviderIdentity provider=identities.provider(providerCode)
        .orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Identity provider not found"));
    String canonical=identities.canonicalUserId(userId)
        .orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Canonical user not found"));
    UUID id=identities.link(userId,provider.id(),provider.issuer(),subject.trim(),actor);
    audit.success("IDENTITY","external_identity.linked","USER",canonical,"EXTERNAL_IDENTITY",
        id.toString(),provider.code(),"LINK",null,Map.of("issuer",provider.issuer()));
    return list(userId).stream().filter(value->value.id().equals(id)).findFirst().orElseThrow();
  }

  @Transactional public void unlink(UUID userId,UUID identityId,String actor){
    long count=identities.identityCount(userId);
    if(count<=1)throw new IllegalArgumentException("A canonical user must retain one external identity");
    int deleted=identities.unlink(userId,identityId);
    if(deleted!=1)throw new ResponseStatusException(HttpStatus.NOT_FOUND);
    audit.success("IDENTITY","external_identity.unlinked","USER",userId.toString(),
        "EXTERNAL_IDENTITY",identityId.toString(),null,"UNLINK",null,Map.of());
  }

  @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
  public record ExternalIdentityView(UUID id,UUID userId,UUID identityProviderId,
      String providerCode,String issuer,String subject,String canonicalUserId,Instant lastLoginAt,
      Instant createdAt) {}
}

package com.aurevia.authz.identity;

import static com.aurevia.authz.api.dto.IdentitySyncDtos.*;

import com.aurevia.authz.directory.OuAccessService;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Idempotent application service for OIDC identity and directory projections. */
@Service
public class IdentitySyncService {
  private final IdentitySyncRepository identities;
  private final OuAccessService ouAccess;

  public IdentitySyncService(IdentitySyncRepository identities,OuAccessService ouAccess) {
    this.identities=identities;this.ouAccess=ouAccess;
  }

  @Transactional
  public LoginIdentityResponse sync(LoginIdentityRequest request) {
    var directoryResult=ouAccess.syncLogin(new OuAccessService.LoginDirectoryIdentity(
        request.issuer(),request.subject(),request.username(),request.displayName(),request.email(),
        request.distinguishedName(),request.ouExternalId(),request.directoryExternalId(),
        request.attributes()));
    UUID userId=directoryResult.userId();
    Set<UUID> previous=identities.membershipGroupIds(userId);
    Set<UUID> incoming=new HashSet<>();
    for(DirectoryGroupRequest group:request.groups()) {
      identities.directoryGroupId(request.issuer(),group.externalId()).ifPresent(incoming::add);
    }
    boolean changed=!previous.equals(incoming)||incoming.size()!=request.groups().size();
    long version=changed?identities.incrementMembershipVersion(userId)
        :identities.membershipVersion(userId);
    String subjectKey=identities.subjectKey(userId);
    Set<UUID> current=new HashSet<>();
    for(DirectoryGroupRequest group:request.groups()) {
      UUID groupId=identities.upsertDirectoryGroup(request.issuer(),group.externalId(),
          normalizePath(group.path()),group.displayName());
      identities.addMembership(userId,groupId);
      current.add(groupId);
      if(!previous.contains(groupId)) {
        identities.enqueueMembership(userId,groupId,subjectKey,group.externalId(),
            "GROUP_MEMBERSHIP_WRITE",version);
      }
    }
    for(UUID removed:previous) {
      if(current.contains(removed)) continue;
      identities.enqueueMembership(userId,removed,subjectKey,
          identities.directoryGroupExternalId(removed),"GROUP_MEMBERSHIP_DELETE",version);
      identities.removeMembership(userId,removed);
    }
    return new LoginIdentityResponse(userId,request.groups().size(),directoryResult.ouId(),
        directoryResult.effectiveGroups());
  }

  private static String normalizePath(String path) {
    String normalized=path==null?"/":path.trim().replace('\\','/');
    if(!normalized.startsWith("/")) normalized="/"+normalized;
    return normalized.replaceAll("/{2,}","/");
  }
}

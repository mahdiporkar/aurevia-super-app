package com.aurevia.authz.identity;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/** Resolves an external OIDC issuer/sub alias to the stable OpenFGA principal. */
@Component
public class CanonicalIdentityResolver {
  private final CanonicalIdentityRepository identities;
  public CanonicalIdentityResolver(CanonicalIdentityRepository identities){this.identities=identities;}
  public String canonicalUserId(String issuer,String subject){
    if(issuer==null||issuer.isBlank()||subject==null||subject.isBlank())
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"External identity is incomplete");
    return identities.canonicalUserId(issuer.trim(),subject.trim())
        .orElseThrow(()->new ResponseStatusException(HttpStatus.FORBIDDEN,
            "External identity is not linked to an active canonical user"));
  }
  public String openFgaUser(String issuer,String subject){return "user:"+canonicalUserId(issuer,subject);}
}

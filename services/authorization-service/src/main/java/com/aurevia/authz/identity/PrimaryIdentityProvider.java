package com.aurevia.authz.identity;

import java.net.URI;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Trust anchor for the BFF's infrastructure-managed primary provider. */
@Component
public class PrimaryIdentityProvider {
  public static final String CODE = "public-iam";
  private final String issuer;

  public PrimaryIdentityProvider(@Value("${aurevia.primary-identity.issuer:}") String issuer) {
    this.issuer = issuer == null ? "" : issuer.trim();
    if (!this.issuer.isEmpty()) validateIssuer(this.issuer);
  }

  public String issuer() {
    if (issuer.isEmpty()) throw new IllegalStateException(
        "OIDC_ISSUER_URI is required for primary identity synchronization and administrator bootstrap");
    return issuer;
  }

  public void verifyIssuer(String candidate) {
    if (!issuer().equals(candidate)) throw new IllegalArgumentException(
        "Primary login issuer does not match OIDC_ISSUER_URI");
  }

  private static void validateIssuer(String value) {
    try {
      URI uri = URI.create(value);
      if ((!"https".equals(uri.getScheme()) && !"http".equals(uri.getScheme()))
          || uri.getHost() == null || uri.getUserInfo() != null
          || uri.getQuery() != null || uri.getFragment() != null) throw new IllegalArgumentException();
    } catch (IllegalArgumentException invalid) {
      throw new IllegalArgumentException("OIDC_ISSUER_URI must be an absolute HTTP(S) issuer URI");
    }
  }
}

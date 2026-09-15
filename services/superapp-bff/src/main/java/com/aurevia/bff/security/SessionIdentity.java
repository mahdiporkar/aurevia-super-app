package com.aurevia.bff.security;

import java.io.Serial;
import java.io.Serializable;
import java.security.Principal;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.server.ResponseStatusException;

/** Minimal token-free identity persisted in the server-side web session. */
public record SessionIdentity(String issuer, String subject, String username)
    implements Principal, Serializable {
  @Serial private static final long serialVersionUID = 1L;

  public SessionIdentity {
    issuer = require(issuer, "issuer");
    subject = require(subject, "subject");
    username = username == null || username.isBlank() ? subject : username.trim();
  }

  @Override
  public String getName() {
    return subject;
  }

  public static SessionIdentity from(Principal principal) {
    if (principal instanceof SessionIdentity identity) return identity;
    if (principal instanceof Authentication authentication) {
      if (authentication.getPrincipal() instanceof SessionIdentity identity) return identity;
      if (authentication instanceof OAuth2AuthenticationToken oauth) return from(oauth);
      if (authentication.getPrincipal() instanceof OidcUser oidc) return from(oidc);
    }
    throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "OIDC session identity missing");
  }

  public static SessionIdentity from(OAuth2AuthenticationToken oauth) {
    if (oauth.getPrincipal() instanceof OidcUser oidc) return from(oidc);
    Map<String, Object> claims = oauth.getPrincipal().getAttributes();
    String issuer = value(claims.get("iss"));
    return new SessionIdentity(issuer, value(claims.get("sub")),
        optional(claims.get("preferred_username"), value(claims.get("sub"))));
  }

  private static SessionIdentity from(OidcUser oidc) {
    Map<String, Object> claims = oidc.getAttributes();
    String issuer = oidc.getIdToken().getIssuer() == null
        ? value(claims.get("iss")) : oidc.getIdToken().getIssuer().toString();
    return new SessionIdentity(issuer, value(claims.get("sub")),
        optional(claims.get("preferred_username"), value(claims.get("sub"))));
  }

  private static String value(Object value) {
    return value == null ? null : String.valueOf(value);
  }

  private static String optional(Object value, String fallback) {
    String text = value(value);
    return text == null || text.isBlank() ? fallback : text;
  }

  private static String require(String value, String field) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(field + " is required");
    }
    return value.trim();
  }
}

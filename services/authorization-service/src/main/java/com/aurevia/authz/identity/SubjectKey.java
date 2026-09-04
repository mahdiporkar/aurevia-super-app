package com.aurevia.authz.identity;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

/** Canonical, issuer-scoped identity used in every OpenFGA user tuple. */
public record SubjectKey(String issuer, String subject) {
  public SubjectKey {
    issuer = required(issuer, "issuer");
    subject = required(subject, "subject");
  }

  public String databaseKey() {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      digest.update(issuer.getBytes(StandardCharsets.UTF_8));
      digest.update((byte) 0);
      digest.update(subject.getBytes(StandardCharsets.UTF_8));
      // OpenFGA reserves ':' as the type/id separator. Keep the version marker
      // inside the identifier without introducing a second separator.
      return "v1_" + HexFormat.of().formatHex(digest.digest());
    } catch (java.security.NoSuchAlgorithmException impossible) {
      throw new IllegalStateException("SHA-256 is unavailable", impossible);
    }
  }

  public String openFgaUser() {
    return "user:" + databaseKey();
  }

  private static String required(String value, String field) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(field + " is required");
    }
    return value.trim();
  }
}

package com.aurevia.authz.identity;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;

class SubjectKeyTest {
  @Test void scopesSameSubjectByIssuerAndNeverLeaksRawIdentity() {
    var first=new SubjectKey("https://issuer-a.example","same-subject");
    var second=new SubjectKey("https://issuer-b.example","same-subject");
    assertThat(first.openFgaUser()).startsWith("user:v1_")
        .matches("^user:[A-Za-z0-9_-]+$")
        .doesNotContain("issuer-a").doesNotContain("same-subject")
        .isNotEqualTo(second.openFgaUser());
  }
}

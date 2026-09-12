package com.aurevia.testlegacy;
import static org.assertj.core.api.Assertions.*;
import java.time.*;
import org.junit.jupiter.api.Test;
class LegacyCredentialsTest {
  @Test void rejectsMissingInvalidAndSsoCredentials() {
    var credentials=new LegacyCredentials("service","test-password",60,Clock.systemUTC());
    assertThat(credentials.accepts(null)).isFalse();
    assertThat(credentials.accepts("invalid")).isFalse();
    assertThat(credentials.accepts("eyJ.invalid.sso")).isFalse();
    assertThat(credentials.acquire("service","incorrect")).isNull();
  }
  @Test void issuedCredentialIsOpaqueAndExpires() {
    Instant now=Instant.parse("2026-01-01T00:00:00Z");
    var clock=new MutableClock(now);
    var credentials=new LegacyCredentials("service","test-password",60,clock);
    String token=credentials.acquire("service","test-password");
    assertThat(token).startsWith("legacy_").doesNotContain(".");
    assertThat(credentials.accepts(token)).isTrue();
    clock.now=now.plusSeconds(60);
    assertThat(credentials.accepts(token)).isFalse();
  }
  private static class MutableClock extends Clock {
    Instant now; MutableClock(Instant now) { this.now=now; }
    public ZoneId getZone() { return ZoneOffset.UTC; }
    public Clock withZone(ZoneId zone) { return this; }
    public Instant instant() { return now; }
  }
}

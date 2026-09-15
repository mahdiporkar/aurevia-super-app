package com.aurevia.bff.security;

import static org.assertj.core.api.Assertions.*;
import java.util.Base64;
import org.junit.jupiter.api.Test;

class TokenVaultCryptoTest {
  @Test void encryptsWithRandomNonceAndDecrypts() {
    var key = Base64.getEncoder().encodeToString(new byte[32]);
    var crypto = new TokenVaultCrypto("v1", key);
    String first = crypto.encrypt("secret-token"), second = crypto.encrypt("secret-token");
    assertThat(first).isNotEqualTo(second).doesNotContain("secret-token");
    assertThat(crypto.decrypt(first)).isEqualTo("secret-token");
  }

  @Test void decryptsPreviousKeyDuringRotationAndEncryptsWithCurrentKey() {
    String previous = Base64.getEncoder().encodeToString(new byte[32]);
    byte[] currentBytes = new byte[32];
    java.util.Arrays.fill(currentBytes, (byte) 7);
    String current = Base64.getEncoder().encodeToString(currentBytes);
    String oldEnvelope = new TokenVaultCrypto("old", previous).encrypt("refresh-token");
    var rotating = new TokenVaultCrypto("current", current, "old=" + previous);
    assertThat(rotating.decrypt(oldEnvelope)).isEqualTo("refresh-token");
    assertThat(rotating.encrypt("access-token")).startsWith("current.");
  }
}

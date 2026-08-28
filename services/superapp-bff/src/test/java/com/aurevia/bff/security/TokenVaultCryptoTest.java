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
}

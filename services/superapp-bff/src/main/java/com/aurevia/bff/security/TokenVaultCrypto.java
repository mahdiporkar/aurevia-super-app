package com.aurevia.bff.security;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/** Application-level AES-256-GCM envelope. The key is provided by a secret store, never source control. */
public final class TokenVaultCrypto {
  private static final SecureRandom RANDOM = new SecureRandom();
  private final String keyId;
  private final SecretKey key;

  public TokenVaultCrypto(String keyId, String keyBase64) {
    this.keyId = keyId;
    byte[] raw = Base64.getDecoder().decode(keyBase64);
    if (raw.length != 32) throw new IllegalArgumentException("Token vault key must be 256 bits");
    this.key = new SecretKeySpec(raw, "AES");
  }

  public String encrypt(String value) {
    try {
      byte[] iv = new byte[12]; RANDOM.nextBytes(iv);
      Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
      cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(128, iv));
      byte[] ciphertext = cipher.doFinal(value.getBytes(StandardCharsets.UTF_8));
      return keyId + "." + Base64.getUrlEncoder().withoutPadding().encodeToString(iv) + "." + Base64.getUrlEncoder().withoutPadding().encodeToString(ciphertext);
    } catch (Exception e) { throw new IllegalStateException("Token encryption failed", e); }
  }

  public String decrypt(String envelope) {
    try {
      String[] parts = envelope.split("\\.");
      if (parts.length != 3 || !keyId.equals(parts[0])) throw new IllegalArgumentException("Unknown token key identifier");
      Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
      cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, Base64.getUrlDecoder().decode(parts[1])));
      return new String(cipher.doFinal(Base64.getUrlDecoder().decode(parts[2])), StandardCharsets.UTF_8);
    } catch (Exception e) { throw new IllegalStateException("Token decryption failed", e); }
  }
}

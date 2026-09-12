package com.aurevia.testlegacy;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.*;
import java.util.Base64;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
class LegacyCredentials {
  private final String username, password;
  private final long ttl;
  private final Clock clock;
  private final ConcurrentHashMap<String,Instant> credentials = new ConcurrentHashMap<>();
  @org.springframework.beans.factory.annotation.Autowired
  LegacyCredentials(@Value("${test-legacy.username}") String username,
      @Value("${test-legacy.password}") String password, @Value("${test-legacy.token-ttl-seconds}") long ttl) {
    this(username,password,ttl,Clock.systemUTC());
  }
  LegacyCredentials(String username,String password,long ttl,Clock clock) {
    if (username.isBlank() || password.isBlank() || ttl < 1) throw new IllegalArgumentException("Legacy credentials required");
    this.username=username; this.password=password; this.ttl=ttl; this.clock=clock;
  }
  String acquire(String username,String password) {
    if (!same(this.username,username) || !same(this.password,password)) return null;
    credentials.entrySet().removeIf(entry -> !entry.getValue().isAfter(clock.instant()));
    byte[] bytes=new byte[32]; new SecureRandom().nextBytes(bytes);
    String token="legacy_"+Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    credentials.put(fingerprint(token),clock.instant().plusSeconds(ttl));
    return token;
  }
  boolean accepts(String token) {
    if (token==null || !token.startsWith("legacy_")) return false;
    Instant expires=credentials.get(fingerprint(token));
    return expires!=null && expires.isAfter(clock.instant());
  }
  long ttl() { return ttl; }
  private static boolean same(String a,String b) {
    return b!=null && MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8),b.getBytes(StandardCharsets.UTF_8));
  }
  private static String fingerprint(String token) {
    try { return Base64.getEncoder().encodeToString(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8))); }
    catch (NoSuchAlgorithmException e) { throw new IllegalStateException("SHA-256 unavailable"); }
  }
}

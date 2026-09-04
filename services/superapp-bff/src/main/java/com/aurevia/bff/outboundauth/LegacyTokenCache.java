package com.aurevia.bff.outboundauth;

import com.aurevia.bff.security.TokenVaultCrypto;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/** Encrypted, single-key Redis cache. A write is atomic from a reader's perspective. */
@Component
final class LegacyTokenCache {
  private final ReactiveStringRedisTemplate redis;
  private final ObjectMapper json;
  private final TokenVaultCrypto crypto;
  private final String environment;

  LegacyTokenCache(ReactiveStringRedisTemplate redis,ObjectMapper json,
      @Value("${aurevia.legacy.token-vault.key-id:${aurevia.token-vault.key-id}}") String keyId,
      @Value("${aurevia.legacy.token-vault.key-base64:${aurevia.token-vault.key-base64}}") String key,
      @Value("${aurevia.legacy.token-vault.previous-keys:${aurevia.token-vault.previous-keys:}}") String previousKeys,
      @Value("${aurevia.legacy.environment:local}") String environment) {
    this.redis=redis;this.json=json;this.crypto=new TokenVaultCrypto(keyId,key,previousKeys);
    this.environment=environment;
  }

  Mono<Cached> read(String profile,long profileVersion,int skewSeconds) {
    return redis.opsForValue().get(key(profile)).flatMap(raw->{
      try {
        Map<?,?> payload=json.readValue(raw,Map.class);
        if(number(payload,"profileVersion").longValue()!=profileVersion) return Mono.empty();
        Instant expiry=Instant.parse(required(payload,"expiresAt"));
        if(!Instant.now().isBefore(expiry.minusSeconds(skewSeconds))) return Mono.empty();
        return Mono.just(new Cached(crypto.decrypt(required(payload,"encryptedAccessToken")),
            required(payload,"tokenType"),expiry,required(payload,"credentialVersion"),profileVersion));
      } catch(Exception error) {
        return Mono.error(new IllegalStateException("Legacy token cache entry is invalid",error));
      }
    });
  }

  Mono<Void> write(String profile,LegacyTokenResponseParser.Parsed token,
      String credentialVersion,long profileVersion) {
    try {
      long ttl=Math.max(1,Duration.between(Instant.now(),token.expiresAt()).toSeconds());
      Map<String,Object> payload=new LinkedHashMap<>();
      payload.put("encryptedAccessToken",crypto.encrypt(token.accessToken()));
      payload.put("tokenType",token.tokenType());
      payload.put("expiresAt",token.expiresAt().toString());
      payload.put("credentialVersion",credentialVersion);
      payload.put("profileVersion",profileVersion);
      return redis.opsForValue().set(key(profile),json.writeValueAsString(payload),
          Duration.ofSeconds(ttl)).then();
    } catch(Exception error) {
      return Mono.error(new IllegalStateException("Legacy token cache write failed",error));
    }
  }

  Mono<Void> invalidate(String profile) { return redis.delete(key(profile)).then(); }
  Mono<Boolean> cached(String profile) { return redis.hasKey(key(profile)); }
  String lock(String profile) { return "legacy-token-lock:"+environment+":"+profile; }
  private String key(String profile) { return "legacy-token-vault:"+environment+":"+profile; }

  private static String required(Map<?,?> payload,String key) {
    Object value=payload.get(key);
    if(value==null || String.valueOf(value).isBlank()) throw new IllegalArgumentException("Missing cache field");
    return String.valueOf(value);
  }
  private static Number number(Map<?,?> payload,String key) {
    Object value=payload.get(key);
    if(!(value instanceof Number number)) throw new IllegalArgumentException("Missing cache field");
    return number;
  }

  record Cached(String accessToken,String tokenType,Instant expiresAt,
      String credentialVersion,long profileVersion) {
    @Override public String toString() { return "Cached[REDACTED]"; }
  }
}

package com.aurevia.bff.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/** Redis-backed server-side token vault. Only an opaque handle belongs in the browser session. */
@Service
public class TokenVaultService {
  public record Tokens(String accessToken, String refreshToken, Instant expiresAt,
      Instant vaultExpiresAt) {
    public Tokens(String accessToken, String refreshToken, Instant expiresAt) {
      this(accessToken, refreshToken, expiresAt,
          refreshToken == null ? expiresAt : expiresAt.plus(Duration.ofMinutes(30)));
    }
  }
  private record EncryptedTokens(String accessToken, String refreshToken, Instant expiresAt,
      Instant vaultExpiresAt) {}
  private final ReactiveStringRedisTemplate redis;
  private final TokenVaultCrypto crypto;
  private final ObjectMapper json;
  private final String namespace;

  public TokenVaultService(ReactiveStringRedisTemplate redis, ObjectMapper json,
      @Value("${aurevia.token-vault.namespace}") String namespace,
      @Value("${aurevia.token-vault.key-id}") String keyId,
      @Value("${aurevia.token-vault.key-base64}") String key) {
    this.redis=redis; this.json=json; this.namespace=namespace;
    this.crypto=new TokenVaultCrypto(keyId,key);
  }
  public Mono<String> store(Tokens tokens) {
    String handle=UUID.randomUUID().toString();
    return write(handle,tokens).thenReturn(handle);
  }
  public Mono<Void> write(String handle, Tokens tokens) {
    Instant vaultExpiry = tokens.vaultExpiresAt() == null ? tokens.expiresAt() : tokens.vaultExpiresAt();
    if (vaultExpiry.isBefore(Instant.now())) {
      return Mono.error(new IllegalArgumentException("expired token vault record"));
    }
    try {
      var value=json.writeValueAsString(new EncryptedTokens(crypto.encrypt(tokens.accessToken()),
          tokens.refreshToken()==null?null:crypto.encrypt(tokens.refreshToken()),tokens.expiresAt(),
          vaultExpiry));
      Duration ttl=Duration.between(Instant.now(),vaultExpiry).plus(Duration.ofMinutes(5));
      return redis.opsForValue().set(key(handle),value,ttl).flatMap(ok -> ok?Mono.empty():Mono.error(new IllegalStateException("vault write failed")));
    } catch(Exception e){ return Mono.error(new IllegalStateException("vault serialization failed",e)); }
  }
  public Mono<Tokens> read(String handle) {
    return redis.opsForValue().get(key(handle)).switchIfEmpty(Mono.error(new TokenNotFoundException()))
      .map(value -> { try { var v=json.readValue(value,EncryptedTokens.class); return new Tokens(crypto.decrypt(v.accessToken()),v.refreshToken()==null?null:crypto.decrypt(v.refreshToken()),v.expiresAt(),v.vaultExpiresAt()==null?v.expiresAt():v.vaultExpiresAt()); }
        catch(Exception e){throw new IllegalStateException("vault record invalid",e);} });
  }
  public Mono<Boolean> delete(String handle){return redis.delete(key(handle)).map(n->n>0);}
  private String key(String handle){if(!handle.matches("[0-9a-f-]{36}"))throw new IllegalArgumentException("invalid vault handle");return namespace+":"+handle;}
  public static final class TokenNotFoundException extends RuntimeException {}
}

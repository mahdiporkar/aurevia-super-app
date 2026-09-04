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
      @Value("${aurevia.token-vault.key-base64}") String key,
      @Value("${aurevia.token-vault.previous-keys:}") String previousKeys) {
    this.redis=redis; this.json=json; this.namespace=namespace;
    this.crypto=new TokenVaultCrypto(keyId,key,previousKeys);
  }
  public Mono<String> store(Tokens tokens) {
    String handle=UUID.randomUUID().toString();
    return write(handle,tokens).thenReturn(handle);
  }
  public Mono<Void> write(String handle, Tokens tokens) {
    Instant vaultExpiry = tokens.vaultExpiresAt() == null ? tokens.expiresAt() : tokens.vaultExpiresAt();
    if (!vaultExpiry.isAfter(Instant.now())) {
      return Mono.error(new IllegalArgumentException("expired token vault record"));
    }
    try {
      var value=json.writeValueAsString(new EncryptedTokens(crypto.encrypt(tokens.accessToken()),
          tokens.refreshToken()==null?null:crypto.encrypt(tokens.refreshToken()),tokens.expiresAt(),
          vaultExpiry));
      Duration ttl=Duration.between(Instant.now(),vaultExpiry);
      return redis.opsForValue().set(key(handle),value,ttl).flatMap(ok -> ok?Mono.empty():Mono.error(new IllegalStateException("vault write failed")));
    } catch(Exception e){ return Mono.error(new IllegalStateException("vault serialization failed",e)); }
  }
  public Mono<Tokens> read(String handle) {
    return redis.opsForValue().get(key(handle)).switchIfEmpty(Mono.error(new TokenNotFoundException()))
      .flatMap(value -> {
        try {
          var encrypted=json.readValue(value,EncryptedTokens.class);
          Instant vaultExpiry=encrypted.vaultExpiresAt()==null
              ? encrypted.expiresAt():encrypted.vaultExpiresAt();
          if(!Instant.now().isBefore(vaultExpiry)) {
            return delete(handle).then(Mono.error(new TokenExpiredException()));
          }
          return Mono.just(new Tokens(crypto.decrypt(encrypted.accessToken()),
              encrypted.refreshToken()==null?null:crypto.decrypt(encrypted.refreshToken()),
              encrypted.expiresAt(),vaultExpiry));
        } catch(TokenExpiredException expired) {
          return Mono.error(expired);
        } catch(Exception invalid) {
          return Mono.error(new IllegalStateException("vault record invalid",invalid));
        }
      });
  }
  public Mono<Boolean> delete(String handle){return redis.delete(key(handle)).map(n->n>0);}
  private String key(String handle){
    try { UUID.fromString(handle); }
    catch(RuntimeException invalid){throw new IllegalArgumentException("invalid vault handle",invalid);}
    return namespace+":"+handle;
  }
  public static final class TokenNotFoundException extends RuntimeException {}
  public static final class TokenExpiredException extends RuntimeException {}
}

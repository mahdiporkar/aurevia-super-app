package com.aurevia.authz.openfga;

import dev.openfga.sdk.api.client.OpenFgaClient;
import dev.openfga.sdk.api.client.model.ClientCheckRequest;
import dev.openfga.sdk.api.client.model.ClientTupleKey;
import dev.openfga.sdk.api.client.model.ClientTupleKeyWithoutCondition;
import java.util.List;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
class OpenFgaRelationshipAdapter implements RelationshipAuthorizationPort {
  private final OpenFgaClient client;
  private final StringRedisTemplate redis;
  private final Duration cacheTtl;
  private final String cacheNamespace;

  OpenFgaRelationshipAdapter(OpenFgaClient client, StringRedisTemplate redis,
      @Value("${aurevia.openfga.cache.ttl:5s}") Duration cacheTtl,
      @Value("${aurevia.openfga.cache.namespace:aurevia:openfga:check}") String cacheNamespace) {
    this.client = client;
    this.redis = redis;
    this.cacheTtl = cacheTtl;
    this.cacheNamespace = cacheNamespace;
  }
  @Override public boolean check(String user, String relation, String object) {
    String key = cacheKey(user, relation, object);
    try {
      String cached = redis.opsForValue().get(key);
      if (cached != null) return "1".equals(cached);
    } catch (RuntimeException cacheUnavailable) {
      // Redis is an acceleration layer; OpenFGA remains the source of truth.
    }
    try {
      var response = client.check(new ClientCheckRequest().user(user).relation(relation)._object(object)).get();
      boolean allowed = Boolean.TRUE.equals(response.getAllowed());
      try {
        redis.opsForValue().set(key, allowed ? "1" : "0", cacheTtl);
      } catch (RuntimeException cacheUnavailable) {
        // A cache write failure must not replace a valid OpenFGA decision.
      }
      return allowed;
    } catch (Exception unavailable) {
      // Availability, malformed model, timeout, and missing context all fail closed.
      return false;
    }
  }

  @Override
  public void write(String user, String relation, String object) {
    try {
      client.writeTuples(List.of(new ClientTupleKey()
          .user(user).relation(relation)._object(object))).get();
      evict(user, relation, object);
    } catch (Exception failure) {
      throw new IllegalStateException("OpenFGA tuple write failed", failure);
    }
  }

  @Override
  public void delete(String user, String relation, String object) {
    try {
      client.deleteTuples(List.of(new ClientTupleKeyWithoutCondition()
          .user(user).relation(relation)._object(object))).get();
    } catch (Exception failure) {
      // OpenFGA deletion is idempotent from the outbox consumer perspective.
      if (!String.valueOf(failure.getMessage()).contains("tuple_not_found")) {
        throw new IllegalStateException("OpenFGA tuple delete failed", failure);
      }
    }
    evict(user, relation, object);
  }

  private void evict(String user, String relation, String object) {
    try {
      redis.delete(cacheKey(user, relation, object));
    } catch (RuntimeException cacheUnavailable) {
      // The short TTL bounds stale decisions if Redis is temporarily unavailable.
    }
  }

  private String cacheKey(String user, String relation, String object) {
    try {
      String tuple = user + '\0' + relation + '\0' + object;
      String digest = HexFormat.of().formatHex(
          MessageDigest.getInstance("SHA-256").digest(tuple.getBytes(StandardCharsets.UTF_8)));
      return cacheNamespace + ':' + digest;
    } catch (Exception impossible) {
      throw new IllegalStateException("SHA-256 is unavailable", impossible);
    }
  }
}

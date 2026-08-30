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
  private final String graphEpochKey;

  OpenFgaRelationshipAdapter(OpenFgaClient client, StringRedisTemplate redis,
      @Value("${aurevia.openfga.cache.ttl:5s}") Duration cacheTtl,
      @Value("${aurevia.openfga.cache.namespace:aurevia:openfga:check}") String cacheNamespace) {
    this.client = client;
    this.redis = redis;
    this.cacheTtl = cacheTtl;
    this.cacheNamespace = cacheNamespace;
    this.graphEpochKey = cacheNamespace + ":graph-epoch";
  }
  @Override public boolean check(String user, String relation, String object) {
    String epoch;
    try {
      epoch = redis.opsForValue().get(graphEpochKey);
      if (epoch == null) {
        Boolean initialized = redis.opsForValue().setIfAbsent(graphEpochKey, "0");
        epoch = Boolean.TRUE.equals(initialized) ? "0" : redis.opsForValue().get(graphEpochKey);
      }
      if (epoch == null) throw new IllegalStateException("Missing authorization graph epoch");
    } catch (RuntimeException cacheUnavailable) {
      return directCheck(user, relation, object);
    }
    String key = cacheKey(epoch, user, relation, object);
    try {
      String cached = redis.opsForValue().get(key);
      if (cached != null) return "1".equals(cached);
    } catch (RuntimeException cacheUnavailable) {
      // Redis is an acceleration layer; OpenFGA remains the source of truth.
    }
    try {
      boolean allowed = directCheck(user, relation, object);
      try {
        redis.opsForValue().set(key, allowed ? "1" : "0", cacheTtl);
      } catch (RuntimeException cacheUnavailable) {
        // A cache write failure must not replace a valid OpenFGA decision.
      }
      return allowed;
    } catch (RuntimeException unavailable) { return false; }
  }

  private boolean directCheck(String user, String relation, String object) {
    try {
      var response = client.check(new ClientCheckRequest().user(user).relation(relation)._object(object)).get();
      return Boolean.TRUE.equals(response.getAllowed());
    } catch (Exception unavailable) { return false; }
  }

  @Override
  public void write(String user, String relation, String object) {
    try {
      bumpGraphEpoch();
      client.writeTuples(List.of(new ClientTupleKey()
          .user(user).relation(relation)._object(object))).get();
    } catch (Exception failure) {
      if (failureChain(failure).contains("already exists")) return;
      throw new IllegalStateException("OpenFGA tuple write failed", failure);
    }
  }

  private static String failureChain(Throwable failure) {
    StringBuilder result=new StringBuilder();
    for(Throwable current=failure;current!=null;current=current.getCause())result.append(' ').append(current.getMessage());
    return result.toString().toLowerCase(java.util.Locale.ROOT);
  }

  @Override
  public void delete(String user, String relation, String object) {
    try {
      bumpGraphEpoch();
      client.deleteTuples(List.of(new ClientTupleKeyWithoutCondition()
          .user(user).relation(relation)._object(object))).get();
    } catch (Exception failure) {
      // OpenFGA deletion is idempotent from the outbox consumer perspective.
      if (!String.valueOf(failure.getMessage()).contains("tuple_not_found")) {
        throw new IllegalStateException("OpenFGA tuple delete failed", failure);
      }
    }
  }

  private void bumpGraphEpoch() {
    try {
      Long epoch = redis.opsForValue().increment(graphEpochKey);
      if (epoch == null) throw new IllegalStateException("Redis did not increment graph epoch");
    } catch (RuntimeException unavailable) {
      // Mutating OpenFGA without invalidating inherited decisions is unsafe.
      throw new IllegalStateException("Authorization cache epoch update failed", unavailable);
    }
  }

  private String cacheKey(String epoch, String user, String relation, String object) {
    try {
      String tuple = user + '\0' + relation + '\0' + object;
      String digest = HexFormat.of().formatHex(
          MessageDigest.getInstance("SHA-256").digest(tuple.getBytes(StandardCharsets.UTF_8)));
      return cacheNamespace + ':' + epoch + ':' + digest;
    } catch (Exception impossible) {
      throw new IllegalStateException("SHA-256 is unavailable", impossible);
    }
  }
}

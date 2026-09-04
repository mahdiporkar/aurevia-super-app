package com.aurevia.authz.openfga;

import dev.openfga.sdk.api.client.OpenFgaClient;
import dev.openfga.sdk.api.client.model.ClientCheckRequest;
import dev.openfga.sdk.api.client.model.ClientReadRequest;
import dev.openfga.sdk.api.client.model.ClientTupleKey;
import dev.openfga.sdk.api.client.model.ClientTupleKeyWithoutCondition;
import dev.openfga.sdk.api.configuration.ClientCheckOptions;
import dev.openfga.sdk.api.configuration.ClientBatchCheckOptions;
import dev.openfga.sdk.api.client.model.ClientBatchCheckItem;
import dev.openfga.sdk.api.client.model.ClientBatchCheckRequest;
import dev.openfga.sdk.api.model.ConsistencyPreference;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.UUID;
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
      var options = new ClientCheckOptions().consistency(ConsistencyPreference.HIGHER_CONSISTENCY);
      var response = client.check(
          new ClientCheckRequest().user(user).relation(relation)._object(object), options).get();
      return Boolean.TRUE.equals(response.getAllowed());
    } catch (Exception unavailable) { return false; }
  }

  @Override
  public Map<RelationshipCheck,Boolean> checkBatch(List<RelationshipCheck> checks) {
    if(checks.isEmpty()) return Map.of();
    String epoch=readEpoch();
    Map<RelationshipCheck,Boolean> decisions=new LinkedHashMap<>();
    List<RelationshipCheck> pending=new ArrayList<>();
    for(RelationshipCheck check:checks) {
      String cached=epoch==null?null:readCache(cacheKey(epoch,check.user(),check.relation(),check.object()));
      if(cached==null) pending.add(check); else decisions.put(check,"1".equals(cached));
    }
    if(!pending.isEmpty()) {
      Map<String,RelationshipCheck> correlations=new LinkedHashMap<>();
      List<ClientBatchCheckItem> items=new ArrayList<>();
      for(RelationshipCheck check:pending) {
        String correlation=UUID.randomUUID().toString();correlations.put(correlation,check);
        items.add(new ClientBatchCheckItem().user(check.user()).relation(check.relation())
            ._object(check.object()).correlationId(correlation));
      }
      try {
        var options=new ClientBatchCheckOptions().maxBatchSize(50).maxParallelRequests(4)
            .consistency(ConsistencyPreference.HIGHER_CONSISTENCY);
        var response=client.batchCheck(ClientBatchCheckRequest.ofChecks(items),options).get();
        for(var result:response.getResult()) {
          RelationshipCheck check=correlations.get(result.getCorrelationId());
          if(check==null) continue;
          boolean allowed=result.getError()==null&&result.isAllowed();
          decisions.put(check,allowed);
          if(epoch!=null) writeCache(cacheKey(epoch,check.user(),check.relation(),check.object()),allowed);
        }
      } catch(Exception unavailable) {
        pending.forEach(check->decisions.putIfAbsent(check,false));
      }
      pending.forEach(check->decisions.putIfAbsent(check,false));
    }
    return Map.copyOf(decisions);
  }

  @Override
  public void write(String user, String relation, String object) {
    bumpGraphEpoch();
    try {
      client.writeTuples(List.of(new ClientTupleKey()
          .user(user).relation(relation)._object(object))).get();
    } catch (Exception failure) {
      if (!failureChain(failure).contains("already exists")) {
        throw new IllegalStateException("OpenFGA tuple write failed: " + failureSummary(failure), failure);
      }
    } finally {
      bumpGraphEpoch();
    }
  }

  private static String failureChain(Throwable failure) {
    StringBuilder result=new StringBuilder();
    for(Throwable current=failure;current!=null;current=current.getCause())result.append(' ').append(current.getMessage());
    return result.toString().toLowerCase(java.util.Locale.ROOT);
  }

  private static String failureSummary(Throwable failure) {
    String summary = failureChain(failure).replaceAll("[\\r\\n\\t]+", " ").trim();
    return summary.length() > 600 ? summary.substring(0, 600) : summary;
  }

  @Override
  public void delete(String user, String relation, String object) {
    // OpenFGA reports deletion of an absent tuple as an error. The outbox is
    // at-least-once, so absence is already the desired final state.
    if (!tupleExists(user, relation, object)) return;
    bumpGraphEpoch();
    try {
      client.deleteTuples(List.of(new ClientTupleKeyWithoutCondition()
          .user(user).relation(relation)._object(object))).get();
    } catch (Exception failure) {
      // A concurrent/replayed delete is successful when the exact tuple no
      // longer exists. Other validation or availability failures remain fatal.
      if (tupleExists(user, relation, object)) {
        throw new IllegalStateException(
            "OpenFGA tuple delete failed: " + failureSummary(failure), failure);
      }
    } finally {
      bumpGraphEpoch();
    }
  }

  private boolean tupleExists(String user, String relation, String object) {
    try {
      var response = client.read(new ClientReadRequest()
          .user(user).relation(relation)._object(object)).get();
      return response.getTuples() != null && !response.getTuples().isEmpty();
    } catch (Exception failure) {
      throw new IllegalStateException(
          "OpenFGA tuple existence check failed: " + failureSummary(failure), failure);
    }
  }

  private String readEpoch() {
    try {
      String epoch=redis.opsForValue().get(graphEpochKey);
      if(epoch==null) {
        Boolean initialized=redis.opsForValue().setIfAbsent(graphEpochKey,"0");
        epoch=Boolean.TRUE.equals(initialized)?"0":redis.opsForValue().get(graphEpochKey);
      }
      return epoch;
    } catch(RuntimeException unavailable) { return null; }
  }

  private String readCache(String key) {
    try { return redis.opsForValue().get(key); }
    catch(RuntimeException unavailable) { return null; }
  }

  private void writeCache(String key,boolean allowed) {
    try { redis.opsForValue().set(key,allowed?"1":"0",cacheTtl); }
    catch(RuntimeException unavailable) { /* cache is an acceleration layer */ }
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

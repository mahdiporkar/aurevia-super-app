package com.aurevia.authz.openfga;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

import dev.openfga.sdk.api.client.OpenFgaClient;
import dev.openfga.sdk.api.client.model.ClientCheckResponse;
import dev.openfga.sdk.api.client.model.ClientReadRequest;
import dev.openfga.sdk.api.client.model.ClientReadResponse;
import dev.openfga.sdk.api.configuration.ClientCheckOptions;
import dev.openfga.sdk.api.model.ConsistencyPreference;
import dev.openfga.sdk.api.model.Tuple;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

class OpenFgaRelationshipAdapterTest {
  @Test
  void requestsHigherConsistencyForAuthorizationDecisions() throws Exception {
    OpenFgaClient client = mock(OpenFgaClient.class);
    StringRedisTemplate redis = mock(StringRedisTemplate.class);
    @SuppressWarnings("unchecked")
    ValueOperations<String, String> values = mock(ValueOperations.class);
    when(redis.opsForValue()).thenReturn(values);
    when(values.get("test:openfga:graph-epoch")).thenReturn("7");
    ClientCheckResponse response = mock(ClientCheckResponse.class);
    when(response.getAllowed()).thenReturn(true);
    when(client.check(org.mockito.ArgumentMatchers.any(),
        org.mockito.ArgumentMatchers.any(ClientCheckOptions.class)))
        .thenReturn(CompletableFuture.completedFuture(response));

    var adapter = new OpenFgaRelationshipAdapter(client, redis,
        Duration.ofSeconds(5), "test:openfga");

    assertTrue(adapter.check("user:alice", "can_view", "resource:page/hr"));
    var options = org.mockito.ArgumentCaptor.forClass(ClientCheckOptions.class);
    verify(client).check(org.mockito.ArgumentMatchers.any(), options.capture());
    assertTrue(options.getValue().getConsistency() == ConsistencyPreference.HIGHER_CONSISTENCY);
  }

  @Test
  void returnsCachedDecisionWithoutCallingOpenFga() {
    OpenFgaClient client = mock(OpenFgaClient.class);
    StringRedisTemplate redis = mock(StringRedisTemplate.class);
    @SuppressWarnings("unchecked")
    ValueOperations<String, String> values = mock(ValueOperations.class);
    when(redis.opsForValue()).thenReturn(values);
    when(values.get("test:openfga:graph-epoch")).thenReturn("7");
    when(values.get(org.mockito.ArgumentMatchers.startsWith("test:openfga:7:"))).thenReturn("1");

    var adapter = new OpenFgaRelationshipAdapter(client, redis,
        Duration.ofSeconds(5), "test:openfga");

    assertTrue(adapter.check("user:alice", "can_view", "application:aurevia/hr"));
    verifyNoInteractions(client);
  }

  @Test
  void preservesTheOpenFgaCauseForOutboxDiagnostics() throws Exception {
    OpenFgaClient client = mock(OpenFgaClient.class);
    StringRedisTemplate redis = mock(StringRedisTemplate.class);
    @SuppressWarnings("unchecked")
    ValueOperations<String, String> values = mock(ValueOperations.class);
    when(redis.opsForValue()).thenReturn(values);
    when(values.increment("test:openfga:graph-epoch")).thenReturn(8L);
    when(client.writeTuples(org.mockito.ArgumentMatchers.anyList()))
        .thenReturn(CompletableFuture.failedFuture(new IllegalArgumentException("invalid tuple format")));

    var adapter = new OpenFgaRelationshipAdapter(client, redis,
        Duration.ofSeconds(5), "test:openfga");

    var failure = assertThrows(IllegalStateException.class,
        () -> adapter.write("user:alice", "viewer", "resource:page/hr"));
    assertTrue(failure.getMessage().contains("invalid tuple format"));
  }

  @Test
  void treatsDeletingAnAlreadyAbsentTupleAsSuccess() throws Exception {
    OpenFgaClient client=mock(OpenFgaClient.class);
    StringRedisTemplate redis=mock(StringRedisTemplate.class);
    ClientReadResponse response=mock(ClientReadResponse.class);
    when(response.getTuples()).thenReturn(java.util.List.of());
    when(client.read(org.mockito.ArgumentMatchers.any(ClientReadRequest.class)))
        .thenReturn(CompletableFuture.completedFuture(response));

    var adapter=new OpenFgaRelationshipAdapter(client,redis,Duration.ofSeconds(5),"test:openfga");

    adapter.delete("user:alice","viewer","resource:page/hr");

    verify(client,never()).deleteTuples(org.mockito.ArgumentMatchers.anyList());
  }

  @Test
  void acceptsAConcurrentDeleteWhenTheTupleIsAbsentAfterTheFailure() throws Exception {
    OpenFgaClient client=mock(OpenFgaClient.class);
    StringRedisTemplate redis=mock(StringRedisTemplate.class);
    @SuppressWarnings("unchecked")
    ValueOperations<String,String> values=mock(ValueOperations.class);
    when(redis.opsForValue()).thenReturn(values);
    when(values.increment("test:openfga:graph-epoch")).thenReturn(8L,9L);
    ClientReadResponse present=mock(ClientReadResponse.class);
    ClientReadResponse absent=mock(ClientReadResponse.class);
    when(present.getTuples()).thenReturn(java.util.List.of(mock(Tuple.class)));
    when(absent.getTuples()).thenReturn(java.util.List.of());
    when(client.read(org.mockito.ArgumentMatchers.any(ClientReadRequest.class)))
        .thenReturn(CompletableFuture.completedFuture(present),
            CompletableFuture.completedFuture(absent));
    when(client.deleteTuples(org.mockito.ArgumentMatchers.anyList()))
        .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("tuple not found")));

    var adapter=new OpenFgaRelationshipAdapter(client,redis,Duration.ofSeconds(5),"test:openfga");

    adapter.delete("user:alice","viewer","resource:page/hr");

    verify(client).deleteTuples(org.mockito.ArgumentMatchers.anyList());
  }
}

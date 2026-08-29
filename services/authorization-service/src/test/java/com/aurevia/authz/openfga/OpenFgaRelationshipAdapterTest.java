package com.aurevia.authz.openfga;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import dev.openfga.sdk.api.client.OpenFgaClient;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

class OpenFgaRelationshipAdapterTest {
  @Test
  void returnsCachedDecisionWithoutCallingOpenFga() {
    OpenFgaClient client = mock(OpenFgaClient.class);
    StringRedisTemplate redis = mock(StringRedisTemplate.class);
    @SuppressWarnings("unchecked")
    ValueOperations<String, String> values = mock(ValueOperations.class);
    when(redis.opsForValue()).thenReturn(values);
    when(values.get(anyString())).thenReturn("1");

    var adapter = new OpenFgaRelationshipAdapter(client, redis,
        Duration.ofSeconds(5), "test:openfga");

    assertTrue(adapter.check("user:alice", "can_view", "application:aurevia/hr"));
    verifyNoInteractions(client);
  }
}

package com.aurevia.bff.outboundauth;
import java.time.Duration;import java.util.UUID;import java.util.function.Supplier;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;import org.springframework.stereotype.Component;import reactor.core.publisher.Mono;import reactor.util.retry.Retry;
@Component class LegacyTokenRefreshCoordinator {
 private final ReactiveStringRedisTemplate redis;LegacyTokenRefreshCoordinator(ReactiveStringRedisTemplate redis){this.redis=redis;}
 <T> Mono<T> once(String lockKey,Supplier<Mono<T>> afterLock,Supplier<Mono<T>> reread){String owner=UUID.randomUUID().toString();return redis.opsForValue().setIfAbsent(lockKey,owner,Duration.ofSeconds(15)).flatMap(acquired->Boolean.TRUE.equals(acquired)?afterLock.get().flatMap(value->release(lockKey,owner).thenReturn(value)).onErrorResume(failure->release(lockKey,owner).then(Mono.error(failure))):reread.get().switchIfEmpty(Mono.error(new Waiting())).retryWhen(Retry.fixedDelay(20,Duration.ofMillis(100)).filter(Waiting.class::isInstance))).timeout(Duration.ofSeconds(5));}
 private Mono<Void> release(String key,String owner){String script="if redis.call('get',KEYS[1])==ARGV[1] then return redis.call('del',KEYS[1]) else return 0 end";return redis.execute(new org.springframework.data.redis.core.script.DefaultRedisScript<>(script,Long.class),java.util.List.of(key),java.util.List.of(owner)).then();}
 private static class Waiting extends RuntimeException{}
}

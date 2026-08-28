package com.aurevia.bff.proxy;
import static org.junit.jupiter.api.Assertions.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
class ProxyRetryPolicyTest {
 @Test void refreshesAndRetriesOnlyOnceOn401(){var calls=new AtomicInteger();var refreshes=new AtomicInteger();var result=ProxyRetryPolicy.execute(()->Mono.just(new ProxyRetryPolicy.Response<>(calls.getAndIncrement()==0?HttpStatus.UNAUTHORIZED:HttpStatus.OK,"ok")),()->{refreshes.incrementAndGet();return Mono.empty();});StepVerifier.create(result).expectNext("ok").verifyComplete();assertEquals(2,calls.get());assertEquals(1,refreshes.get());}
 @Test void neverRefreshes403(){var refreshes=new AtomicInteger();var result=ProxyRetryPolicy.execute(()->Mono.just(new ProxyRetryPolicy.Response<String>(HttpStatus.FORBIDDEN,null)),()->{refreshes.incrementAndGet();return Mono.empty();});StepVerifier.create(result).expectError(ProxyRetryPolicy.ProxyStatusException.class).verify();assertEquals(0,refreshes.get());}
 @Test void second401IsReturnedWithoutAnotherRefresh(){var refreshes=new AtomicInteger();var result=ProxyRetryPolicy.execute(()->Mono.just(new ProxyRetryPolicy.Response<String>(HttpStatus.UNAUTHORIZED,null)),()->{refreshes.incrementAndGet();return Mono.empty();});StepVerifier.create(result).expectError(ProxyRetryPolicy.ProxyStatusException.class).verify();assertEquals(1,refreshes.get());}
}

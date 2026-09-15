package com.aurevia.bff.security;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/** Collapses concurrent refreshes for one vault handle into a single upstream call. */
@Component public class RefreshCoordinator {
  private final ConcurrentHashMap<String,Mono<TokenVaultService.Tokens>> active=new ConcurrentHashMap<>();
  public Mono<TokenVaultService.Tokens> once(String handle,Supplier<Mono<TokenVaultService.Tokens>> refresh){
    return active.computeIfAbsent(handle,k->refresh.get().cache().doFinally(s->active.remove(k))).hide();
  }
}

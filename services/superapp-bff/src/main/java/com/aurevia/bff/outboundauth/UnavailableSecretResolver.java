package com.aurevia.bff.outboundauth;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.core.publisher.Mono;
@Configuration class UnavailableSecretResolver {
 @Bean @ConditionalOnMissingBean(SecretResolver.class) SecretResolver disabled(){return ref->Mono.error(new IllegalStateException("Legacy Secret Store is not configured"));}
}

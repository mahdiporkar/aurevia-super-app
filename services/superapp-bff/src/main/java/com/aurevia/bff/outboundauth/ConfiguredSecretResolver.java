package com.aurevia.bff.outboundauth;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
/** Fake local-development resolver. It is unavailable unless explicitly enabled. */
@Component @ConditionalOnProperty(name="aurevia.legacy.local-secrets.enabled",havingValue="true")
class ConfiguredSecretResolver implements SecretResolver {
 private final Map<String,Map<String,String>> values;
 ConfiguredSecretResolver(ObjectMapper json,@Value("${aurevia.legacy.local-secrets.json:{}}") String source){try{values=json.readValue(source,json.getTypeFactory().constructMapType(Map.class,String.class,Map.class));}catch(Exception e){throw new IllegalStateException("Invalid local secret configuration");}}
 public Mono<ResolvedSecret> resolve(SecretReference ref){Map<String,String> v=values.get(ref.value());if(v==null)return Mono.error(new IllegalStateException("Secret unavailable"));return Mono.just(new ResolvedSecret(v.get("username"),v.get("password"),v.get("clientId"),v.get("clientSecret"),v.getOrDefault("version","1")));}
}

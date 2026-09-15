package com.aurevia.bff.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/** Resolves secret:// references without storing credential values in the registry. */
@Component
class IdentityProviderSecretResolver {
  private static final long MAX_BYTES=64*1024;
  private final ObjectMapper json;
  private final boolean localEnabled;
  private final Map<String,String> local;
  private final String configuredRoot;
  IdentityProviderSecretResolver(ObjectMapper json,
      @Value("${aurevia.identity-providers.local-secrets.enabled:false}") boolean localEnabled,
      @Value("${aurevia.identity-providers.local-secrets.json:{}}") String localJson,
      @Value("${aurevia.identity-providers.file-secrets.root:}") String root){
    this.json=json;this.localEnabled=localEnabled;this.configuredRoot=root;
    try{this.local=json.readValue(localJson,json.getTypeFactory().constructMapType(
        Map.class,String.class,String.class));}
    catch(Exception invalid){throw new IllegalStateException("Invalid local identity secret configuration");}
  }
  Mono<String> resolve(String reference){
    if(reference==null||!reference.matches("secret://[A-Za-z0-9._/-]+"))
      return Mono.error(new IllegalArgumentException("Invalid identity provider secret reference"));
    if(localEnabled){String value=local.get(reference);return value==null||value.isBlank()
        ?Mono.error(new IllegalStateException("Identity provider secret unavailable")):Mono.just(value);}
    return Mono.fromCallable(()->readFile(reference)).subscribeOn(Schedulers.boundedElastic());
  }
  private String readFile(String reference)throws Exception{
    if(configuredRoot==null||configuredRoot.isBlank())throw new IllegalStateException(
        "Identity provider secret store is not configured");
    Path root=Path.of(configuredRoot).toRealPath();
    String relative=reference.substring("secret://".length())+".json";
    Path candidate=root.resolve(relative).normalize();
    if(!candidate.startsWith(root))throw new IllegalArgumentException("Invalid secret reference");
    Path real=candidate.toRealPath();
    if(!real.startsWith(root)||!Files.isRegularFile(real)||Files.size(real)>MAX_BYTES)
      throw new IllegalStateException("Identity provider secret unavailable");
    Map<?,?> secret=json.readValue(Files.readString(real,StandardCharsets.UTF_8),Map.class);
    Object value=secret.get("clientSecret");
    if(value==null||String.valueOf(value).isBlank())throw new IllegalStateException(
        "Identity provider clientSecret is unavailable");
    return String.valueOf(value);
  }
}

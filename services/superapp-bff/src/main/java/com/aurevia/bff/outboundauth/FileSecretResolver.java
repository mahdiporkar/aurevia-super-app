package com.aurevia.bff.outboundauth;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/** Production adapter for a read-only, externally mounted secret volume. */
@Component
@ConditionalOnProperty(name="aurevia.legacy.file-secrets.enabled",havingValue="true")
final class FileSecretResolver implements SecretResolver {
  private static final long MAX_SECRET_BYTES=64*1024;
  private final Path root;
  private final ObjectMapper json;

  FileSecretResolver(ObjectMapper json,@Value("${aurevia.legacy.file-secrets.root}") String root) {
    this.json=json;
    try {
      this.root=Path.of(root).toRealPath();
      if(!Files.isDirectory(this.root)) throw new IllegalStateException("Secret root is not a directory");
    } catch(Exception error) {
      throw new IllegalStateException("Legacy secret volume is unavailable",error);
    }
  }

  @Override
  public Mono<ResolvedSecret> resolve(SecretReference reference) {
    return Mono.fromCallable(()->read(reference)).subscribeOn(Schedulers.boundedElastic());
  }

  private ResolvedSecret read(SecretReference reference) throws Exception {
    String relative=reference.value().substring("secret://".length())+".json";
    Path candidate=root.resolve(relative).normalize();
    if(!candidate.startsWith(root)) throw new IllegalArgumentException("Invalid secret reference");
    Path real=candidate.toRealPath();
    if(!real.startsWith(root) || !Files.isRegularFile(real) || Files.size(real)>MAX_SECRET_BYTES) {
      throw new IllegalStateException("Secret unavailable");
    }
    Map<?,?> value=json.readValue(Files.readString(real,StandardCharsets.UTF_8),Map.class);
    String version=string(value,"version");
    if(version==null || version.isBlank()) throw new IllegalStateException("Secret version is required");
    return new ResolvedSecret(string(value,"username"),string(value,"password"),
        string(value,"clientId"),string(value,"clientSecret"),version);
  }

  private static String string(Map<?,?> value,String key) {
    Object item=value.get(key);
    return item==null?null:String.valueOf(item);
  }
}

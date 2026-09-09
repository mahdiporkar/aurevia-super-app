package com.aurevia.authz.registry;

import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.springframework.stereotype.Component;

/** Bounded manifest HTTP adapter. Redirects are disabled to prevent allowlist bypasses. */
@Component
final class HttpManifestFetcher implements ResourceManifestFetcher {
  private static final int MAX_BYTES=1_048_576;
  private final HttpClient client=HttpClient.newBuilder()
      .connectTimeout(Duration.ofSeconds(5))
      .followRedirects(HttpClient.Redirect.NEVER)
      .build();

  @Override public String fetch(String url) {
    try {
      HttpRequest request=HttpRequest.newBuilder(URI.create(url))
          .timeout(Duration.ofSeconds(10))
          .header("Accept","application/json")
          .GET().build();
      HttpResponse<InputStream> response=client.send(request,HttpResponse.BodyHandlers.ofInputStream());
      if(response.statusCode()<200||response.statusCode()>=300) {
        response.body().close();
        throw new IllegalArgumentException("manifest endpoint returned HTTP "+response.statusCode());
      }
      String contentType=response.headers().firstValue("Content-Type").orElse("");
      if(!contentType.isBlank()&&!contentType.toLowerCase().contains("json")) {
        response.body().close();
        throw new IllegalArgumentException("manifest endpoint must return JSON");
      }
      try(InputStream body=response.body()) {
        byte[] bytes=body.readNBytes(MAX_BYTES+1);
        if(bytes.length>MAX_BYTES)throw new IllegalArgumentException("manifest exceeds 1 MiB limit");
        return new String(bytes,StandardCharsets.UTF_8);
      }
    } catch(IllegalArgumentException failure) {
      throw failure;
    } catch(InterruptedException failure) {
      Thread.currentThread().interrupt();
      throw new IllegalArgumentException("manifest fetch was interrupted",failure);
    } catch(Exception failure) {
      throw new IllegalArgumentException("unable to fetch manifest",failure);
    }
  }
}

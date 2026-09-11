package com.aurevia.authz.identityprovider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

interface IdentityProviderHealthProbe { void verify(String jwksUri); }

@Component
class HttpIdentityProviderHealthProbe implements IdentityProviderHealthProbe {
  private final HttpClient client;
  private final ObjectMapper json;
  private final Duration timeout;
  HttpIdentityProviderHealthProbe(ObjectMapper json,
      @Value("${aurevia.identity-providers.health-timeout:5s}") Duration timeout){
    this.json=json;this.timeout=timeout;
    this.client=HttpClient.newBuilder().connectTimeout(timeout)
        .followRedirects(HttpClient.Redirect.NEVER).build();
  }
  @Override public void verify(String jwksUri){
    try{
      var request=HttpRequest.newBuilder(URI.create(jwksUri)).timeout(timeout)
          .header("Accept","application/json").GET().build();
      var response=client.send(request,HttpResponse.BodyHandlers.ofString());
      if(response.statusCode()<200||response.statusCode()>=300||response.body().length()>1_048_576) {
        throw new IllegalStateException("JWKS endpoint rejected the health check");
      }
      JsonNode body=json.readTree(response.body());
      if(!body.path("keys").isArray()||body.path("keys").isEmpty()) {
        throw new IllegalStateException("JWKS document has no signing keys");
      }
    }catch(InterruptedException interrupted){Thread.currentThread().interrupt();
      throw new IllegalStateException("JWKS health check interrupted",interrupted);
    }catch(Exception failure){throw new IllegalStateException("JWKS health check failed",failure);}
  }
}

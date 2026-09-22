package com.aurevia.bff.identity;

import com.fasterxml.jackson.databind.JsonNode;
import io.netty.channel.ChannelOption;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeoutException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;
import static com.aurevia.bff.identity.KeycloakUserModels.*;

/**
 * Server-side Keycloak Admin REST API integration for basic user creation.
 *
 * <p>The administrative service account (client credentials) is a different Keycloak client from
 * the primary login client. Its secret, the obtained admin token and the initial password only
 * travel between this service and Keycloak; none of them is logged, persisted or returned.</p>
 */
@Service
public class KeycloakAdminUserService {
  private final ClientRegistration primary;
  private final WebClient client;
  private final String clientId;
  private final String clientSecret;
  private final String baseUrl;

  @Autowired
  public KeycloakAdminUserService(
      @Qualifier("primaryClientRegistration") ClientRegistration primary,
      @Value("${aurevia.keycloak-admin.client-id:}") String clientId,
      @Value("${aurevia.keycloak-admin.client-secret:}") String clientSecret,
      @Value("${aurevia.keycloak-admin.base-url:}") String baseUrl) {
    this(primary, clientId, clientSecret, baseUrl, WebClient.builder()
        .clientConnector(new ReactorClientHttpConnector(HttpClient.create()
            .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 5000)
            .responseTimeout(Duration.ofSeconds(10)).followRedirect(false)))
        .codecs(codecs -> codecs.defaultCodecs().maxInMemorySize(65536)).build());
  }

  KeycloakAdminUserService(ClientRegistration primary, String clientId,
      String clientSecret, String baseUrl, WebClient client) {
    this.primary=primary;
    this.clientId=clientId==null?"":clientId.trim();
    this.clientSecret=clientSecret==null?"":clientSecret;
    this.baseUrl=baseUrl==null?"":baseUrl.trim();
    this.client=client;
  }

  /** True when a service account is configured; used for diagnostics only, never exposes values. */
  public boolean configured() { return !clientId.isBlank() && !clientSecret.isBlank(); }

  public Mono<CreatedUser> create(CreateUser user) {
    return Mono.defer(() -> {
      if (!configured()) return Mono.error(failure(
          HttpStatus.SERVICE_UNAVAILABLE, "KEYCLOAK_ADMIN_NOT_CONFIGURED",
          "Keycloak user administration requires KEYCLOAK_ADMIN_CLIENT_ID and KEYCLOAK_ADMIN_CLIENT_SECRET."));
      // The login client and the administrative service account must stay separate clients.
      if (clientId.equals(primary.getClientId())) return Mono.error(failure(
          HttpStatus.SERVICE_UNAVAILABLE, "KEYCLOAK_ADMIN_CLIENT_NOT_SEPARATE",
          "Keycloak administration requires a dedicated service-account client."));
      String usersUrl=usersUrl(primary.getProviderDetails().getIssuerUri());
      return token(primary.getProviderDetails().getTokenUri())
          .flatMap(token -> client.post().uri(usersUrl).headers(h -> h.setBearerAuth(token))
              .contentType(MediaType.APPLICATION_JSON).bodyValue(Map.of(
                  "username",user.username(),"firstName",user.firstName(),
                  "lastName",user.lastName(),"email",user.email(),"enabled",user.enabled(),
                  "credentials",List.of(Map.of("type","password","temporary",false,
                      "value",user.initialPassword()))))
              .exchangeToMono(response -> created(response,user)));
    }).timeout(Duration.ofSeconds(20))
      .onErrorMap(TimeoutException.class, error -> failure(HttpStatus.GATEWAY_TIMEOUT,
          "KEYCLOAK_TIMEOUT", "Keycloak did not respond in time. Check whether the user was created before retrying."))
      .onErrorMap(WebClientRequestException.class, error -> failure(HttpStatus.SERVICE_UNAVAILABLE,
          "KEYCLOAK_UNAVAILABLE", "Keycloak is unavailable. Check whether the user was created before retrying."))
      .onErrorMap(error -> !(error instanceof KeycloakAdminException), error -> failure(
          HttpStatus.BAD_GATEWAY, "KEYCLOAK_INVALID_RESPONSE", "Keycloak returned an unexpected response."));
  }

  /** Existence and username of a Keycloak user by stable id; used only for startup diagnostics. */
  public Mono<KeycloakUser> lookup(String userId) {
    return Mono.defer(() -> {
      if (!configured()) return Mono.error(failure(HttpStatus.SERVICE_UNAVAILABLE,
          "KEYCLOAK_ADMIN_NOT_CONFIGURED", "Keycloak administration is not configured."));
      if (userId == null || !userId.matches("[A-Za-z0-9_.-]{1,255}")) return Mono.error(failure(
          HttpStatus.BAD_REQUEST, "KEYCLOAK_USER_ID_INVALID", "The Keycloak user id has an unexpected format."));
      String url=usersUrl(primary.getProviderDetails().getIssuerUri())+"/"+userId;
      return token(primary.getProviderDetails().getTokenUri()).flatMap(token -> client.get().uri(url)
          .headers(h -> h.setBearerAuth(token)).exchangeToMono(response -> {
            if (response.statusCode().value()==404) return response.releaseBody().then(Mono.error(failure(
                HttpStatus.NOT_FOUND, "KEYCLOAK_USER_NOT_FOUND", "No Keycloak user has this id in the realm.")));
            if (!response.statusCode().is2xxSuccessful()) return reject(response,false);
            return response.bodyToMono(JsonNode.class).map(json -> new KeycloakUser(
                json.path("id").asText(""), json.path("username").asText(""), json.path("enabled").asBoolean(false)));
          }));
    }).timeout(Duration.ofSeconds(20))
      .onErrorMap(TimeoutException.class, error -> failure(HttpStatus.GATEWAY_TIMEOUT,
          "KEYCLOAK_TIMEOUT", "Keycloak did not respond in time."))
      .onErrorMap(WebClientRequestException.class, error -> failure(HttpStatus.SERVICE_UNAVAILABLE,
          "KEYCLOAK_UNAVAILABLE", "Keycloak is unavailable."))
      .onErrorMap(error -> !(error instanceof KeycloakAdminException), error -> failure(
          HttpStatus.BAD_GATEWAY, "KEYCLOAK_INVALID_RESPONSE", "Keycloak returned an unexpected response."));
  }

  private Mono<String> token(String tokenUrl) {
    var form=new LinkedMultiValueMap<String,String>();
    form.add("grant_type","client_credentials");
    form.add("client_id",clientId);
    form.add("client_secret",clientSecret);
    return client.post().uri(tokenUrl).contentType(MediaType.APPLICATION_FORM_URLENCODED)
        .bodyValue(form).exchangeToMono(response -> {
          if (!response.statusCode().is2xxSuccessful()) return reject(response,true);
          return response.bodyToMono(JsonNode.class).flatMap(json -> {
            String accessToken=json.path("access_token").asText("");
            if (accessToken.isBlank()) return Mono.error(failure(HttpStatus.BAD_GATEWAY,
                "KEYCLOAK_INVALID_RESPONSE", "Keycloak did not return an administrative access token."));
            return Mono.just(accessToken);
          }).switchIfEmpty(Mono.error(failure(HttpStatus.BAD_GATEWAY,
              "KEYCLOAK_INVALID_RESPONSE", "Keycloak returned an empty token response.")));
        });
  }

  private Mono<CreatedUser> created(ClientResponse response, CreateUser user) {
    if (response.statusCode().value()!=201) return reject(response,false);
    URI location=response.headers().asHttpHeaders().getLocation();
    String path=location==null?null:location.getPath();
    // Parse only the stable identifier. Never follow a Location supplied by the upstream.
    if(path==null || !path.matches(".*/users/[A-Za-z0-9_-]{1,255}")) {
      return response.releaseBody().then(Mono.error(failure(HttpStatus.BAD_GATEWAY,
          "KEYCLOAK_INVALID_RESPONSE", "Keycloak created the user without a valid user ID. Verify the user before retrying.")));
    }
    String id=path.substring(path.lastIndexOf('/')+1);
    return response.releaseBody().thenReturn(new CreatedUser(id,user.username(),user.firstName(),
        user.lastName(),user.email(),user.enabled()));
  }

  private <T> Mono<T> reject(ClientResponse response, boolean tokenRequest) {
    int status=response.statusCode().value();
    KeycloakAdminException error;
    if (status==401 || status==403 || (tokenRequest && status==400)) error=failure(
        HttpStatus.BAD_GATEWAY, "KEYCLOAK_ADMIN_ACCESS_DENIED",
        "Keycloak rejected the administrative service account. Verify its credentials and realm-management manage-users permission.");
    else if (status==409 && !tokenRequest) error=failure(HttpStatus.CONFLICT,
        "KEYCLOAK_USER_CONFLICT", "The username or email already exists in Keycloak.");
    else if ((status==400 || status==422) && !tokenRequest) error=failure(HttpStatus.BAD_REQUEST,
        "KEYCLOAK_USER_REJECTED", "Keycloak rejected the user details or initial password. Check the email, required fields, and realm password policy.");
    else if (status==429 || status>=500) error=failure(HttpStatus.SERVICE_UNAVAILABLE,
        "KEYCLOAK_UNAVAILABLE", "Keycloak is temporarily unavailable. Verify the user before retrying.");
    else error=failure(HttpStatus.BAD_GATEWAY, "KEYCLOAK_INVALID_RESPONSE",
        "Keycloak returned an unexpected response. Verify the realm and administrative service-account configuration.");
    return response.releaseBody().then(Mono.error(error));
  }

  private String usersUrl(String issuerValue) {
    try {
      URI issuer=URI.create(issuerValue);
      String path=issuer.getRawPath();
      int realmIndex=path.lastIndexOf("/realms/");
      if (realmIndex<0 || !path.substring(realmIndex+8).matches("[A-Za-z0-9._~-]+"))
        throw new IllegalArgumentException();
      String base=baseUrl.isBlank()
          ? issuer.getScheme()+"://"+issuer.getRawAuthority()+path.substring(0,realmIndex)
          : baseUrl.replaceAll("/+$", "");
      URI configured=URI.create(base);
      if (!List.of("http","https").contains(configured.getScheme()) || configured.getHost()==null
          || configured.getUserInfo()!=null || configured.getQuery()!=null || configured.getFragment()!=null)
        throw new IllegalArgumentException();
      return base+"/admin/realms/"+path.substring(realmIndex+8)+"/users";
    } catch(RuntimeException invalid) {
      throw failure(HttpStatus.SERVICE_UNAVAILABLE, "KEYCLOAK_ADMIN_INVALID_CONFIGURATION",
          "Keycloak administration requires a Keycloak realm issuer and a valid KEYCLOAK_ADMIN_BASE_URL when overridden.");
    }
  }

  private static KeycloakAdminException failure(HttpStatus status,String code,String message) {
    return new KeycloakAdminException(status,code,message);
  }
}

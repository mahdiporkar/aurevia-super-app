package com.aurevia.bff.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static com.aurevia.bff.identity.KeycloakUserModels.*;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/** Fake Keycloak over real HTTP: token endpoint plus the admin users endpoint. */
class KeycloakAdminUserServiceTest {
  private static final String USER_ID="8c604f37-33d2-42e4-a982-35bd5613e974";
  private HttpServer keycloak;
  private String base;
  private final AtomicInteger tokenStatus=new AtomicInteger(200);
  private final AtomicInteger usersStatus=new AtomicInteger(201);
  private final AtomicReference<String> tokenBody=new AtomicReference<>("{\"access_token\":\"admin-token-value\"}");
  private final List<String> tokenRequests=new CopyOnWriteArrayList<>();
  private final List<String> userRequests=new CopyOnWriteArrayList<>();
  private final List<String> authorizationHeaders=new CopyOnWriteArrayList<>();

  @BeforeEach void start() throws IOException {
    keycloak=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
    base="http://127.0.0.1:"+keycloak.getAddress().getPort();
    keycloak.createContext("/realms/aurevia/protocol/openid-connect/token",exchange->{
      tokenRequests.add(read(exchange));
      respond(exchange,tokenStatus.get(),tokenBody.get());
    });
    keycloak.createContext("/admin/realms/aurevia/users",exchange->{
      userRequests.add(read(exchange));
      authorizationHeaders.add(exchange.getRequestHeaders().getFirst("Authorization"));
      if(usersStatus.get()==201)exchange.getResponseHeaders().set("Location",base+"/admin/realms/aurevia/users/"+USER_ID);
      respond(exchange,usersStatus.get(),usersStatus.get()==201?"":"{\"errorMessage\":\"upstream detail must not leak\"}");
    });
    keycloak.start();
  }
  @AfterEach void stop(){keycloak.stop(0);}

  @Test void createsUserThroughServiceAccountAndReturnsOnlySafeFields(){
    StepVerifier.create(service("aurevia-identity-admin","admin-secret").create(request()))
        .assertNext(created->{
          assertThat(created.id()).isEqualTo(USER_ID);
          assertThat(created.username()).isEqualTo("new.user");
          assertThat(created.toString()).doesNotContain("Secret-Pass-1","admin-secret","admin-token-value");
        }).verifyComplete();
    assertThat(tokenRequests).singleElement().satisfies(body->assertThat(body)
        .contains("grant_type=client_credentials","client_id=aurevia-identity-admin","client_secret=admin-secret"));
    assertThat(authorizationHeaders).containsExactly("Bearer admin-token-value");
    assertThat(userRequests).singleElement().satisfies(body->assertThat(body)
        .contains("\"username\":\"new.user\"","\"enabled\":true","\"value\":\"Secret-Pass-1\"","\"temporary\":false"));
  }

  @Test void duplicateUsernameOrEmailBecomesConflictWithoutUpstreamDetails(){
    usersStatus.set(409);
    expect(service("aurevia-identity-admin","admin-secret").create(request()),HttpStatus.CONFLICT,"KEYCLOAK_USER_CONFLICT");
  }

  @Test void invalidPayloadOrPasswordPolicyBecomesBadRequest(){
    usersStatus.set(400);
    expect(service("aurevia-identity-admin","admin-secret").create(request()),HttpStatus.BAD_REQUEST,"KEYCLOAK_USER_REJECTED");
  }

  @Test void serviceAccountRejectedByKeycloakIsReportedClearly(){
    tokenStatus.set(401);
    expect(service("aurevia-identity-admin","wrong").create(request()),HttpStatus.BAD_GATEWAY,"KEYCLOAK_ADMIN_ACCESS_DENIED");
    assertThat(userRequests).isEmpty();
    tokenStatus.set(200);usersStatus.set(403);
    expect(service("aurevia-identity-admin","admin-secret").create(request()),HttpStatus.BAD_GATEWAY,"KEYCLOAK_ADMIN_ACCESS_DENIED");
  }

  @Test void keycloakOutageIsAnUpstreamError(){
    usersStatus.set(503);
    expect(service("aurevia-identity-admin","admin-secret").create(request()),HttpStatus.SERVICE_UNAVAILABLE,"KEYCLOAK_UNAVAILABLE");
    keycloak.stop(0);
    expect(service("aurevia-identity-admin","admin-secret").create(request()),HttpStatus.SERVICE_UNAVAILABLE,"KEYCLOAK_UNAVAILABLE");
  }

  @Test void tokenWithoutAccessTokenOrCreatedWithoutStableIdIsAnInvalidResponse(){
    tokenBody.set("{\"token_type\":\"Bearer\"}");
    expect(service("aurevia-identity-admin","admin-secret").create(request()),HttpStatus.BAD_GATEWAY,"KEYCLOAK_INVALID_RESPONSE");
  }

  @Test void missingOrSharedServiceAccountConfigurationFailsBeforeAnyRequest(){
    expect(service("","").create(request()),HttpStatus.SERVICE_UNAVAILABLE,"KEYCLOAK_ADMIN_NOT_CONFIGURED");
    expect(service("aurevia-bff","login-secret").create(request()),HttpStatus.SERVICE_UNAVAILABLE,"KEYCLOAK_ADMIN_CLIENT_NOT_SEPARATE");
    assertThat(tokenRequests).isEmpty();
    assertThat(userRequests).isEmpty();
  }

  @Test void issuerWithoutRealmPathIsAConfigurationError(){
    var registration=registration("https://login.example/not-a-realm",base+"/realms/aurevia/protocol/openid-connect/token");
    var service=new KeycloakAdminUserService(registration,"aurevia-identity-admin","admin-secret","",WebClient.create());
    expect(service.create(request()),HttpStatus.SERVICE_UNAVAILABLE,"KEYCLOAK_ADMIN_INVALID_CONFIGURATION");
  }

  @Test void lookupResolvesStableIdsAndReportsUnknownIdsClearly(){
    keycloak.createContext("/admin/realms/aurevia/users/"+USER_ID,exchange->{
      authorizationHeaders.add(exchange.getRequestHeaders().getFirst("Authorization"));
      respond(exchange,200,"{\"id\":\""+USER_ID+"\",\"username\":\"administrator\",\"enabled\":true}");
    });
    keycloak.createContext("/admin/realms/aurevia/users/missing-id",exchange->respond(exchange,404,"{\"error\":\"User not found\"}"));
    StepVerifier.create(service("aurevia-identity-admin","admin-secret").lookup(USER_ID))
        .assertNext(user->{assertThat(user.username()).isEqualTo("administrator");assertThat(user.enabled()).isTrue();})
        .verifyComplete();
    assertThat(authorizationHeaders).containsExactly("Bearer admin-token-value");
    StepVerifier.create(service("aurevia-identity-admin","admin-secret").lookup("missing-id"))
        .expectErrorSatisfies(error->assertThat(((KeycloakAdminException)error).code()).isEqualTo("KEYCLOAK_USER_NOT_FOUND")).verify();
    StepVerifier.create(service("aurevia-identity-admin","admin-secret").lookup("../users"))
        .expectErrorSatisfies(error->assertThat(((KeycloakAdminException)error).code()).isEqualTo("KEYCLOAK_USER_ID_INVALID")).verify();
  }

  private void expect(Mono<CreatedUser> result,HttpStatus status,String code){
    StepVerifier.create(result).expectErrorSatisfies(error->{
      assertThat(error).isInstanceOf(KeycloakAdminException.class);
      var failure=(KeycloakAdminException)error;
      assertThat(failure.status()).isEqualTo(status);
      assertThat(failure.code()).isEqualTo(code);
      assertThat(failure.getMessage()).doesNotContain("upstream detail","Secret-Pass-1","admin-secret","admin-token-value");
    }).verify();
  }

  private KeycloakAdminUserService service(String clientId,String secret){
    // Issuer host deliberately differs from the reachable base URL, like localhost vs host.docker.internal.
    var registration=registration("http://login.example/realms/aurevia",base+"/realms/aurevia/protocol/openid-connect/token");
    return new KeycloakAdminUserService(registration,clientId,secret,base,WebClient.create());
  }

  private static ClientRegistration registration(String issuer,String tokenUri){
    return ClientRegistration.withRegistrationId("public-iam").clientId("aurevia-bff").clientSecret("login-secret")
        .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
        .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}").issuerUri(issuer)
        .authorizationUri(issuer+"/auth").tokenUri(tokenUri).jwkSetUri(issuer+"/jwks")
        .userNameAttributeName("sub").build();
  }

  private static CreateUser request(){
    return new CreateUser("new.user","New","User","new.user@example.com",true,"Secret-Pass-1");
  }

  private static String read(HttpExchange exchange) throws IOException {
    try(var body=exchange.getRequestBody()){return new String(body.readAllBytes(),StandardCharsets.UTF_8);}
  }

  private static void respond(HttpExchange exchange,int status,String body) throws IOException {
    byte[] bytes=body.getBytes(StandardCharsets.UTF_8);
    exchange.getResponseHeaders().set("Content-Type","application/json");
    exchange.sendResponseHeaders(status,bytes.length==0?-1:bytes.length);
    if(bytes.length>0)try(var out=exchange.getResponseBody()){out.write(bytes);}
    exchange.close();
  }
}

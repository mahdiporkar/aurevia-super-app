package com.aurevia.bff.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aurevia.bff.identity.KeycloakAdminException;
import com.aurevia.bff.identity.KeycloakAdminUserService;
import com.aurevia.bff.identity.KeycloakUserModels.CreateUser;
import com.aurevia.bff.identity.KeycloakUserModels.CreatedUser;
import com.aurevia.bff.security.SessionIdentity;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.WebFilter;
import reactor.core.publisher.Mono;

/** HTTP-level contract of the Create User endpoint: OpenFGA gate, validation and safe output. */
class KeycloakUserControllerTest {
  private static final String PASSWORD="Initial-Pass-9";
  private final KeycloakAdminUserService users=mock(KeycloakAdminUserService.class);
  private final AtomicReference<String> decision=new AtomicReference<>("ALLOW");
  private final List<String> checks=new CopyOnWriteArrayList<>();
  private HttpServer authorization;
  private WebTestClient client;

  @BeforeEach void start() throws IOException {
    authorization=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
    authorization.createContext("/internal/v1/authorize/check",exchange->{
      try(var body=exchange.getRequestBody()){checks.add(new String(body.readAllBytes(),StandardCharsets.UTF_8));}
      byte[] response=("{\"result\":\""+decision.get()+"\"}").getBytes(StandardCharsets.UTF_8);
      exchange.getResponseHeaders().set("Content-Type","application/json");
      exchange.sendResponseHeaders(200,response.length);
      try(var out=exchange.getResponseBody()){out.write(response);}
    });
    authorization.start();
    var controller=new KeycloakUserController(WebClient.builder()
        .baseUrl("http://127.0.0.1:"+authorization.getAddress().getPort()).build(),users);
    client=WebTestClient.bindToController(controller).webFilter(principal()).build();
  }
  @AfterEach void stop(){authorization.stop(0);}

  @Test void authorizedAdministratorCreatesUserAndPasswordNeverReturns(){
    when(users.create(any())).thenReturn(Mono.just(new CreatedUser("kc-id-1","new.user","New","User","new.user@example.com",true)));
    client.post().uri("/api/v1/admin/keycloak-users").contentType(MediaType.APPLICATION_JSON)
        .bodyValue(body()).exchange().expectStatus().isCreated()
        .expectBody(String.class).value(json->{
          assertThat(json).contains("\"id\":\"kc-id-1\"","\"username\":\"new.user\"");
          assertThat(json).doesNotContain(PASSWORD,"initialPassword");
        });
    assertThat(checks).singleElement().satisfies(check->assertThat(check).contains(
        "\"resource\":\"application:aurevia\"","\"action\":\"admin\"","\"subjectId\":\"admin-sub\"",
        "\"issuer\":\"https://issuer.example/realms/aurevia\""));
    verify(users).create(any(CreateUser.class));
  }

  @Test void unauthorizedUserIsRefusedBeforeKeycloakIsContacted(){
    decision.set("DENY");
    client.post().uri("/api/v1/admin/keycloak-users").contentType(MediaType.APPLICATION_JSON)
        .bodyValue(body()).exchange().expectStatus().isForbidden()
        .expectBody().jsonPath("$.code").isEqualTo("ACCESS_DENIED");
    verify(users,never()).create(any());
  }

  @Test void invalidPayloadReportsFieldNamesOnly(){
    client.post().uri("/api/v1/admin/keycloak-users").contentType(MediaType.APPLICATION_JSON)
        .bodyValue(Map.of("username","has space","firstName","","lastName","User","email","not-an-email",
            "enabled",true,"initialPassword",PASSWORD))
        .exchange().expectStatus().isBadRequest()
        .expectBody(String.class).value(json->{
          assertThat(json).contains("\"code\":\"INVALID_USER\"","\"email\"","\"firstName\"","\"username\"");
          assertThat(json).doesNotContain(PASSWORD,"has space","not-an-email");
        });
    verify(users,never()).create(any());
  }

  @Test void keycloakFailuresKeepTheirApplicationLevelStatus(){
    when(users.create(any())).thenReturn(Mono.error(new KeycloakAdminException(HttpStatus.CONFLICT,
        "KEYCLOAK_USER_CONFLICT","The username or email already exists in Keycloak.")));
    client.post().uri("/api/v1/admin/keycloak-users").contentType(MediaType.APPLICATION_JSON)
        .bodyValue(body()).exchange().expectStatus().isEqualTo(HttpStatus.CONFLICT)
        .expectBody().jsonPath("$.code").isEqualTo("KEYCLOAK_USER_CONFLICT");
  }

  @Test void authorizationOutageIsNotTreatedAsPermission(){
    authorization.stop(0);
    client.post().uri("/api/v1/admin/keycloak-users").contentType(MediaType.APPLICATION_JSON)
        .bodyValue(body()).exchange().expectStatus().isEqualTo(HttpStatus.SERVICE_UNAVAILABLE)
        .expectBody().jsonPath("$.code").isEqualTo("AUTHORIZATION_UNAVAILABLE");
    verify(users,never()).create(any());
  }

  private static WebFilter principal(){
    var identity=new SessionIdentity("https://issuer.example/realms/aurevia","admin-sub","administrator");
    return (exchange,chain)->chain.filter(exchange.mutate().principal(Mono.just(identity)).build());
  }

  private static Map<String,Object> body(){
    return Map.of("username","new.user","firstName","New","lastName","User",
        "email","new.user@example.com","enabled",true,"initialPassword",PASSWORD);
  }
}

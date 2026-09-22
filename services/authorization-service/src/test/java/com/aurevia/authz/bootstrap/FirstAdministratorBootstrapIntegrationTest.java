package com.aurevia.authz.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;

import com.aurevia.authz.access.AccessAdministrationService;
import com.aurevia.authz.access.AccessModels.GrantView;
import com.aurevia.authz.api.dto.IdentitySyncDtos.LoginIdentityRequest;
import com.aurevia.authz.identity.IdentitySyncService;
import com.aurevia.authz.support.PermissionStack;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * First Administrator Bootstrap on the real application wiring (PostgreSQL, OpenFGA, Redis):
 * the configured Keycloak sub becomes the canonical subject, receives {@code admin} on
 * {@code application:aurevia} through the ordinary outbox projection, and the bootstrap is a
 * one-time operation that survives restarts, username changes and revocation.
 */
@EnabledIfEnvironmentVariable(named = "AUREVIA_TEST_OPENFGA_URL", matches = ".+")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class FirstAdministratorBootstrapIntegrationTest {
  private static final String ISSUER = "https://idp.bootstrap.test/realms/aurevia";
  private static final String ADMIN_SUB = "8c604f37-33d2-42e4-a982-35bd5613e974";
  private static final Duration WAIT = Duration.ofSeconds(20);
  private static final PermissionStack STACK;

  static {
    try {
      STACK = PermissionStack.configured() ? PermissionStack.provision() : null;
    } catch (Exception failure) {
      throw new IllegalStateException("Permission stack could not be provisioned", failure);
    }
  }

  @DynamicPropertySource
  static void stack(DynamicPropertyRegistry registry) {
    if (STACK == null) return;
    registry.add("spring.datasource.url", () -> STACK.jdbcUrl);
    registry.add("spring.datasource.username", () -> STACK.jdbcUser);
    registry.add("spring.datasource.password", () -> STACK.jdbcPassword);
    registry.add("spring.data.redis.host", () -> STACK.redisHost);
    registry.add("spring.data.redis.port", () -> STACK.redisPort);
    registry.add("spring.data.redis.password", () -> STACK.redisPassword);
    registry.add("aurevia.openfga.base-url", () -> STACK.openFgaUrl);
    registry.add("aurevia.openfga.store-id", () -> STACK.storeId);
    registry.add("aurevia.openfga.model-id", () -> STACK.modelId);
    registry.add("aurevia.openfga.reconcile-on-startup", () -> "false");
    registry.add("aurevia.openfga.cache.ttl", () -> "1s");
    registry.add("aurevia.outbox.interval-ms", () -> "200");
    registry.add("aurevia.internal.username", () -> "bff");
    registry.add("aurevia.internal.password", () -> "test-secret");
    // The production inputs: runtime issuer and the stable Keycloak user id. No password anywhere.
    registry.add("aurevia.primary-identity.issuer", () -> ISSUER);
    registry.add("aurevia.bootstrap.admin-sub", () -> ADMIN_SUB);
  }

  @AfterAll static void tearDown() throws Exception { if (STACK != null) STACK.dropDatabase(); }

  @Autowired JdbcClient database;
  @Autowired AccessAdministrationService access;
  @Autowired IdentitySyncService loginSync;
  @Autowired FirstAdministratorBootstrapService bootstrap;
  @Autowired TestRestTemplate http;
  @LocalServerPort int port;

  @Test @Order(1) void startupProvisionsTheConfiguredSubOnceThroughTheExistingAuthorizationPath() {
    assertThat(completed()).isTrue();
    UUID user = adminUser();
    assertThat(database.sql("select count(*) from external_identity where issuer=:i and subject=:s")
        .param("i", ISSUER).param("s", ADMIN_SUB).query(Long.class).single()).isEqualTo(1);
    List<GrantView> grants = access.grants("USER", user);
    assertThat(grants).singleElement().satisfies(grant -> {
      assertThat(grant.resourceKey()).isEqualTo("application:aurevia");
      assertThat(grant.actionKey()).isEqualTo("admin");
      assertThat(grant.relation()).isEqualTo("manager");
    });
    await(() -> "APPLIED".equals(access.grants("USER", user).getFirst().projectionStatus()));
    await(() -> "ALLOW".equals(decision(ADMIN_SUB, "application:aurevia", "admin")));
    // Root admin inherits into the deployment applications, including the Admin Panel.
    assertThat(decision(ADMIN_SUB, "application:aurevia/admin", "admin")).isEqualTo("ALLOW");
    assertThat(manifestPermissions(ADMIN_SUB).get("application:aurevia")).contains("admin");
    assertThat(database.sql("select count(*) from audit_event where actor_key='FIRST_ADMIN_BOOTSTRAP' and event_type='GRANT_CREATED'")
        .query(Long.class).single()).isGreaterThanOrEqualTo(1);
  }

  @Test @Order(2) void repeatedStartupIsIdempotentAndCreatesNothingNew() {
    UUID user = adminUser();
    long grantsBefore = activeGrants(user);
    long outboxBefore = database.sql("select count(*) from outbox_event").query(Long.class).single();
    for (int restart = 0; restart < 3; restart++) {
      assertThat(bootstrap.provision()).isEqualTo(FirstAdministratorBootstrapService.Outcome.ALREADY_COMPLETED);
    }
    assertThat(adminUser()).isEqualTo(user);
    assertThat(activeGrants(user)).isEqualTo(grantsBefore).isEqualTo(1);
    assertThat(database.sql("select count(*) from outbox_event").query(Long.class).single()).isEqualTo(outboxBefore);
    assertThat(database.sql("select count(*) from schema_version where component='first-administrator'")
        .query(Long.class).single()).isEqualTo(1);
  }

  @Test @Order(3) void usernameAndEmailChangesDoNotChangeTheAuthorizationIdentity() {
    UUID user = adminUser();
    var response = loginSync.sync(new LoginIdentityRequest("public-iam", ISSUER, ADMIN_SUB,
        "renamed.administrator", "Renamed Administrator", "renamed@aurevia.test", List.of(),
        null, null, null, Map.of()));
    assertThat(response.userId()).isEqualTo(user);
    assertThat(database.sql("select username from app_user where id=:id").param("id", user)
        .query(String.class).single()).isEqualTo("renamed.administrator");
    assertThat(database.sql("select count(*) from app_user where issuer=:i and external_id=:s")
        .param("i", ISSUER).param("s", ADMIN_SUB).query(Long.class).single()).isEqualTo(1);
    assertThat(decision(ADMIN_SUB, "application:aurevia", "admin")).isEqualTo("ALLOW");
    // A different Keycloak user who happens to take the old username gains nothing.
    var impostor = loginSync.sync(new LoginIdentityRequest("public-iam", ISSUER, UUID.randomUUID().toString(),
        "administrator", "Administrator", null, List.of(), null, null, null, Map.of()));
    assertThat(impostor.userId()).isNotEqualTo(user);
    assertThat(access.grants("USER", impostor.userId())).isEmpty();
  }

  @Test @Order(4) void revokedBootstrapPermissionIsNotRestoredByLaterStartups() {
    UUID user = adminUser();
    GrantView grant = access.grants("USER", user).getFirst();
    access.revoke(grant.id(), "system-administrator");
    await(() -> "DENY".equals(decision(ADMIN_SUB, "application:aurevia", "admin")));
    assertThat(activeGrants(user)).isZero();

    for (int restart = 0; restart < 2; restart++) {
      assertThat(bootstrap.provision()).isEqualTo(FirstAdministratorBootstrapService.Outcome.ALREADY_COMPLETED);
    }
    assertThat(activeGrants(user)).isZero();
    assertThat(decision(ADMIN_SUB, "application:aurevia", "admin")).isEqualTo("DENY");
    assertThat(manifestPermissions(ADMIN_SUB)).doesNotContainKey("application:aurevia");
  }

  @Test @Order(5) void primaryLoginNeedsNoIdentityProviderRow() {
    assertThat(database.sql("select count(*) from identity_provider where issuer_url=:i")
        .param("i", ISSUER).query(Long.class).single()).isZero();
    var response = loginSync.sync(new LoginIdentityRequest("public-iam", ISSUER, UUID.randomUUID().toString(),
        "someone", "Someone", null, List.of(), null, null, null, Map.of()));
    assertThat(response.userId()).isNotNull();
  }

  // ---------------------------------------------------------------- helpers

  private boolean completed() {
    return database.sql("select count(*) from schema_version where component='first-administrator'")
        .query(Long.class).single() == 1;
  }

  private UUID adminUser() {
    return database.sql("select user_id from external_identity where issuer=:i and subject=:s")
        .param("i", ISSUER).param("s", ADMIN_SUB).query(UUID.class).single();
  }

  private long activeGrants(UUID user) {
    return database.sql("select count(*) from authorization_grant where subject_type='USER' and subject_id=:u and status='ACTIVE'")
        .param("u", user).query(Long.class).single();
  }

  private static void await(Supplier<Boolean> condition) {
    long deadline = System.nanoTime() + WAIT.toNanos();
    while (System.nanoTime() < deadline) {
      if (Boolean.TRUE.equals(condition.get())) return;
      try { Thread.sleep(150); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }
    assertThat(condition.get()).as("condition within " + WAIT).isTrue();
  }

  private HttpHeaders headers() {
    HttpHeaders headers = new HttpHeaders();
    headers.setBasicAuth("bff", "test-secret");
    headers.setContentType(MediaType.APPLICATION_JSON);
    return headers;
  }

  @SuppressWarnings("unchecked")
  private String decision(String subject, String resourceKey, String action) {
    Map<String, Object> body = Map.of("subjectId", subject, "issuer", ISSUER,
        "resource", resourceKey, "action", action, "context", Map.of(),
        "correlationId", UUID.randomUUID().toString());
    Map<String, Object> response = http.postForObject("http://localhost:" + port + "/internal/v1/authorize/check",
        new HttpEntity<>(body, headers()), Map.class);
    return String.valueOf(response.get("result"));
  }

  @SuppressWarnings("unchecked")
  private Map<String, List<String>> manifestPermissions(String subject) {
    Map<String, Object> manifest = http.exchange("http://localhost:" + port + "/internal/v1/subjects/" + subject
        + "/manifest?issuer=" + ISSUER, HttpMethod.GET, new HttpEntity<>(headers()), Map.class).getBody();
    return (Map<String, List<String>>) manifest.get("permissions");
  }
}

package com.aurevia.authz.sync;

import static org.assertj.core.api.Assertions.assertThat;

import com.aurevia.authz.access.AccessAdministrationService;
import com.aurevia.authz.access.AccessModels.GrantCommand;
import com.aurevia.authz.access.AccessModels.GrantView;
import com.aurevia.authz.access.AccessModels.ResourceCommand;
import com.aurevia.authz.api.dto.IdentitySyncDtos.DirectoryGroupRequest;
import com.aurevia.authz.api.dto.IdentitySyncDtos.LoginIdentityRequest;
import com.aurevia.authz.identity.IdentityAdministrationService;
import com.aurevia.authz.identity.IdentityModels.RoleAssignmentCommand;
import com.aurevia.authz.identity.IdentityModels.RoleCommand;
import com.aurevia.authz.identity.IdentitySyncService;
import com.aurevia.authz.superset.SupersetAssetModels.AssetCommand;
import com.aurevia.authz.superset.SupersetAssetService;
import com.aurevia.authz.support.PermissionStack;
import dev.openfga.sdk.api.client.model.ClientTupleKey;
import dev.openfga.sdk.api.client.model.ClientTupleKeyWithoutCondition;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * The complete permission lifecycle on the real application wiring against real PostgreSQL,
 * real OpenFGA and real Redis: Admin service → PostgreSQL → outbox → OpenFGA → decision →
 * {@code /internal/v1/subjects/{subject}/manifest} and {@code /internal/v1/authorize/check}.
 *
 * <p>A permission counts as effective only when the HTTP endpoints the BFF consumes report it.
 * Rows in {@code authorization_grant} are never asserted as success on their own.</p>
 */
@EnabledIfEnvironmentVariable(named = "AUREVIA_TEST_OPENFGA_URL", matches = ".+")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class PermissionLifecycleIntegrationTest {

  private static final String ISSUER = "https://idp.perm.test/realms/perm";
  private static final String ACTOR = "integration-test";
  private static final Duration WAIT = Duration.ofSeconds(20);

  private static final PermissionStack STACK;
  private static final PermissionStack.PausableProxy OPENFGA_PROXY;

  static {
    try {
      if (PermissionStack.configured()) {
        STACK = PermissionStack.provision();
        OPENFGA_PROXY = new PermissionStack.PausableProxy(STACK.openFgaUrl);
      } else {
        STACK = null;
        OPENFGA_PROXY = null;
      }
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
    registry.add("aurevia.openfga.base-url", OPENFGA_PROXY::url);
    registry.add("aurevia.openfga.store-id", () -> STACK.storeId);
    registry.add("aurevia.openfga.model-id", () -> STACK.modelId);
    registry.add("aurevia.openfga.reconcile-on-startup", () -> "false");
    registry.add("aurevia.openfga.cache.ttl", () -> "1s");
    registry.add("aurevia.outbox.interval-ms", () -> "200");
    registry.add("aurevia.outbox.max-attempts", () -> "3");
    registry.add("aurevia.outbox.claim-timeout-seconds", () -> "5");
    registry.add("aurevia.expiration.sweep-interval-ms", () -> "500");
    registry.add("aurevia.internal.username", () -> "bff");
    registry.add("aurevia.internal.password", () -> "test-secret");
  }

  @AfterAll static void tearDown() throws Exception {
    if (OPENFGA_PROXY != null) OPENFGA_PROXY.close();
    if (STACK != null) STACK.dropDatabase();
  }

  @Autowired JdbcClient database;
  @Autowired AccessAdministrationService access;
  @Autowired IdentityAdministrationService identity;
  @Autowired IdentitySyncService loginSync;
  @Autowired SupersetAssetService superset;
  @Autowired OpenFgaReconciliationService reconciliation;
  @Autowired OutboxReconciler outbox;
  @Autowired OutboxMetricsRepository outboxMetrics;
  @Autowired ExpirationSweeper expiration;
  @Autowired TestRestTemplate http;
  @LocalServerPort int port;

  // ---------------------------------------------------------------- direct grants

  @Test void directUserGrantBecomesEffectiveThroughTheOutboxAndAppearsInContext() {
    Catalog catalog = catalog("user-direct");
    Subject alice = login("alice-direct", List.of());

    access.grant(new GrantCommand(null, "USER", alice.userId, catalog.pageA, catalog.view, null),
        ACTOR);

    awaitProjection("USER", alice.userId, catalog.pageAKey, "APPLIED");
    awaitAllowed(alice, catalog.pageAKey, true);
    assertThat(contextPermissions(alice)).containsKey(catalog.pageAKey)
        .doesNotContainKey(catalog.pageBKey);
    assertThat(decision(alice, catalog.pageBKey)).isEqualTo("DENY");
    // The grant on the page is enough: no separate application grant was created.
    assertThat(contextPermissions(alice)).doesNotContainKey("application:aurevia/hr");
  }

  @Test void directoryGroupGrantIsInheritedThroughNamespacedGroupMembership() {
    Catalog catalog = catalog("group-direct");
    Subject bob = login("bob-group", List.of(new DirectoryGroupRequest(
        "cn=finance-" + catalog.suffix, "/finance-" + catalog.suffix, "Finance")));
    UUID groupId = directoryGroupId("cn=finance-" + catalog.suffix);

    access.grant(new GrantCommand(null, "GROUP", groupId, catalog.pageA, catalog.view, null),
        ACTOR);

    awaitProjection("GROUP", groupId, catalog.pageAKey, "APPLIED");
    awaitAllowed(bob, catalog.pageAKey, true);
    assertThat(contextPermissions(bob)).containsKey(catalog.pageAKey);
    assertThat(openFgaTuples(bob.canonical)).contains("group:directory/" + groupId);
  }

  @Test void accessGroupGrantIsInheritedThroughEffectiveMembership() {
    Catalog catalog = catalog("ag-direct");
    Subject carol = login("carol-ag", List.of());
    UUID accessGroup = accessGroupWithMember(agCode("direct", catalog), carol);

    access.grant(new GrantCommand(null, "ACCESS_GROUP", accessGroup, catalog.pageA,
        catalog.view, null), ACTOR);

    awaitProjection("ACCESS_GROUP", accessGroup, catalog.pageAKey, "APPLIED");
    awaitAllowed(carol, catalog.pageAKey, true);
    assertThat(contextPermissions(carol)).containsKey(catalog.pageAKey);
  }

  @Test void roleGrantReachesEveryAssignmentPath() {
    Catalog catalog = catalog("role-paths");
    UUID role = identity.createRole(new RoleCommand("perm-" + catalog.suffix, "نقش", "Role"),
        ACTOR).id();
    access.grant(new GrantCommand(null, "ROLE", role, catalog.pageA, catalog.view, null), ACTOR);
    awaitProjection("ROLE", role, catalog.pageAKey, "APPLIED");

    Subject viaUser = login("dan-role-user", List.of());
    identity.assignRole(new RoleAssignmentCommand("USER", viaUser.userId, role, null), ACTOR);
    awaitAllowed(viaUser, catalog.pageAKey, true);

    Subject viaGroup = login("erin-role-group", List.of(new DirectoryGroupRequest(
        "cn=audit-" + catalog.suffix, "/audit-" + catalog.suffix, "Audit")));
    UUID groupId = directoryGroupId("cn=audit-" + catalog.suffix);
    identity.assignRole(new RoleAssignmentCommand("DIRECTORY_GROUP", groupId, role, null), ACTOR);
    awaitAllowed(viaGroup, catalog.pageAKey, true);

    Subject viaAccessGroup = login("frank-role-ag", List.of());
    UUID accessGroup = accessGroupWithMember(agCode("role", catalog),
        viaAccessGroup);
    identity.assignRole(new RoleAssignmentCommand("ACCESS_GROUP", accessGroup, role, null),
        ACTOR);
    awaitAllowed(viaAccessGroup, catalog.pageAKey, true);

    for (Subject subject : List.of(viaUser, viaGroup, viaAccessGroup)) {
      assertThat(contextPermissions(subject)).containsKey(catalog.pageAKey);
    }
  }

  // ---------------------------------------------------------------- role lifecycle

  @Test void disablingARoleRemovesInheritedAccessAndReenablingRestoresIt() {
    Catalog catalog = catalog("role-lifecycle");
    var created = identity.createRole(new RoleCommand("life-" + catalog.suffix, "نقش", "Role"),
        ACTOR);
    UUID role = created.id();
    access.grant(new GrantCommand(null, "ROLE", role, catalog.pageA, catalog.view, null), ACTOR);
    Subject gina = login("gina-lifecycle", List.of());
    identity.assignRole(new RoleAssignmentCommand("USER", gina.userId, role, null), ACTOR);
    awaitAllowed(gina, catalog.pageAKey, true);

    long version = identity.updateRoleStatus(role, created.version(), false, ACTOR).version();
    awaitAllowed(gina, catalog.pageAKey, false);
    assertThat(contextPermissions(gina)).doesNotContainKey(catalog.pageAKey);
    assertThat(decision(gina, catalog.pageAKey)).isEqualTo("DENY");

    identity.updateRoleStatus(role, version, true, ACTOR);
    awaitAllowed(gina, catalog.pageAKey, true);
    assertThat(contextPermissions(gina)).containsKey(catalog.pageAKey);
  }

  // ---------------------------------------------------------------- revoke and expiry

  @Test void revokingAGrantRemovesTheTupleTheContextEntryAndTheRuntimeDecision() {
    Catalog catalog = catalog("revoke");
    Subject hank = login("hank-revoke", List.of());
    UUID grant = access.grant(new GrantCommand(null, "USER", hank.userId, catalog.pageA,
        catalog.view, null), ACTOR).id();
    awaitAllowed(hank, catalog.pageAKey, true);

    access.revoke(grant, ACTOR);

    awaitProjection("USER", hank.userId, catalog.pageAKey, null);
    awaitAllowed(hank, catalog.pageAKey, false);
    assertThat(contextPermissions(hank)).doesNotContainKey(catalog.pageAKey);
    assertThat(decision(hank, catalog.pageAKey)).isEqualTo("DENY");
    assertThat(openFgaTuples(hank.canonical)).doesNotContain(catalog.pageAObject);
  }

  @Test void expiredGrantsAndRoleAssignmentsStopBeingEffectiveWithoutARestart() {
    Catalog catalog = catalog("expiry");
    Subject ivy = login("ivy-expiry", List.of());
    access.grant(new GrantCommand(null, "USER", ivy.userId, catalog.pageA, catalog.view,
        Instant.now().plusSeconds(3)), ACTOR);
    awaitAllowed(ivy, catalog.pageAKey, true);

    awaitAllowed(ivy, catalog.pageAKey, false);
    assertThat(contextPermissions(ivy)).doesNotContainKey(catalog.pageAKey);
    assertThat(grantStatus("USER", ivy.userId, catalog.pageAKey)).isEqualTo("ARCHIVED");

    UUID role = identity.createRole(new RoleCommand("exp-" + catalog.suffix, "نقش", "Role"),
        ACTOR).id();
    access.grant(new GrantCommand(null, "ROLE", role, catalog.pageB, catalog.view, null), ACTOR);
    identity.assignRole(new RoleAssignmentCommand("USER", ivy.userId, role,
        Instant.now().plusSeconds(3)), ACTOR);
    awaitAllowed(ivy, catalog.pageBKey, true);

    awaitAllowed(ivy, catalog.pageBKey, false);
    assertThat(contextPermissions(ivy)).doesNotContainKey(catalog.pageBKey);
    assertThat(database.sql("select count(*) from user_role_assignment where user_id=:u")
        .param("u", ivy.userId).query(Long.class).single()).isZero();
  }

  // ---------------------------------------------------------------- projection failures

  @Test void openFgaOutageLeavesTheGrantRetryingUntilRecoveryThenApplied() {
    Catalog catalog = catalog("outage");
    Subject jack = login("jack-outage", List.of());

    OPENFGA_PROXY.pause();
    try {
      access.grant(new GrantCommand(null, "USER", jack.userId, catalog.pageA, catalog.view,
          null), ACTOR);
      awaitProjection("USER", jack.userId, catalog.pageAKey, "RETRYING");
      GrantView view = grant("USER", jack.userId, catalog.pageAKey);
      assertThat(view.projectionError()).isNotBlank();
      assertThat(outboxMetrics.retrying()).isGreaterThanOrEqualTo(1);
    } finally {
      OPENFGA_PROXY.resume();
    }

    awaitProjection("USER", jack.userId, catalog.pageAKey, "APPLIED");
    assertThat(grant("USER", jack.userId, catalog.pageAKey).projectionError()).isNull();
    awaitAllowed(jack, catalog.pageAKey, true);
    assertThat(contextPermissions(jack)).containsKey(catalog.pageAKey);
  }

  @Test void aPermanentlyInvalidProjectionIsDeadLetteredAndNeverReportedEffective() {
    Catalog catalog = catalog("dead-letter");
    Subject kim = login("kim-dead", List.of());
    UUID grantId = UUID.randomUUID();
    database.sql("""
        insert into authorization_grant(id,subject_type,subject_id,resource_id,action_id,relation)
        values(:id,'USER',:subject,:resource,:action,'viewer')
        """).param("id", grantId).param("subject", kim.userId).param("resource", catalog.pageA)
        .param("action", catalog.view).update();
    database.sql("""
        insert into outbox_event(aggregate_type,aggregate_id,event_type,payload,idempotency_key)
        values('grant',:id,'GRANT_WRITE',cast(:payload as jsonb),:key)
        """).param("id", grantId)
        .param("payload", "{\"user\":\"user:" + kim.canonical + "\",\"relation\":\"no_such_relation\","
            + "\"object\":\"" + catalog.pageAObject + "\"}")
        .param("key", "GRANT_WRITE:" + grantId + ":0").update();

    awaitProjection("USER", kim.userId, catalog.pageAKey, "FAILED");
    GrantView view = grant("USER", kim.userId, catalog.pageAKey);
    assertThat(view.projectionError()).isNotBlank();
    assertThat(outboxMetrics.deadLettered()).isGreaterThanOrEqualTo(1);
    assertThat(decision(kim, catalog.pageAKey)).isEqualTo("DENY");
    assertThat(contextPermissions(kim)).doesNotContainKey(catalog.pageAKey);
  }

  // ---------------------------------------------------------------- reconciliation

  @Test void reconciliationKeepsEveryLegitimateTupleRemovesStaleOnesAndIsIdempotent()
      throws Exception {
    Catalog catalog = catalog("recon");
    Subject leo = login("leo-recon", List.of(new DirectoryGroupRequest(
        "cn=recon-" + catalog.suffix, "/recon-" + catalog.suffix, "Recon")));
    UUID groupId = directoryGroupId("cn=recon-" + catalog.suffix);
    UUID accessGroup = accessGroupWithMember(agCode("recon", catalog), leo);
    UUID role = identity.createRole(new RoleCommand("recon-" + catalog.suffix, "نقش", "Role"),
        ACTOR).id();
    access.grant(new GrantCommand(null, "USER", leo.userId, catalog.pageA, catalog.view, null), ACTOR);
    access.grant(new GrantCommand(null, "GROUP", groupId, catalog.pageA, catalog.view, null), ACTOR);
    access.grant(new GrantCommand(null, "ACCESS_GROUP", accessGroup, catalog.pageA, catalog.view, null), ACTOR);
    access.grant(new GrantCommand(null, "ROLE", role, catalog.pageB, catalog.view, null), ACTOR);
    identity.assignRole(new RoleAssignmentCommand("USER", leo.userId, role, null), ACTOR);
    identity.assignRole(new RoleAssignmentCommand("DIRECTORY_GROUP", groupId, role, null), ACTOR);
    identity.assignRole(new RoleAssignmentCommand("ACCESS_GROUP", accessGroup, role, null), ACTOR);
    awaitAllowed(leo, catalog.pageAKey, true);
    awaitAllowed(leo, catalog.pageBKey, true);
    await(() -> outboxMetrics.pending() == 0);

    var direct = STACK.directClient();
    String staleUser = "user:usr_stale_" + catalog.suffix;
    direct.writeTuples(List.of(new ClientTupleKey().user(staleUser).relation("viewer")
        ._object(catalog.pageAObject))).get();

    var repair = reconciliation.reconcile(true);
    assertThat(repair.unexpected()).extracting(ReconciliationTuple::user).contains(staleUser);
    // Nothing this scenario projected may be reported missing; demo seed rows are out of scope.
    assertThat(repair.missing()).noneMatch(t -> t.object().contains(catalog.suffix));

    var verification = reconciliation.reconcile(false);
    assertThat(verification.missing()).isEmpty();
    assertThat(verification.unexpected()).isEmpty();
    assertThat(verification.expectedCount()).isEqualTo(verification.actualCount());


    awaitAllowed(leo, catalog.pageAKey, true);
    awaitAllowed(leo, catalog.pageBKey, true);
    List<String> users = openFgaTuples(leo.canonical);
    assertThat(users).contains("group:directory/" + groupId, "group:" + agCode("recon", catalog).toLowerCase(),
        "role:recon-" + catalog.suffix);
    assertThat(database.sql("select count(*) from outbox_event where dead_lettered_at is not null"
        + " and aggregate_id in (select id from authorization_grant where resource_id in (:a,:b))")
        .param("a", catalog.pageA).param("b", catalog.pageB).query(Long.class).single()).isZero();
  }

  // ---------------------------------------------------------------- identity

  @Test void loginSyncAndAuthorizationResolveTheSameCanonicalUserForACustomSubjectClaim() {
    Catalog catalog = catalog("subject-claim");
    // The IdP maps subjects from employee_id; the value carried through every step is EMP-…
    Subject employee = login("EMP-" + catalog.suffix, List.of());
    access.grant(new GrantCommand(null, "USER", employee.userId, catalog.pageA, catalog.view,
        null), ACTOR);
    awaitAllowed(employee, catalog.pageAKey, true);

    Map<String, Object> manifest = manifest(employee);
    @SuppressWarnings("unchecked")
    Map<String, Object> subject = (Map<String, Object>) manifest.get("subject");
    assertThat(subject.get("id")).isEqualTo(employee.subject);
    assertThat(database.sql("select canonical_user_id from app_user u join external_identity e"
        + " on e.user_id=u.id where e.issuer=:issuer and e.subject=:subject")
        .param("issuer", ISSUER).param("subject", employee.subject).query(String.class).single())
        .isEqualTo(employee.canonical);
    assertThat(openFgaTuples(employee.canonical)).contains("user:" + employee.canonical);
  }

  // ---------------------------------------------------------------- superset

  @Test void aSingleDashboardGrantAuthorizesOnlyThatAssetWithoutBroadReportsAccess() {
    Catalog catalog = catalog("superset");
    Subject mia = login("mia-superset", List.of());
    var dashboardA = superset.create(new AssetCommand("9" + catalog.numeric, "DASHBOARD",
        "Dashboard A " + catalog.suffix, "/superset/dashboard/9" + catalog.numeric + "/", null,
        true, "operation-default"), ACTOR);
    var dashboardB = superset.create(new AssetCommand("8" + catalog.numeric, "DASHBOARD",
        "Dashboard B " + catalog.suffix, "/superset/dashboard/8" + catalog.numeric + "/", null,
        true, "operation-default"), ACTOR);

    superset.grant(dashboardA.id(), "USER", mia.userId, "VIEW", ACTOR);
    awaitProjection("USER", mia.userId, dashboardA.resourceKey(), "APPLIED");
    awaitAllowed(mia, dashboardA.resourceKey(), true);

    Map<String, List<String>> permissions = contextPermissions(mia);
    assertThat(permissions).containsKey(dashboardA.resourceKey())
        .doesNotContainKey(dashboardB.resourceKey())
        .doesNotContainKey("application:aurevia/reports")
        .doesNotContainKey("external_resource:superset-public");
    assertThat(superset.accessForSubject(ISSUER, mia.subject, "operation-default",
        "/superset/dashboard/9" + catalog.numeric + "/", "GET", "", "DASHBOARD",
        "9" + catalog.numeric).result()).isEqualTo("ALLOW");
    assertThat(superset.accessForSubject(ISSUER, mia.subject, "operation-default",
        "/superset/dashboard/8" + catalog.numeric + "/", "GET", "", "DASHBOARD",
        "8" + catalog.numeric).result()).isEqualTo("DENY");
    assertThat(decision(mia, dashboardB.resourceKey())).isEqualTo("DENY");
    assertThat(superset.assetsForSubject(ISSUER, mia.subject, "operation-default"))
        .extracting(asset -> asset.externalId()).containsExactly("9" + catalog.numeric);
  }

  // ---------------------------------------------------------------- diagnostics

  @Test void diagnosticsNameTheContributingPathAndTheProjectionState() {
    Catalog catalog = catalog("diag");
    Subject nora = login("nora-diag", List.of(new DirectoryGroupRequest(
        "cn=diag-" + catalog.suffix, "/diag-" + catalog.suffix, "Diag")));
    UUID groupId = directoryGroupId("cn=diag-" + catalog.suffix);
    access.grant(new GrantCommand(null, "GROUP", groupId, catalog.pageA, catalog.view, null), ACTOR);
    awaitAllowed(nora, catalog.pageAKey, true);

    Map<String, Object> explanation = diagnose(nora, catalog.pageAKey, "view");
    assertThat(explanation.get("allowed")).isEqualTo(true);
    assertThat(explanation.get("reason_code")).isEqualTo("ALLOWED_BY_GRANT");
    assertThat(explanation.get("canonical_subject")).isEqualTo("user:" + nora.canonical);
    @SuppressWarnings("unchecked")
    List<Map<String, Object>> grants = (List<Map<String, Object>>) explanation.get("grants");
    assertThat(grants).singleElement().satisfies(item -> {
      assertThat(item.get("subject_type")).isEqualTo("GROUP");
      assertThat(item.get("projection_status")).isEqualTo("APPLIED");
    });

    Map<String, Object> denied = diagnose(nora, catalog.pageBKey, "view");
    assertThat(denied.get("allowed")).isEqualTo(false);
    assertThat(denied.get("reason_code")).isEqualTo("NO_CONTRIBUTING_GRANT");
  }

  // ================================================================ helpers

  private record Catalog(String suffix, String numeric, UUID pageA, UUID pageB, UUID view,
      String pageAKey, String pageBKey, String pageAObject) {}

  private record Subject(UUID userId, String subject, String canonical) {}

  /** access_group.code must match ^[A-Z][A-Z0-9_]{2,159}$. */
  private static String agCode(String name, Catalog catalog) {
    return ("AG_" + name + "_" + catalog.suffix).toUpperCase().replace('-', '_');
  }

  /** application:aurevia/hr → module → page A / page B, created through the Admin service. */
  private Catalog catalog(String name) {
    String suffix = name + "-" + Integer.toHexString(UUID.randomUUID().hashCode() & 0xffff);
    String numeric = String.valueOf(Math.abs(suffix.hashCode()) % 1_000_000);
    UUID application = database.sql(
        "select id from resource where resource_key='application:aurevia/hr'")
        .query(UUID.class).single();
    UUID view = database.sql("select id from action where action_key='view'")
        .query(UUID.class).single();
    UUID module = access.createResource(resource("module:hr." + suffix, "MODULE", application),
        ACTOR).id();
    String pageAKey = "page:hr." + suffix + ".a";
    String pageBKey = "page:hr." + suffix + ".b";
    UUID pageA = access.createResource(resource(pageAKey, "PAGE", module), ACTOR).id();
    UUID pageB = access.createResource(resource(pageBKey, "PAGE", module), ACTOR).id();
    for (UUID id : List.of(module, pageA, pageB)) access.attachAction(id, view, ACTOR);
    return new Catalog(suffix, numeric, pageA, pageB, view, pageAKey, pageBKey,
        "resource:" + pageAKey.replace(':', '/'));
  }

  private static ResourceCommand resource(String key, String type, UUID parent) {
    return new ResourceCommand(key, type, parent, key, key, "hr", "INTERNAL", null, null, null,
        "ADMIN", null, true, Map.of());
  }

  /** The real login path: identity_provider lookup, app_user/external_identity upsert, groups. */
  private Subject login(String subject, List<DirectoryGroupRequest> groups) {
    ensureIdentityProvider();
    var response = loginSync.sync(new LoginIdentityRequest("perm-idp", ISSUER, subject, subject,
        subject, subject + "@perm.test", groups, null, null, null, Map.of()));
    return new Subject(response.userId(), subject, response.canonicalUserId());
  }

  private void ensureIdentityProvider() {
    database.sql("""
        insert into identity_provider(code,name,provider_type,issuer_url,authorization_endpoint,
          token_endpoint,jwks_uri,client_id,client_secret_reference,subject_claim,
          connection_status,created_by,updated_by)
        values('perm-idp','Permission IdP','OIDC',:issuer,:issuer||'/auth',:issuer||'/token',
          :issuer||'/jwks','perm','secret://perm','employee_id','ACTIVE','test','test')
        on conflict(code) do nothing
        """).param("issuer", ISSUER).update();
  }

  private UUID directoryGroupId(String externalId) {
    return database.sql("select id from directory_group where issuer=:issuer and external_id=:id")
        .param("issuer", ISSUER).param("id", externalId).query(UUID.class).single();
  }

  private UUID accessGroupWithMember(String code, Subject member) {
    UUID id = database.sql("insert into access_group(code,name,created_by)"
        + " values(:code,:code,'test') returning id").param("code", code).query(UUID.class).single();
    database.sql("insert into effective_group_membership(user_id,access_group_id,source_type,"
        + "source_id) values(:u,:g,'OU_RULE',:s)")
        .param("u", member.userId).param("g", id).param("s", UUID.randomUUID()).update();
    database.sql("""
        insert into outbox_event(aggregate_type,aggregate_id,event_type,payload,idempotency_key)
        values('access-group-membership',:g,'ACCESS_GROUP_MEMBERSHIP_WRITE',
          jsonb_build_object('user','user:'||:canonical,'relation','member',
            'object','group:'||lower(:code)),
          'ACCESS_GROUP_MEMBERSHIP_WRITE:'||:g||':'||:canonical)
        """).param("g", id).param("canonical", member.canonical).param("code", code).update();
    return id;
  }

  private GrantView grant(String subjectType, UUID subjectId, String resourceKey) {
    return access.grants(subjectType, subjectId).stream()
        .filter(item -> item.resourceKey().equals(resourceKey)).findFirst().orElse(null);
  }

  private String grantStatus(String subjectType, UUID subjectId, String resourceKey) {
    return database.sql("select g.status::text from authorization_grant g join resource r"
        + " on r.id=g.resource_id where g.subject_type=cast(:t as subject_type)"
        + " and g.subject_id=:s and r.resource_key=:k order by g.created_at desc limit 1")
        .param("t", subjectType).param("s", subjectId).param("k", resourceKey)
        .query(String.class).single();
  }

  private void awaitProjection(String subjectType, UUID subjectId, String resourceKey,
      String expected) {
    await(() -> {
      GrantView view = grant(subjectType, subjectId, resourceKey);
      return expected == null ? view == null : view != null
          && expected.equals(view.projectionStatus());
    });
  }

  private static final java.util.concurrent.atomic.AtomicReference<String> LAST_REASON = new java.util.concurrent.atomic.AtomicReference<>("");

  private void awaitAllowed(Subject subject, String resourceKey, boolean expected) {
    await(() -> (expected ? "ALLOW" : "DENY").equals(decision(subject, resourceKey)));
  }

  private static void await(Supplier<Boolean> condition) {
    long deadline = System.nanoTime() + WAIT.toNanos();
    while (System.nanoTime() < deadline) {
      if (Boolean.TRUE.equals(condition.get())) return;
      try { Thread.sleep(150); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }
    assertThat(condition.get()).as("condition within " + WAIT + " (last reason: " + LAST_REASON.get() + ")").isTrue();
  }

  // ---------------------------------------------------------------- HTTP, as the BFF calls it

  private HttpHeaders headers() {
    HttpHeaders headers = new HttpHeaders();
    headers.setBasicAuth("bff", "test-secret");
    headers.setContentType(MediaType.APPLICATION_JSON);
    headers.set("X-Actor-Issuer", ISSUER);
    headers.set("X-Actor-Subject", "administrator");
    return headers;
  }

  private String decision(Subject subject, String resourceKey) {
    String type = database.sql("select type::text from resource where resource_key=:k")
        .param("k", resourceKey).query(String.class).single();
    String object = com.aurevia.authz.semantics.ResourceObjectKey.from(type, resourceKey);
    Map<String, Object> body = Map.of("subjectId", subject.subject, "issuer", ISSUER,
        "resource", object, "action", "view", "context", Map.of(),
        "correlationId", UUID.randomUUID().toString());
    @SuppressWarnings("unchecked")
    Map<String, Object> response = http.postForObject(
        "http://localhost:" + port + "/internal/v1/authorize/check",
        new HttpEntity<>(body, headers()), Map.class);
    LAST_REASON.set(String.valueOf(response.get("reasonCode")));
    return String.valueOf(response.get("result"));
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> manifest(Subject subject) {
    return http.exchange("http://localhost:" + port + "/internal/v1/subjects/" + subject.subject
        + "/manifest?issuer=" + ISSUER, org.springframework.http.HttpMethod.GET,
        new HttpEntity<>(headers()), Map.class).getBody();
  }

  @SuppressWarnings("unchecked")
  private Map<String, List<String>> contextPermissions(Subject subject) {
    return (Map<String, List<String>>) manifest(subject).get("permissions");
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> diagnose(Subject subject, String resourceKey, String action) {
    grantAdministrator();
    return http.exchange("http://localhost:" + port
        + "/internal/v1/registry/diagnostics/authorization?issuer=" + ISSUER + "&subject="
        + subject.subject + "&resource=" + resourceKey + "&action=" + action,
        org.springframework.http.HttpMethod.GET, new HttpEntity<>(headers()), Map.class).getBody();
  }

  /** The admin interceptor requires can_manage on application:aurevia for the actor. */
  private void grantAdministrator() {
    Subject admin = login("administrator", List.of());
    UUID root = database.sql("select id from resource where resource_key='application:aurevia'")
        .query(UUID.class).single();
    UUID manage = database.sql("select id from action where action_key='manage'")
        .query(UUID.class).single();
    database.sql("insert into resource_action(resource_id,action_id) values(:r,:a)"
        + " on conflict do nothing").param("r", root).param("a", manage).update();
    access.grant(new GrantCommand(null, "USER", admin.userId, root, manage, null), ACTOR);
    await(() -> {
      GrantView view = grant("USER", admin.userId, "application:aurevia");
      return view != null && "APPLIED".equals(view.projectionStatus());
    });
  }

  private List<String> openFgaTuples(String canonical) {
    Map<String, Object> explanation = diagnoseRaw(canonical);
    return explanation == null ? List.of() : explanation.keySet().stream().toList();
  }

  /** Reads the subject's tuples directly from OpenFGA; the Read API needs an object type. */
  private Map<String, Object> diagnoseRaw(String canonical) {
    try {
      var direct = STACK.directClient();
      java.util.LinkedHashMap<String, Object> result = new java.util.LinkedHashMap<>();
      result.put("user:" + canonical, true);
      for (String type : List.of("group:", "role:", "resource:", "application:",
          "external_resource:")) {
        var response = direct.read(new dev.openfga.sdk.api.client.model.ClientReadRequest()
            .user("user:" + canonical)._object(type)).get();
        if (response.getTuples() != null) response.getTuples().forEach(tuple ->
            result.put(tuple.getKey().getObject(), tuple.getKey().getRelation()));
      }
      return result;
    } catch (Exception failure) {
      throw new IllegalStateException("OpenFGA read failed", failure);
    }
  }

  @SuppressWarnings("unused")
  private static ClientTupleKeyWithoutCondition tuple(String user, String relation, String object) {
    return new ClientTupleKeyWithoutCondition().user(user).relation(relation)._object(object);
  }
}

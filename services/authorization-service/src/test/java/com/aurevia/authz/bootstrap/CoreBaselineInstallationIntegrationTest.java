package com.aurevia.authz.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

/**
 * Production cleanliness by construction, from an EMPTY database:
 * <ul>
 *   <li>PRODUCTION: the Core baseline alone (what a fresh installation receives) contains no demo/test
 *       artifact and every Core entity the platform needs;</li>
 *   <li>DEVELOPMENT: Core baseline + the development fixture restores the demo content;</li>
 *   <li>baseline + fixture is equivalent (by natural keys) to the historical chain V1..Vn, so nothing
 *       was deleted, only separated.</li>
 * </ul>
 */
@EnabledIfEnvironmentVariable(named = "AUREVIA_TEST_JDBC_URL", matches = ".+")
class CoreBaselineInstallationIntegrationTest {
  private static final Path RESOURCES = Path.of("src/main/resources");
  private static final List<String> DEMO_RESOURCE_KEYS = List.of("application:aurevia/hr", "application:aurevia/finance",
      "application:hr", "page:hr.employees", "page:finance.payments", "business_resource:employee", "business_resource:payment",
      "data:hr.payroll", "data:finance.ledger", "external_resource:superset/hr-workforce",
      "external_resource:superset/finance-executive", "external_resource:superset-public/dashboard/welcome-dashboard",
      "component:hr.employee.grid", "module:hr", "module:finance", "application:public-default", "application:operation-default");
  private static final List<String> CORE_RESOURCE_KEYS = List.of("application:aurevia", "application:aurevia/admin",
      "application:aurevia/reports", "module:admin.superset-catalog", "external_resource:superset-public",
      "business_resource:public-zone-logs", "integration.auth-profile", "proxy.target", "proxy.route", "proxy.operation");

  private static Schema production, development, chain;

  @BeforeAll static void install() throws Exception {
    production = Schema.create("classpath:db/migration", "classpath:db/baseline");
    development = Schema.create("classpath:db/migration", "classpath:db/baseline");
    development.apply(fixtureBody());
    chain = Schema.create("classpath:db/migration");
  }
  @AfterAll static void drop() throws Exception { for (var s : List.of(production, development, chain)) if (s != null) s.close(); }

  // ---------------------------------------------------------------- PRODUCTION

  @Test void freshInstallationAppliesTheBaselineInsteadOfTheHistoricalChain() {
    assertThat(production.list("select type||':'||version from flyway_schema_history where success and version is not null"))
        .containsExactly("SQL_BASELINE:79", "SQL:80");
  }

  @Test void productionContainsNoDemoOrTestArtifacts() {
    assertThat(production.list("select resource_key from resource")).containsExactlyInAnyOrderElementsOf(CORE_RESOURCE_KEYS)
        .doesNotContainAnyElementsOf(DEMO_RESOURCE_KEYS);
    assertThat(production.list("select code from panel")).containsExactly("ADMIN");
    assertThat(production.count("app_user")).isZero();
    assertThat(production.count("external_identity")).isZero();
    assertThat(production.count("directory_group")).isZero();
    assertThat(production.list("select role_key from application_role"))
        .containsExactlyInAnyOrder("aurevia-administrator", "superset-designer", "superset-viewer")
        .doesNotContain("hr-viewer", "finance-maker", "finance-approver");
    assertThat(production.count("user_role_assignment")).isZero();
    assertThat(production.list("select subject_type::text from authorization_grant where status='ACTIVE'")).containsOnly("ROLE");
    for (String table : List.of("service_target", "proxy_route", "route_operation", "outbound_connection",
        "superset_instance", "superset_asset", "superset_proxy_mapping", "resource_manifest_import", "identity_provider"))
      assertThat(production.count(table)).as(table).isZero();
    assertThat(production.list("select code from outbound_auth_profile")).containsExactly("public-iam-forward");
    // No OpenFGA tuple for a demo object or a demo subject can ever be projected.
    assertThat(production.list("select payload::text from outbox_event"))
        .allSatisfy(payload -> assertThat(payload).doesNotContain("user:usr_", "/hr", "/finance", "test-sso", "test-legacy", "superset/"));
    assertThat(production.list("select manifest_snapshot::text from ui_module_artifact"))
        .allSatisfy(manifest -> assertThat(manifest).doesNotContain("mfe-hr", "mfe-finance", "mf-test"));
  }

  @Test void productionContainsEveryCoreEntityThePlatformNeeds() {
    assertThat(production.count("action")).isEqualTo(chain.count("action"));
    assertThat(production.list("select action_key from action")).contains("view", "admin", "manage", "view_api", "view_audit", "test");
    assertThat(production.list("select p.code||' '||a.artifact_version from panel p join ui_module_artifact a on a.id=p.active_artifact_id"))
        .containsExactly("ADMIN 0.6.0");
    assertThat(production.count("ui_module_artifact")).isEqualTo(6);
    assertThat(production.list("select r.resource_key||'/'||a.action_key from resource r join resource_action ra on ra.resource_id=r.id join action a on a.id=ra.action_id"))
        .contains("application:aurevia/admin", "application:aurevia/view", "application:aurevia/admin/view",
            "business_resource:public-zone-logs/view_api", "business_resource:public-zone-logs/view_audit",
            "integration.auth-profile/test", "external_resource:superset-public/view");
    assertThat(production.list("select c.resource_key||' <- '||p.resource_key from resource c join resource p on p.id=c.parent_id"))
        .contains("application:aurevia/admin <- application:aurevia", "proxy.target <- application:aurevia/admin");
    assertThat(production.list("select event_type||' '||(payload->>'object') from outbox_event"))
        .contains("RESOURCE_PARENT_WRITE application:aurevia/admin", "GRANT_WRITE resource:module/admin.superset-catalog");
    assertThat(production.list("select component from schema_version")).contains("control-plane", "admin-navigation-contract", "superset-role-model");
    assertThat(production.list("select subject||'|'||status from (select aurevia_subject_key('https://i','s') subject,'ok' status) x")).hasSize(1);
    // The bootstrap target of the first administrator exists.
    assertThat(production.list("select r.resource_key from resource r join resource_action ra on ra.resource_id=r.id join action a on a.id=ra.action_id where a.action_key='admin' and r.resource_key='application:aurevia'")).hasSize(1);
  }

  // ---------------------------------------------------------------- DEVELOPMENT

  @Test void developmentFixtureRestoresTheDemoContentOnTopOfCore() {
    assertThat(development.list("select resource_key from resource")).containsAll(DEMO_RESOURCE_KEYS).containsAll(CORE_RESOURCE_KEYS);
    assertThat(development.list("select code from panel")).containsExactlyInAnyOrder("ADMIN", "HR", "FINANCE", "REPORTS");
    assertThat(development.list("select username from app_user")).contains("administrator", "hr-user", "finance-maker", "finance-approver", "viewer");
    assertThat(development.list("select role_key from application_role")).contains("hr-viewer", "finance-maker", "finance-approver");
    assertThat(development.count("user_role_assignment")).isEqualTo(chain.count("user_role_assignment"));
    assertThat(development.list("select code from superset_instance")).containsExactlyInAnyOrder("public-default", "operation-default");
    assertThat(development.list("select p.code||' '||a.artifact_version from panel p join ui_module_artifact a on a.id=p.active_artifact_id order by 1"))
        .containsExactly("ADMIN 0.6.0", "FINANCE 0.1.0", "HR 0.1.1", "REPORTS 0.1.0");
    assertThat(development.list("select version from schema_version where component='development-demo-fixture'")).containsExactly("79");
  }

  @Test void fixtureIsIdempotent() throws Exception {
    long grants = development.count("authorization_grant"), outbox = development.count("outbox_event");
    development.apply(fixtureBody());
    assertThat(development.count("authorization_grant")).isEqualTo(grants);
    assertThat(development.count("outbox_event")).isEqualTo(outbox);
  }

  // ---------------------------------------------------------------- SEPARATION, NOT DELETION

  @Test void baselinePlusFixtureEqualsTheHistoricalChainByNaturalKeys() {
    Map<String, String> keys = Map.ofEntries(
        Map.entry("resource", "select resource_key||'|'||type||'|'||coalesce((select resource_key from resource p where p.id=r.parent_id),'')||'|'||status from resource r"),
        Map.entry("resource_action", "select r.resource_key||'/'||a.action_key from resource_action ra join resource r on r.id=ra.resource_id join action a on a.id=ra.action_id"),
        Map.entry("action", "select action_key from action"),
        Map.entry("panel", "select code||'|'||semantic_version||'|'||coalesce(discovery_resource_key,'') from panel"),
        Map.entry("ui_module_artifact", "select p.code||'|'||a.artifact_version||'|'||coalesce(a.manifest_checksum,'') from ui_module_artifact a join panel p on p.id=a.panel_id"),
        Map.entry("app_user", "select issuer||'|'||external_id||'|'||username from app_user"),
        Map.entry("application_role", "select role_key||'|'||status from application_role"),
        Map.entry("user_role_assignment", "select u.external_id||'|'||r.role_key from user_role_assignment x join app_user u on u.id=x.user_id join application_role r on r.id=x.role_id"),
        Map.entry("authorization_grant", "select g.subject_type||'|'||coalesce(u.external_id,ar.role_key,dg.external_id)||'|'||r.resource_key||'|'||a.action_key||'|'||g.relation||'|'||g.status from authorization_grant g join resource r on r.id=g.resource_id join action a on a.id=g.action_id left join app_user u on g.subject_type='USER' and u.id=g.subject_id left join application_role ar on g.subject_type='ROLE' and ar.id=g.subject_id left join directory_group dg on g.subject_type='GROUP' and dg.id=g.subject_id"),
        Map.entry("proxy_route", "select code||'|'||path_prefix from proxy_route"),
        Map.entry("route_operation", "select r.code||'|'||o.http_method||'|'||o.path_pattern from route_operation o join proxy_route r on r.id=o.proxy_route_id"),
        Map.entry("service_target", "select code||'|'||gateway_base_url from service_target"),
        Map.entry("outbound_auth_profile", "select code from outbound_auth_profile"),
        Map.entry("superset_instance", "select code||'|'||zone from superset_instance"),
        Map.entry("superset_asset", "select external_id||'|'||asset_type from superset_asset"),
        Map.entry("directory_group", "select external_id from directory_group"),
        Map.entry("schema_version", "select component||'='||version from schema_version where component<>'development-demo-fixture'"),
        Map.entry("openfga relationships", "select 'compared-via-effectiveRelationships'"));
    keys.forEach((name, sql) -> {
      if (name.equals("openfga relationships")) {
        // The chain's outbox carries repair history; the reconciler's table-derived set is authoritative.
        assertThat(development.effectiveRelationships()).as(name).containsExactlyInAnyOrderElementsOf(chain.effectiveRelationships());
        return;
      }
      assertThat(development.list(sql)).as(name).containsExactlyInAnyOrderElementsOf(chain.list(sql));
    });
  }

  private static String fixtureBody() throws Exception {
    String script = Files.readString(RESOURCES.resolve("db/fixtures/development/010-development-demo.sql"), StandardCharsets.UTF_8);
    // The psql guard (\gset/\if) only exists for the Compose loader; the test applies the body directly.
    String body = script.substring(script.indexOf("BEGIN;"), script.lastIndexOf("COMMIT;") + "COMMIT;".length());
    assertThat(Pattern.compile("^\\\\", Pattern.MULTILINE).matcher(body).find()).as("no psql meta-command inside the body").isFalse();
    return body;
  }

  /** One disposable schema per installation mode, migrated by Flyway with the given locations. */
  private static final class Schema implements AutoCloseable {
    final String name = "install_test_" + UUID.randomUUID().toString().replace("-", "");
    final DriverManagerDataSource source;
    final Connection connection;
    final JdbcClient database;
    private Schema(String... locations) throws Exception {
      String url = System.getenv("AUREVIA_TEST_JDBC_URL");
      source = new DriverManagerDataSource(url, env("AUREVIA_TEST_JDBC_USER", "postgres"), env("AUREVIA_TEST_JDBC_PASSWORD", "postgres"));
      // Several disposable schemas coexist here; keep pgcrypto in public where all of them can see it.
      try (Connection setup = source.getConnection(); var statement = setup.createStatement()) {
        statement.execute("create extension if not exists pgcrypto with schema public");
      }
      Flyway.configure().dataSource(source).schemas(name).defaultSchema(name).locations(locations)
          .initSql("set search_path to " + name + ", public").load().migrate();
      connection = source.getConnection();
      try (var statement = connection.createStatement()) { statement.execute("set search_path to " + name + ", public"); }
      database = JdbcClient.create(new SingleConnectionDataSource(connection, true));
    }
    static Schema create(String... locations) throws Exception { return new Schema(locations); }
    void apply(String script) throws Exception { try (var statement = connection.createStatement()) { statement.execute(script); } }
    List<String> list(String sql) { return database.sql(sql).query(String.class).list(); }
    long count(String table) { return database.sql("select count(*) from " + table).query(Long.class).single(); }
    /** The OpenFGA relationships the runtime reconciler expects for this database (the source of truth). */
    List<String> effectiveRelationships() {
      // Canonical user ids derive from random UUIDs; compare subjects by their stable external id.
      Map<String, String> users = new java.util.HashMap<>();
      database.sql("select 'user:'||canonical_user_id||'|'||external_id from app_user").query(String.class).list()
          .forEach(row -> users.put(row.substring(0, row.indexOf('|')), "user:" + row.substring(row.indexOf('|') + 1)));
      return com.aurevia.authz.sync.ReconciliationExpectations.tuples(database).stream()
          .map(tuple -> users.entrySet().stream().reduce(tuple, (t, e) -> t.replace(e.getKey(), e.getValue()), (a, b) -> a)).sorted().toList();
    }
    @Override public void close() throws Exception {
      connection.close();
      try (Connection cleanup = source.getConnection(); var statement = cleanup.createStatement()) { statement.execute("drop schema " + name + " cascade"); }
    }
    private static String env(String key, String fallback) { String value = System.getenv(key); return value == null ? fallback : value; }
  }
}

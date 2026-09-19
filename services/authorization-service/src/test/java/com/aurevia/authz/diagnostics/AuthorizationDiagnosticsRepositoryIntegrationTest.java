package com.aurevia.authz.diagnostics;

import static org.assertj.core.api.Assertions.assertThat;

import com.aurevia.authz.support.PostgresFixture;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * Executes the diagnostics SQL against the real migrated schema.
 *
 * <p>The diagnostics queries reach across every subject type, the resource ancestor chain, and
 * the outbox. A mock-only test would not prove the SQL is valid, so this runs the real
 * migrations, including V71, in a disposable schema.</p>
 */
@EnabledIfEnvironmentVariable(named = "AUREVIA_TEST_JDBC_URL", matches = ".+")
class AuthorizationDiagnosticsRepositoryIntegrationTest {

  private PostgresFixture fixture;
  private JdbcClient database;
  private AuthorizationDiagnosticsRepository diagnostics;

  private UUID applicationId;
  private UUID pageId;
  private UUID viewActionId;
  private UUID userId;

  @BeforeEach void setUp() throws Exception {
    fixture = PostgresFixture.migrated();
    database = fixture.database();
    diagnostics = new JdbcAuthorizationDiagnosticsRepository(database);
    seedCatalog();
  }

  @AfterEach void tearDown() throws Exception { fixture.close(); }

  @Test void theReportsPanelDeclaresTheSupersetCatalogAsItsDiscoverySubtree() {
    String discoveryKey = database.sql(
        "select discovery_resource_key from panel where code='REPORTS'")
        .query(String.class).optional().orElse(null);

    assertThat(discoveryKey).isEqualTo("external_resource:superset-public");
  }

  @Test void discoveryResourceKeyMustReferenceARealCatalogResource() {
    assertThat(database.sql(
        "select count(*) from panel p where p.discovery_resource_key is not null"
        + " and not exists(select 1 from resource r"
        + "   where r.resource_key=p.discovery_resource_key)")
        .query(Long.class).single()).isZero();
  }

  @Test void aDirectUserGrantIsReportedWithItsProjectionState() {
    UUID grantId = grant("USER", userId, pageId);
    enqueueProjection(grantId, "GRANT_WRITE", true, false);

    List<AuthorizationDiagnostics.GrantExplanation> grants =
        diagnostics.contributingGrants(pageId, "usr_alice");

    assertThat(grants).singleElement().satisfies(item -> {
      assertThat(item.subjectType()).isEqualTo("USER");
      assertThat(item.grantedResourceKey()).isEqualTo("page:diag.employees");
      assertThat(item.projectionStatus()).isEqualTo("APPLIED");
    });
  }

  @Test void anUnprocessedProjectionIsReportedAsPendingRatherThanApplied() {
    UUID grantId = grant("USER", userId, pageId);
    enqueueProjection(grantId, "GRANT_WRITE", false, false);

    assertThat(diagnostics.contributingGrants(pageId, "usr_alice"))
        .singleElement()
        .satisfies(item -> assertThat(item.projectionStatus()).isEqualTo("PENDING"));
  }

  @Test void aDeadLetteredProjectionIsReportedAsFailedWithItsError() {
    UUID grantId = grant("USER", userId, pageId);
    enqueueProjection(grantId, "GRANT_WRITE", false, true);

    assertThat(diagnostics.contributingGrants(pageId, "usr_alice"))
        .singleElement()
        .satisfies(item -> {
          assertThat(item.projectionStatus()).isEqualTo("FAILED");
          assertThat(item.projectionError()).isEqualTo("OpenFGA rejected the tuple");
        });
  }

  @Test void aGrantOnAnAncestorResourceIsReportedForTheDescendantPage() {
    UUID grantId = grant("USER", userId, applicationId);
    enqueueProjection(grantId, "GRANT_WRITE", true, false);

    assertThat(diagnostics.contributingGrants(pageId, "usr_alice"))
        .singleElement()
        .satisfies(item ->
            assertThat(item.grantedResourceKey()).isEqualTo("application:diag"));
  }

  @Test void directoryGroupAccessGroupAndRoleGrantsAllReachTheSubject() {
    UUID groupId = directoryGroup();
    UUID accessGroupId = accessGroup();
    UUID roleId = roleAssignedToUser();

    grant("GROUP", groupId, pageId);
    grant("ACCESS_GROUP", accessGroupId, pageId);
    grant("ROLE", roleId, pageId);

    assertThat(diagnostics.contributingGrants(pageId, "usr_alice"))
        .extracting(AuthorizationDiagnostics.GrantExplanation::subjectType)
        .containsExactlyInAnyOrder("GROUP", "ACCESS_GROUP", "ROLE");
  }

  @Test void anInactiveRoleStopsContributingItsGrant() {
    UUID roleId = roleAssignedToUser();
    grant("ROLE", roleId, pageId);
    assertThat(diagnostics.contributingGrants(pageId, "usr_alice")).hasSize(1);

    database.sql("update application_role set status='INACTIVE' where id=:id")
        .param("id", roleId).update();

    assertThat(diagnostics.contributingGrants(pageId, "usr_alice")).isEmpty();
  }

  @Test void anExpiredGrantAndAnExpiredRoleAssignmentStopContributing() {
    database.sql(
        "insert into authorization_grant(subject_type,subject_id,resource_id,action_id,"
        + "relation,expires_at) values('USER',:subject,:resource,:action,'viewer',"
        + "now()-interval '1 hour')")
        .param("subject", userId).param("resource", pageId).param("action", viewActionId)
        .update();

    assertThat(diagnostics.contributingGrants(pageId, "usr_alice")).isEmpty();

    UUID roleId = roleAssignedToUser();
    database.sql("update user_role_assignment set expires_at=now()-interval '1 hour'"
        + " where role_id=:role").param("role", roleId).update();
    grant("ROLE", roleId, pageId);

    assertThat(diagnostics.contributingGrants(pageId, "usr_alice")).isEmpty();
  }

  @Test void membershipsAndRolesExposeTheirOpenFgaObjects() {
    UUID groupId = directoryGroup();
    accessGroup();
    roleAssignedToUser();

    assertThat(diagnostics.memberships("usr_alice"))
        .extracting(AuthorizationDiagnostics.MembershipExplanation::openFgaObject)
        .containsExactlyInAnyOrder("group:directory/" + groupId, "group:diag_ou");

    assertThat(diagnostics.roles("usr_alice"))
        .extracting(AuthorizationDiagnostics.RoleExplanation::openFgaObject)
        .containsExactly("role:diag.auditor");
  }

  @Test void aResourceResolvesByRegistryKeyAndByCanonicalOpenFgaObject() {
    assertThat(diagnostics.resource("page:diag.employees", "view"))
        .get().satisfies(state -> {
          assertThat(state.id()).isEqualTo(pageId);
          assertThat(state.actionAttached()).isTrue();
        });

    assertThat(diagnostics.resource("resource:page/diag.employees", "view"))
        .get().satisfies(state -> assertThat(state.id()).isEqualTo(pageId));

    assertThat(diagnostics.resource("page:diag.employees", "delete"))
        .get().satisfies(state -> assertThat(state.actionAttached()).isFalse());

    assertThat(diagnostics.resource("page:does-not-exist", "view")).isEmpty();
  }

  private void seedCatalog() {
    applicationId = UUID.randomUUID();
    pageId = UUID.randomUUID();
    database.sql("insert into resource(id,resource_key,type,name_fa,name_en)"
        + " values(:id,'application:diag','APPLICATION','تشخیص','Diagnostics')")
        .param("id", applicationId).update();
    database.sql("insert into resource(id,resource_key,type,parent_id,name_fa,name_en)"
        + " values(:id,'page:diag.employees','PAGE',:parent,'کارکنان','Employees')")
        .param("id", pageId).param("parent", applicationId).update();
    viewActionId = database.sql("select id from action where action_key='view'")
        .query(UUID.class).single();
    database.sql("insert into resource_action(resource_id,action_id) values(:r,:a),(:p,:a)")
        .param("r", pageId).param("p", applicationId).param("a", viewActionId).update();
    userId = UUID.randomUUID();
    database.sql("insert into app_user(id,issuer,external_id,username,canonical_user_id)"
        + " values(:id,'https://issuer.test','alice','alice','usr_alice')")
        .param("id", userId).update();
  }

  private UUID grant(String subjectType, UUID subjectId, UUID resourceId) {
    return database.sql(
        "insert into authorization_grant(subject_type,subject_id,resource_id,action_id,relation)"
        + " values(cast(:type as subject_type),:subject,:resource,:action,'viewer') returning id")
        .param("type", subjectType).param("subject", subjectId).param("resource", resourceId)
        .param("action", viewActionId).query(UUID.class).single();
  }

  private void enqueueProjection(UUID grantId, String eventType, boolean processed,
      boolean deadLettered) {
    database.sql(
        "insert into outbox_event(aggregate_type,aggregate_id,event_type,payload,"
        + "idempotency_key,attempts,processed_at,dead_lettered_at,last_error)"
        + " values('grant',:id,:type,'{}'::jsonb,:key,:attempts,"
        + "  case when :processed then now() end,"
        + "  case when :dead then now() end,"
        + "  case when :dead then 'OpenFGA rejected the tuple' end)")
        .param("id", grantId).param("type", eventType)
        .param("key", eventType + ":" + grantId + ":0")
        .param("attempts", deadLettered ? 12 : 0)
        .param("processed", processed).param("dead", deadLettered).update();
  }

  private UUID directoryGroup() {
    UUID groupId = database.sql(
        "insert into directory_group(issuer,external_id,normalized_path,display_name)"
        + " values('https://issuer.test','cn=diag','/diag','Diagnostics group') returning id")
        .query(UUID.class).single();
    database.sql("insert into user_group_membership(user_id,group_id) values(:u,:g)")
        .param("u", userId).param("g", groupId).update();
    return groupId;
  }

  private UUID accessGroup() {
    UUID accessGroupId = database.sql(
        "insert into access_group(code,name,created_by)"
        + " values('DIAG_OU','Diagnostics OU','test') returning id")
        .query(UUID.class).single();
    database.sql(
        "insert into effective_group_membership(user_id,access_group_id,source_type,source_id)"
        + " values(:u,:g,'OU_RULE',:source)")
        .param("u", userId).param("g", accessGroupId).param("source", UUID.randomUUID()).update();
    return accessGroupId;
  }

  private UUID roleAssignedToUser() {
    UUID roleId = database.sql(
        "insert into application_role(role_key,name_fa,name_en)"
        + " values('diag.auditor','حسابرس','Auditor') returning id")
        .query(UUID.class).single();
    database.sql("insert into user_role_assignment(user_id,role_id,assigned_by)"
        + " values(:u,:r,'test')").param("u", userId).param("r", roleId).update();
    return roleId;
  }
}

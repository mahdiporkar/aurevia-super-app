package com.aurevia.authz.sync;

import static org.assertj.core.api.Assertions.assertThat;

import com.aurevia.authz.support.PostgresFixture;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * Guards the query that decides which OpenFGA tuples are legitimate.
 *
 * <p>Startup reconciliation deletes every tuple this query does not return. A relationship the
 * query forgets is therefore silently revoked from every affected subject on the next restart,
 * which is the most damaging failure mode in the projection pipeline. These tests run the real
 * query against the real migrated schema for every subject type and every relationship the
 * authorization model supports.</p>
 */
@EnabledIfEnvironmentVariable(named = "AUREVIA_TEST_JDBC_URL", matches = ".+")
class OpenFgaReconciliationRepositoryIntegrationTest {

  private PostgresFixture fixture;
  private JdbcClient database;
  private OpenFgaReconciliationRepository reconciliation;

  private UUID applicationId;
  private UUID moduleId;
  private UUID pageId;
  private UUID viewActionId;
  private UUID userId;

  @BeforeEach void setUp() throws Exception {
    fixture = PostgresFixture.migrated();
    database = fixture.database();
    reconciliation = new JdbcOpenFgaReconciliationRepository(database);
    seedCatalog();
  }

  @AfterEach void tearDown() throws Exception { fixture.close(); }

  @Test void aDirectUserGrantIsExpected() {
    grant("USER", userId, pageId);

    assertThat(expected()).contains(
        new ReconciliationTuple("user:usr_alice", "viewer", "resource:page/recon.employees"));
  }

  @Test void aDirectoryGroupGrantIsExpectedAsAMemberSet() {
    UUID groupId = directoryGroup();
    grant("GROUP", groupId, pageId);

    assertThat(expected()).contains(new ReconciliationTuple(
        "group:directory/" + groupId + "#member", "viewer", "resource:page/recon.employees"));
  }

  @Test void anAccessGroupGrantIsExpectedAsAMemberSet() {
    UUID accessGroupId = accessGroup();
    grant("ACCESS_GROUP", accessGroupId, pageId);

    assertThat(expected()).contains(new ReconciliationTuple(
        "group:recon_ou#member", "viewer", "resource:page/recon.employees"));
  }

  @Test void aRoleGrantIsExpectedAsAnAssigneeSet() {
    UUID roleId = role();
    grant("ROLE", roleId, pageId);

    assertThat(expected()).contains(new ReconciliationTuple(
        "role:recon.auditor#assignee", "viewer", "resource:page/recon.employees"));
  }

  @Test void everyRoleAssignmentPathIsExpected() {
    UUID roleId = role();
    UUID groupId = directoryGroup();
    UUID accessGroupId = accessGroup();
    assignRoleToUser(roleId);
    database.sql("insert into group_role_assignment(group_id,role_id,assigned_by)"
        + " values(:g,:r,'test')").param("g", groupId).param("r", roleId).update();
    database.sql("insert into access_group_role_assignment(access_group_id,role_id,assigned_by)"
        + " values(:g,:r,'test')").param("g", accessGroupId).param("r", roleId).update();

    assertThat(expected()).contains(
        new ReconciliationTuple("user:usr_alice", "assignee", "role:recon.auditor"),
        new ReconciliationTuple("group:directory/" + groupId + "#member", "assignee",
            "role:recon.auditor"),
        new ReconciliationTuple("group:recon_ou#member", "assignee", "role:recon.auditor"));
  }

  @Test void bothMembershipKindsAreExpected() {
    UUID groupId = directoryGroup();
    accessGroup();

    assertThat(expected()).contains(
        new ReconciliationTuple("user:usr_alice", "member", "group:directory/" + groupId),
        new ReconciliationTuple("user:usr_alice", "member", "group:recon_ou"));
  }

  @Test void thePageToModuleToApplicationHierarchyIsExpected() {
    assertThat(expected()).contains(
        new ReconciliationTuple("resource:module/recon.people", "parent",
            "resource:page/recon.employees"),
        new ReconciliationTuple("application:recon", "parent", "resource:module/recon.people"));
  }

  @Test void anInactiveRoleContributesNeitherItsGrantsNorItsAssignments() {
    UUID roleId = role();
    grant("ROLE", roleId, pageId);
    assignRoleToUser(roleId);
    assertThat(expected()).contains(
        new ReconciliationTuple("role:recon.auditor#assignee", "viewer",
            "resource:page/recon.employees"),
        new ReconciliationTuple("user:usr_alice", "assignee", "role:recon.auditor"));

    database.sql("update application_role set status='INACTIVE' where id=:id")
        .param("id", roleId).update();

    assertThat(expected()).doesNotContain(
        new ReconciliationTuple("role:recon.auditor#assignee", "viewer",
            "resource:page/recon.employees"),
        new ReconciliationTuple("user:usr_alice", "assignee", "role:recon.auditor"));
  }

  @Test void revokedExpiredAndDeactivatedRelationshipsAreGenuinelyStale() {
    UUID revoked = grant("USER", userId, pageId);
    database.sql("update authorization_grant set status='ARCHIVED' where id=:id")
        .param("id", revoked).update();

    database.sql(
        "insert into authorization_grant(subject_type,subject_id,resource_id,action_id,"
        + "relation,expires_at) values('USER',:s,:r,:a,'viewer',now()-interval '1 hour')")
        .param("s", userId).param("r", pageId).param("a", viewActionId).update();

    assertThat(expected()).doesNotContain(
        new ReconciliationTuple("user:usr_alice", "viewer", "resource:page/recon.employees"));

    UUID groupId = directoryGroup();
    grant("GROUP", groupId, pageId);
    database.sql("update directory_group set status='ARCHIVED' where id=:id")
        .param("id", groupId).update();

    assertThat(expected()).doesNotContain(
        new ReconciliationTuple("group:directory/" + groupId + "#member", "viewer",
            "resource:page/recon.employees"),
        new ReconciliationTuple("user:usr_alice", "member", "group:directory/" + groupId));
  }

  @Test void aDeprecatedResourceStopsContributingItsParentRelationship() {
    assertThat(expected()).contains(new ReconciliationTuple(
        "resource:module/recon.people", "parent", "resource:page/recon.employees"));

    database.sql("update resource set status='DEPRECATED' where id=:id")
        .param("id", pageId).update();

    assertThat(expected()).doesNotContain(new ReconciliationTuple(
        "resource:module/recon.people", "parent", "resource:page/recon.employees"));
  }

  @Test void theExpectedProjectionIsDeterministicAcrossRepeatedReads() {
    grant("USER", userId, pageId);
    grant("ACCESS_GROUP", accessGroup(), pageId);

    Set<ReconciliationTuple> first = expected();
    Set<ReconciliationTuple> second = expected();

    // Reconciliation must converge: repeating it may not change the desired state.
    assertThat(second).isEqualTo(first);
  }

  private Set<ReconciliationTuple> expected() { return reconciliation.expectedTuples(); }

  private void seedCatalog() {
    applicationId = UUID.randomUUID();
    moduleId = UUID.randomUUID();
    pageId = UUID.randomUUID();
    database.sql("insert into resource(id,resource_key,type,name_fa,name_en)"
        + " values(:id,'application:recon','APPLICATION','آشتی','Reconciliation')")
        .param("id", applicationId).update();
    database.sql("insert into resource(id,resource_key,type,parent_id,name_fa,name_en)"
        + " values(:id,'module:recon.people','MODULE',:parent,'افراد','People')")
        .param("id", moduleId).param("parent", applicationId).update();
    database.sql("insert into resource(id,resource_key,type,parent_id,name_fa,name_en)"
        + " values(:id,'page:recon.employees','PAGE',:parent,'کارکنان','Employees')")
        .param("id", pageId).param("parent", moduleId).update();
    viewActionId = database.sql("select id from action where action_key='view'")
        .query(UUID.class).single();
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

  private UUID directoryGroup() {
    UUID groupId = database.sql(
        "insert into directory_group(issuer,external_id,normalized_path,display_name)"
        + " values('https://issuer.test','cn=recon','/recon','Reconciliation') returning id")
        .query(UUID.class).single();
    database.sql("insert into user_group_membership(user_id,group_id) values(:u,:g)")
        .param("u", userId).param("g", groupId).update();
    return groupId;
  }

  private UUID accessGroup() {
    UUID accessGroupId = database.sql(
        "insert into access_group(code,name,created_by)"
        + " values('RECON_OU','Reconciliation OU','test') returning id")
        .query(UUID.class).single();
    database.sql(
        "insert into effective_group_membership(user_id,access_group_id,source_type,source_id)"
        + " values(:u,:g,'OU_RULE',:source)")
        .param("u", userId).param("g", accessGroupId).param("source", UUID.randomUUID()).update();
    return accessGroupId;
  }

  private UUID role() {
    return database.sql("insert into application_role(role_key,name_fa,name_en)"
        + " values('recon.auditor','حسابرس','Auditor') returning id")
        .query(UUID.class).single();
  }

  private void assignRoleToUser(UUID roleId) {
    database.sql("insert into user_role_assignment(user_id,role_id,assigned_by)"
        + " values(:u,:r,'test')").param("u", userId).param("r", roleId).update();
  }
}

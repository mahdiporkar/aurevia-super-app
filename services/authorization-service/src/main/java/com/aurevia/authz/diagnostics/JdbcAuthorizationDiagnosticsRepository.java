package com.aurevia.authz.diagnostics;

import static com.aurevia.authz.diagnostics.AuthorizationDiagnostics.*;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcAuthorizationDiagnosticsRepository implements AuthorizationDiagnosticsRepository {

  /*
   * The canonical OpenFGA object of a registry row. Kept identical to the projection and
   * reconciliation expressions so a diagnostic can never disagree with what was written.
   */
  private static final String CANONICAL_OBJECT =
      "case r.type"
      + " when 'APPLICATION' then 'application:'||regexp_replace(r.resource_key,'^application:','')"
      + " when 'EXTERNAL_RESOURCE' then 'external_resource:'"
      + "   ||replace(regexp_replace(r.resource_key,'^(external:|external_resource:)',''),':','/')"
      + " else 'resource:'||replace(r.resource_key,':','/') end";

  /*
   * Latest projection attempt for a grant. Identical to the Admin grant listing so the
   * diagnostic and Access Studio never report different projection states.
   */
  private static final String PROJECTION =
      " left join lateral ("
      + " select e.event_type,e.processed_at,e.dead_lettered_at,e.attempts,e.last_error"
      + " from outbox_event e"
      + " where e.aggregate_type='grant' and e.aggregate_id=g.id"
      + "   and e.event_type in ('GRANT_WRITE','GRANT_DELETE')"
      + " order by e.sequence desc limit 1) projection on true ";

  private static final String PROJECTION_STATUS =
      "case when projection.event_type is null then 'UNKNOWN'"
      + " when projection.dead_lettered_at is not null then 'FAILED'"
      + " when projection.processed_at is not null then"
      + "   case when projection.event_type='GRANT_DELETE' then 'REVOKED' else 'APPLIED' end"
      + " when projection.attempts>0 then 'RETRYING' else 'PENDING' end";

  private final JdbcClient database;

  JdbcAuthorizationDiagnosticsRepository(JdbcClient database) { this.database = database; }

  @Override public Optional<ResourceState> resource(String resourceOrObject, String actionKey) {
    return database.sql(
        "select r.id,r.resource_key as \"resourceKey\",r.type::text as \"type\","
        + " r.status::text as \"status\",r.visibility_enabled as \"visibilityEnabled\","
        + " exists(select 1 from resource_action ra join action a on a.id=ra.action_id"
        + "   where ra.resource_id=r.id and a.action_key=:action) as \"actionAttached\","
        + " r.panel_id as \"panelId\""
        + " from resource r"
        + " where r.resource_key=:resource or " + CANONICAL_OBJECT + "=:resource"
        + " order by (r.resource_key=:resource) desc limit 1")
        .param("resource", resourceOrObject).param("action", actionKey)
        .query((rs, row) -> new ResourceState(uuid(rs, "id"), rs.getString("resourceKey"),
            rs.getString("type"), rs.getString("status"), rs.getBoolean("visibilityEnabled"),
            rs.getBoolean("actionAttached"), uuid(rs, "panelId"))).optional();
  }

  /**
   * Every ACTIVE grant that can reach this subject for the requested resource or any of its
   * ancestors, because the OpenFGA model inherits {@code can_*} through {@code parent}.
   */
  @Override
  public List<GrantExplanation> contributingGrants(UUID resourceId, String canonicalSubject) {
    return database.sql(
        "with recursive chain as ("
        + " select id,parent_id,resource_key,0 as depth from resource where id=:resource"
        + " union all"
        + " select r.id,r.parent_id,r.resource_key,chain.depth+1"
        + " from resource r join chain on r.id=chain.parent_id)"
        + " select g.id,g.subject_type::text as \"subjectType\","
        + " coalesce(u.username,dg.display_name,ag.name,ar.role_key) as \"subjectLabel\","
        + " chain.resource_key as \"grantedResourceKey\",a.action_key as \"actionKey\","
        + " g.relation,g.status::text as \"status\",g.expires_at as \"expiresAt\","
        + PROJECTION_STATUS + " as \"projectionStatus\","
        + " projection.last_error as \"projectionError\""
        + " from authorization_grant g"
        + " join chain on chain.id=g.resource_id"
        + " join action a on a.id=g.action_id"
        + " left join app_user u on g.subject_type='USER' and u.id=g.subject_id"
        + " left join directory_group dg on g.subject_type='GROUP' and dg.id=g.subject_id"
        + " left join access_group ag on g.subject_type='ACCESS_GROUP' and ag.id=g.subject_id"
        + " left join application_role ar on g.subject_type='ROLE' and ar.id=g.subject_id"
        + PROJECTION
        + " where g.status='ACTIVE' and (g.expires_at is null or g.expires_at>now()) and ("
        + "   (g.subject_type='USER' and u.canonical_user_id=:subject)"
        + "   or (g.subject_type='GROUP' and dg.status='ACTIVE' and exists("
        + "     select 1 from user_group_membership m join app_user mu on mu.id=m.user_id"
        + "     where m.group_id=dg.id and mu.canonical_user_id=:subject))"
        + "   or (g.subject_type='ACCESS_GROUP' and ag.active and exists("
        + "     select 1 from effective_group_membership em join app_user eu on eu.id=em.user_id"
        + "     where em.access_group_id=ag.id and em.active"
        + "       and eu.canonical_user_id=:subject))"
        + "   or (g.subject_type='ROLE' and ar.status='ACTIVE' and exists("
        + "     select 1 from user_role_assignment ura join app_user ru on ru.id=ura.user_id"
        + "     where ura.role_id=ar.id and ru.canonical_user_id=:subject"
        + "       and (ura.expires_at is null or ura.expires_at>now())"
        + "     union all"
        + "     select 1 from group_role_assignment gra"
        + "     join directory_group rg on rg.id=gra.group_id and rg.status='ACTIVE'"
        + "     join user_group_membership m on m.group_id=gra.group_id"
        + "     join app_user gu on gu.id=m.user_id"
        + "     where gra.role_id=ar.id and gu.canonical_user_id=:subject"
        + "       and (gra.expires_at is null or gra.expires_at>now())"
        + "     union all"
        + "     select 1 from access_group_role_assignment agra"
        + "     join access_group rag on rag.id=agra.access_group_id and rag.active"
        + "     join effective_group_membership em"
        + "       on em.access_group_id=agra.access_group_id and em.active"
        + "     join app_user au on au.id=em.user_id"
        + "     where agra.role_id=ar.id and au.canonical_user_id=:subject"
        + "       and (agra.expires_at is null or agra.expires_at>now()))))"
        + " order by chain.depth,g.subject_type::text,a.action_key")
        .param("resource", resourceId).param("subject", canonicalSubject)
        .query((rs, row) -> new GrantExplanation(uuid(rs, "id"), rs.getString("subjectType"),
            rs.getString("subjectLabel"), rs.getString("grantedResourceKey"),
            rs.getString("actionKey"), rs.getString("relation"), rs.getString("status"),
            instant(rs, "expiresAt"), rs.getString("projectionStatus"),
            rs.getString("projectionError"))).list();
  }

  @Override public List<MembershipExplanation> memberships(String canonicalSubject) {
    return database.sql(
        "select 'DIRECTORY_GROUP' as \"kind\",dg.id,dg.external_id as \"externalId\","
        + " dg.display_name as \"displayName\","
        + " 'group:directory/'||dg.id as \"openFgaObject\","
        + " dg.status='ACTIVE' as \"active\""
        + " from user_group_membership m"
        + " join app_user u on u.id=m.user_id"
        + " join directory_group dg on dg.id=m.group_id"
        + " where u.canonical_user_id=:subject"
        + " union all"
        + " select 'ACCESS_GROUP',ag.id,ag.code,ag.name,'group:'||lower(ag.code),ag.active"
        + " from effective_group_membership em"
        + " join app_user u on u.id=em.user_id"
        + " join access_group ag on ag.id=em.access_group_id"
        + " where u.canonical_user_id=:subject and em.active"
        + " order by 1,4")
        .param("subject", canonicalSubject)
        .query((rs, row) -> new MembershipExplanation(rs.getString("kind"), uuid(rs, "id"),
            rs.getString("externalId"), rs.getString("displayName"),
            rs.getString("openFgaObject"), rs.getBoolean("active"))).list();
  }

  @Override public List<RoleExplanation> roles(String canonicalSubject) {
    return database.sql(
        "select r.id,r.role_key as \"roleKey\",r.status::text as \"status\","
        + " 'USER' as \"via\",u.username as \"viaLabel\",x.expires_at as \"expiresAt\""
        + " from user_role_assignment x"
        + " join app_user u on u.id=x.user_id"
        + " join application_role r on r.id=x.role_id"
        + " where u.canonical_user_id=:subject"
        + " union all"
        + " select r.id,r.role_key,r.status::text,'DIRECTORY_GROUP',dg.display_name,x.expires_at"
        + " from group_role_assignment x"
        + " join directory_group dg on dg.id=x.group_id"
        + " join user_group_membership m on m.group_id=x.group_id"
        + " join app_user u on u.id=m.user_id"
        + " join application_role r on r.id=x.role_id"
        + " where u.canonical_user_id=:subject"
        + " union all"
        + " select r.id,r.role_key,r.status::text,'ACCESS_GROUP',ag.name,x.expires_at"
        + " from access_group_role_assignment x"
        + " join access_group ag on ag.id=x.access_group_id"
        + " join effective_group_membership em"
        + "   on em.access_group_id=x.access_group_id and em.active"
        + " join app_user u on u.id=em.user_id"
        + " join application_role r on r.id=x.role_id"
        + " where u.canonical_user_id=:subject"
        + " order by 2,4")
        .param("subject", canonicalSubject)
        .query((rs, row) -> new RoleExplanation(uuid(rs, "id"), rs.getString("roleKey"),
            rs.getString("status"), rs.getString("via"), rs.getString("viaLabel"),
            instant(rs, "expiresAt"), "role:" + rs.getString("roleKey"))).list();
  }

  @Override public Optional<PanelState> panel(UUID panelId) {
    if (panelId == null) return Optional.empty();
    return database.sql(
        "select p.id,p.code,p.slug,p.active,p.classification,"
        + " p.active_artifact_id as \"activeArtifactId\","
        + " a.validation_status as \"artifactValidationStatus\","
        + " a.manifest_snapshot is not null as \"manifestPresent\","
        + " p.discovery_resource_key as \"discoveryResourceKey\""
        + " from panel p left join ui_module_artifact a on a.id=p.active_artifact_id"
        + " where p.id=:panel")
        .param("panel", panelId).query((rs, row) -> panelState(rs)).optional();
  }

  /**
   * Collapses the artifact lifecycle into one stated cause so a hidden micro frontend is never
   * silently hidden. Ordered from the earliest missing precondition to the ready state.
   */
  private static PanelState panelState(ResultSet rs) throws SQLException {
    boolean active = rs.getBoolean("active");
    UUID artifactId = uuid(rs, "activeArtifactId");
    String validation = rs.getString("artifactValidationStatus");
    boolean manifest = rs.getBoolean("manifestPresent");
    String readiness;
    if (!active) readiness = "PANEL_DISABLED";
    else if (artifactId == null) readiness = "NO_ACTIVE_ARTIFACT";
    else if (!"VALID".equals(validation)) readiness = "ARTIFACT_NOT_VALID";
    else if (!manifest) readiness = "MANIFEST_MISSING";
    else readiness = "READY";
    return new PanelState(uuid(rs, "id"), rs.getString("code"), rs.getString("slug"), active,
        rs.getString("classification"), artifactId, validation, manifest,
        rs.getString("discoveryResourceKey"), readiness);
  }

  private static UUID uuid(ResultSet rs, String name) throws SQLException {
    return rs.getObject(name, UUID.class);
  }

  private static Instant instant(ResultSet rs, String name) throws SQLException {
    var timestamp = rs.getTimestamp(name);
    return timestamp == null ? null : timestamp.toInstant();
  }
}

package com.aurevia.authz.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import com.aurevia.authz.observability.AuditTrail;

@RestController
@RequestMapping("/internal/v1/registry")
public class IdentityAdminController {
  private final JdbcClient database;
  private final AuditTrail auditTrail;

  public IdentityAdminController(JdbcClient database,AuditTrail auditTrail) {
    this.database = database;
    this.auditTrail = auditTrail;
  }

  @GetMapping("/groups")
  List<Map<String, Object>> groups() {
    return database.sql("""
        select id, issuer, external_id, normalized_path, display_name,
               parent_id, status, sync_at, version
        from directory_group order by normalized_path
        """).query().listOfRows();
  }

  @GetMapping("/roles")
  List<Map<String, Object>> roles() {
    return database.sql("""
        select id, role_key, name_fa, name_en, status, version
        from application_role order by role_key
        """).query().listOfRows();
  }

  @GetMapping("/role-assignments")
  List<Map<String, Object>> roleAssignments() {
    return database.sql("""
        select 'USER' subject_type, ura.user_id subject_id, u.display_name subject_name,
               ura.role_id, r.role_key, ura.expires_at
        from user_role_assignment ura join app_user u on u.id=ura.user_id
        join application_role r on r.id=ura.role_id
        union all
        select 'GROUP', gra.group_id, g.display_name, gra.role_id, r.role_key, gra.expires_at
        from group_role_assignment gra join directory_group g on g.id=gra.group_id
        join application_role r on r.id=gra.role_id
        order by subject_name, role_key
        """).query().listOfRows();
  }

  @PostMapping("/roles")
  @ResponseStatus(HttpStatus.CREATED)
  @Transactional
  Map<String, Object> createRole(@Valid @RequestBody RoleWrite request) {
    UUID id = UUID.randomUUID();
    database.sql("""
        insert into application_role(id, role_key, name_fa, name_en)
        values (:id, :key, :fa, :en)
        """).param("id", id).param("key", request.roleKey())
        .param("fa", request.nameFa()).param("en", request.nameEn()).update();
    audit("ROLE_CREATED", "role", request.roleKey());
    return Map.of("id", id, "version", 0);
  }

  @PostMapping("/role-assignments")
  @ResponseStatus(HttpStatus.CREATED)
  @Transactional
  void assignRole(@Valid @RequestBody RoleAssignment request) {
    if ("USER".equals(request.subjectType())) {
      database.sql("""
          insert into user_role_assignment(user_id, role_id, expires_at)
          values (:subject, :role, :expires)
          on conflict(user_id, role_id) do update set expires_at=excluded.expires_at
          """).param("subject", request.subjectId()).param("role", request.roleId())
          .param("expires", request.expiresAt()).update();
    } else if ("GROUP".equals(request.subjectType())) {
      database.sql("""
          insert into group_role_assignment(group_id, role_id, expires_at)
          values (:subject, :role, :expires)
          on conflict(group_id, role_id) do update set expires_at=excluded.expires_at
          """).param("subject", request.subjectId()).param("role", request.roleId())
          .param("expires", request.expiresAt()).update();
    } else {
      throw new IllegalArgumentException("subjectType must be USER or GROUP");
    }
    enqueueAssignment(request.subjectType(), request.subjectId(), request.roleId(),
        "ROLE_ASSIGNMENT_WRITE");
    audit("ROLE_ASSIGNED", request.subjectType().toLowerCase(), request.subjectId().toString());
  }

  @DeleteMapping("/role-assignments/{subjectType}/{subjectId}/{roleId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Transactional
  void revokeRole(@PathVariable String subjectType, @PathVariable UUID subjectId,
      @PathVariable UUID roleId) {
    String type = subjectType.toUpperCase();
    enqueueAssignment(type, subjectId, roleId, "ROLE_ASSIGNMENT_DELETE");
    if ("USER".equals(type)) {
      database.sql("delete from user_role_assignment where user_id=:subject and role_id=:role")
          .param("subject", subjectId).param("role", roleId).update();
    } else if ("GROUP".equals(type)) {
      database.sql("delete from group_role_assignment where group_id=:subject and role_id=:role")
          .param("subject", subjectId).param("role", roleId).update();
    } else {
      throw new IllegalArgumentException("subjectType must be USER or GROUP");
    }
    audit("ROLE_REVOKED", type.toLowerCase(), subjectId.toString());
  }

  private void enqueueAssignment(String subjectType, UUID subjectId, UUID roleId,
      String eventType) {
    database.sql("""
        insert into outbox_event(aggregate_type,aggregate_id,event_type,payload,idempotency_key)
        select 'role-assignment',:role,:event,
          jsonb_build_object('user',case :type when 'USER' then 'user:'||u.external_id
            else 'group:'||g.external_id||'#member' end,
            'relation','assignee','object','role:'||r.role_key),
          :event||':'||:type||':'||:subject||':'||:role||':'||gen_random_uuid()
        from application_role r
        left join app_user u on :type='USER' and u.id=:subject
        left join directory_group g on :type='GROUP' and g.id=:subject
        where r.id=:role on conflict(idempotency_key) do nothing
        """).param("role", roleId).param("event", eventType)
        .param("type", subjectType.toUpperCase()).param("subject", subjectId).update();
  }

  private void audit(String event, String type, String key) {
    database.sql("""
        insert into audit_event(actor_key,event_type,target_type,target_key,correlation_id)
        values ('bff-admin',:event,:type,:key,:correlation)
        """).param("event", event).param("type", type).param("key", key)
        .param("correlation", UUID.randomUUID().toString()).update();
    auditTrail.success("IDENTITY",event.toLowerCase().replace('_','.'),type,key,type,key,key,event,null,Map.of("target",key));
  }

  public record RoleWrite(@NotBlank String roleKey, @NotBlank String nameFa, @NotBlank String nameEn) {}
  public record RoleAssignment(@NotBlank String subjectType, @NotNull UUID subjectId,
      @NotNull UUID roleId, Instant expiresAt) {}
}

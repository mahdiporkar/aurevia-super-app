package com.aurevia.authz.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.HashSet;
import java.util.Set;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Idempotent projection of an authenticated OIDC identity and its directory groups. */
@RestController
@RequestMapping("/internal/v1/identity")
public class IdentitySyncController {
  private final JdbcClient database;

  public IdentitySyncController(JdbcClient database) {
    this.database = database;
  }

  @PostMapping("/login-sync")
  @Transactional
  public Map<String, Object> loginSync(@Valid @RequestBody LoginIdentity identity) {
    UUID userId = database.sql("""
        insert into app_user(issuer, external_id, username, display_name, email)
        values (:issuer, :subject, :username, :displayName, :email)
        on conflict(issuer, external_id) do update set
          username=excluded.username, display_name=excluded.display_name,
          email=excluded.email, status='ACTIVE', updated_at=now()
        returning id
        """).param("issuer", identity.issuer()).param("subject", identity.subject())
        .param("username", identity.username()).param("displayName", identity.displayName())
        .param("email", identity.email()).query(UUID.class).single();

    Set<UUID> previousGroups = new HashSet<>(database.sql(
        "select group_id from user_group_membership where user_id=:userId")
        .param("userId", userId).query(UUID.class).list());
    Set<UUID> currentGroups = new HashSet<>();
    for (DirectoryGroup group : identity.groups() == null ? List.<DirectoryGroup>of() : identity.groups()) {
      UUID groupId = database.sql("""
          insert into directory_group(issuer, external_id, normalized_path, display_name, sync_at)
          values (:issuer, :externalId, :path, :displayName, now())
          on conflict(issuer, external_id) do update set
            normalized_path=excluded.normalized_path, display_name=excluded.display_name,
            status='ACTIVE', sync_at=now()
          returning id
          """).param("issuer", identity.issuer()).param("externalId", group.externalId())
          .param("path", normalizePath(group.path())).param("displayName", group.displayName())
          .query(UUID.class).single();
      database.sql("""
          insert into user_group_membership(user_id, group_id) values (:userId, :groupId)
          on conflict do nothing
          """).param("userId", userId).param("groupId", groupId).update();
      currentGroups.add(groupId);
      if (!previousGroups.contains(groupId)) {
        enqueueMembership(userId, groupId, identity.subject(), group.externalId(),
            "GROUP_MEMBERSHIP_WRITE");
      }
    }
    for (UUID removed : previousGroups) {
      if (!currentGroups.contains(removed)) {
        String groupExternalId = database.sql("select external_id from directory_group where id=:id")
            .param("id", removed).query(String.class).single();
        enqueueMembership(userId, removed, identity.subject(), groupExternalId,
            "GROUP_MEMBERSHIP_DELETE");
        database.sql("delete from user_group_membership where user_id=:user and group_id=:group")
            .param("user", userId).param("group", removed).update();
      }
    }
    return Map.of("userId", userId, "groups", identity.groups() == null ? 0 : identity.groups().size());
  }

  private static String normalizePath(String path) {
    String normalized = path == null ? "/" : path.trim().replace('\\', '/');
    if (!normalized.startsWith("/")) normalized = "/" + normalized;
    return normalized.replaceAll("/{2,}", "/");
  }

  private void enqueueMembership(UUID userId, UUID groupId, String subject, String groupExternalId,
      String event) {
    database.sql("""
        insert into outbox_event(aggregate_type,aggregate_id,event_type,payload,idempotency_key)
        values('group-membership',:user,:event,
          jsonb_build_object('user','user:'||:subject,'relation','member',
            'object','group:'||:groupExternal),
          :event||':'||:user||':'||:group||':'||gen_random_uuid())
        """).param("user", userId).param("group", groupId).param("event", event)
        .param("subject", subject).param("groupExternal", groupExternalId).update();
  }

  public record LoginIdentity(@NotBlank String issuer, @NotBlank String subject,
      @NotBlank String username, String displayName, String email, List<DirectoryGroup> groups) {}
  public record DirectoryGroup(@NotBlank String externalId, @NotBlank String path,
      @NotBlank String displayName) {}
}

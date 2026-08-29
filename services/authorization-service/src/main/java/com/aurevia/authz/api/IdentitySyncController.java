package com.aurevia.authz.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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

    database.sql("delete from user_group_membership where user_id=:userId")
        .param("userId", userId).update();
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
    }
    return Map.of("userId", userId, "groups", identity.groups() == null ? 0 : identity.groups().size());
  }

  private static String normalizePath(String path) {
    String normalized = path == null ? "/" : path.trim().replace('\\', '/');
    if (!normalized.startsWith("/")) normalized = "/" + normalized;
    return normalized.replaceAll("/{2,}", "/");
  }

  public record LoginIdentity(@NotBlank String issuer, @NotBlank String subject,
      @NotBlank String username, String displayName, String email, List<DirectoryGroup> groups) {}
  public record DirectoryGroup(@NotBlank String externalId, @NotBlank String path,
      @NotBlank String displayName) {}
}

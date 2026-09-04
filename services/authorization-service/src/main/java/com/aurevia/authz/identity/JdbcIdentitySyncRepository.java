package com.aurevia.authz.identity;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcIdentitySyncRepository implements IdentitySyncRepository {
  private final JdbcClient database;
  JdbcIdentitySyncRepository(JdbcClient database) { this.database=database; }

  @Override public Set<UUID> membershipGroupIds(UUID userId) {
    return new HashSet<>(database.sql(
        "select group_id from user_group_membership where user_id=:user")
        .param("user",userId).query(UUID.class).list());
  }
  @Override public Optional<UUID> directoryGroupId(String issuer,String externalId) {
    return database.sql("""
        select id from directory_group where issuer=:issuer and external_id=:external
        """).param("issuer",issuer).param("external",externalId).query(UUID.class).optional();
  }
  @Override public long incrementMembershipVersion(UUID userId) {
    return database.sql("""
        update app_user set membership_version=membership_version+1,updated_at=now()
        where id=:id returning membership_version
        """).param("id",userId).query(Long.class).single();
  }
  @Override public long membershipVersion(UUID userId) {
    return database.sql("select membership_version from app_user where id=:id")
        .param("id",userId).query(Long.class).single();
  }
  @Override public String subjectKey(UUID userId) {
    return database.sql("select subject_key from app_user where id=:id")
        .param("id",userId).query(String.class).single();
  }
  @Override public UUID upsertDirectoryGroup(String issuer,String externalId,String path,
      String displayName) {
    return database.sql("""
        insert into directory_group(issuer,external_id,normalized_path,display_name,sync_at)
        values(:issuer,:externalId,:path,:displayName,now())
        on conflict(issuer,external_id) do update set
          normalized_path=excluded.normalized_path,display_name=excluded.display_name,
          status='ACTIVE',sync_at=now() returning id
        """).param("issuer",issuer).param("externalId",externalId).param("path",path)
        .param("displayName",displayName).query(UUID.class).single();
  }
  @Override public void addMembership(UUID userId,UUID groupId) {
    database.sql("""
        insert into user_group_membership(user_id,group_id) values(:user,:group)
        on conflict do nothing
        """).param("user",userId).param("group",groupId).update();
  }
  @Override public String directoryGroupExternalId(UUID groupId) {
    return database.sql("select external_id from directory_group where id=:id")
        .param("id",groupId).query(String.class).single();
  }
  @Override public void removeMembership(UUID userId,UUID groupId) {
    database.sql("delete from user_group_membership where user_id=:user and group_id=:group")
        .param("user",userId).param("group",groupId).update();
  }
  @Override public void enqueueMembership(UUID userId,UUID groupId,String subjectKey,
      String groupExternalId,String event,long version) {
    database.sql("""
        insert into outbox_event(aggregate_type,aggregate_id,event_type,payload,idempotency_key)
        values('group-membership',:user,:event,
          jsonb_build_object('user','user:'||:subjectKey,'relation','member',
            'object','group:'||:groupExternal),
          :event||':'||:user||':'||:group||':'||:version)
        """).param("user",userId).param("group",groupId).param("event",event)
        .param("subjectKey",subjectKey).param("groupExternal",groupExternalId)
        .param("version",version).update();
  }
}

package com.aurevia.authz.directory;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcActiveDirectorySyncRepository implements ActiveDirectorySyncRepository {
  private final JdbcClient database;
  JdbcActiveDirectorySyncRepository(JdbcClient database) { this.database=database; }

  @Override public void startRun(UUID id) {
    database.sql("insert into directory_sync_run(id,source,status) values(:id,'ACTIVE_DIRECTORY','RUNNING')")
      .param("id",id).update();
  }
  @Override public void completeRun(UUID id,int ous,int users) { database.sql("""
      update directory_sync_run set status='SUCCEEDED',completed_at=now(),
        discovered_ous=:ous,discovered_users=:users where id=:id
      """).param("id",id).param("ous",ous).param("users",users).update(); }
  @Override public void failRun(UUID id,int ous,int users,String safeError) { database.sql("""
      update directory_sync_run set status='FAILED',completed_at=now(),
        discovered_ous=:ous,discovered_users=:users,safe_error=:error where id=:id
      """).param("id",id).param("ous",ous).param("users",users)
      .param("error",safeError).update(); }
  @Override public RemovalStats ouRemovalStats(String issuer,Set<String> ids) {
    int active=database.sql("select count(*) from directory_ou where issuer=:issuer and active")
      .param("issuer",issuer).query(Integer.class).single();
    int missing=database.sql("""
      select count(*) from directory_ou where issuer=:issuer and active
        and external_id<>all(cast(:ids as text[]))
      """).param("issuer",issuer).param("ids",array(ids)).query(Integer.class).single();
    return new RemovalStats(active,missing);
  }
  @Override public UUID upsertOu(String issuer,String external,String dn,String path,String name) {
    return database.sql("""
      insert into directory_ou(issuer,external_id,external_dn,external_path,name)
      values(:issuer,:external,:dn,:path,:name)
      on conflict(issuer,external_id) do update set external_dn=excluded.external_dn,
        external_path=excluded.external_path,name=excluded.name,active=true,last_synced_at=now(),
        updated_at=now(),version=directory_ou.version+1 returning id
      """).param("issuer",issuer).param("external",external).param("dn",dn)
      .param("path",path).param("name",name).query(UUID.class).single();
  }
  @Override public void updateOuParent(UUID id,UUID parent) {
    database.sql("update directory_ou set parent_ou_id=:parent where id=:id")
      .param("id",id).param("parent",parent).update();
  }
  @Override public void deactivateMissingOus(String issuer,Set<String> ids) { database.sql("""
      update directory_ou set active=false,updated_at=now(),version=version+1
      where issuer=:issuer and active and external_id<>all(cast(:ids as text[]))
      """).param("issuer",issuer).param("ids",array(ids)).update(); }
  @Override public List<UUID> linkedUsers(String issuer,String external) { return database.sql("""
      select id from app_user where issuer=:issuer and directory_external_id=:external
      """).param("issuer",issuer).param("external",external).query(UUID.class).list(); }
  @Override public RemovalStats userRemovalStats(String issuer,Set<String> ids) {
    int active=database.sql("""
      select count(distinct a.user_id) from user_ou_assignment a join app_user u on u.id=a.user_id
      where a.active and u.issuer=:issuer and u.directory_external_id is not null
      """).param("issuer",issuer).query(Integer.class).single();
    int missing=database.sql("""
      select count(distinct a.user_id) from user_ou_assignment a join app_user u on u.id=a.user_id
      where a.active and u.issuer=:issuer and u.directory_external_id is not null
        and u.directory_external_id<>all(cast(:ids as text[]))
      """).param("issuer",issuer).param("ids",array(ids)).query(Integer.class).single();
    return new RemovalStats(active,missing);
  }
  @Override public List<UUID> deactivateMissingUserAssignments(String issuer,Set<String> ids) {
    List<UUID> removed=database.sql("""
      select distinct a.user_id from user_ou_assignment a join app_user u on u.id=a.user_id
      where a.active and u.issuer=:issuer and u.directory_external_id is not null
        and u.directory_external_id<>all(cast(:ids as text[]))
      """).param("issuer",issuer).param("ids",array(ids)).query(UUID.class).list();
    if(!removed.isEmpty()) database.sql("""
      update user_ou_assignment a set active=false,removed_at=now(),updated_at=now()
      from app_user u where u.id=a.user_id and a.active and u.issuer=:issuer
        and u.directory_external_id is not null
        and u.directory_external_id<>all(cast(:ids as text[]))
      """).param("issuer",issuer).param("ids",array(ids)).update();
    return removed;
  }
  private static String[] array(Set<String> values) { return values.toArray(String[]::new); }
}

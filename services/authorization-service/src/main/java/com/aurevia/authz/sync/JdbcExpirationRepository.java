package com.aurevia.authz.sync;

import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcExpirationRepository implements ExpirationRepository {
  private final JdbcClient database;
  JdbcExpirationRepository(JdbcClient database) { this.database = database; }

  @Override public List<ExpiredGrant> expiredGrants() {
    return database.sql("""
        select id,version from authorization_grant
        where status='ACTIVE' and expires_at is not null and expires_at<=now()
        order by expires_at for update skip locked
        """).query(ExpiredGrant.class).list();
  }

  @Override public List<ExpiredRoleAssignment> expiredRoleAssignments() {
    // Subject type names match IdentityRepository so the existing projection code is reused.
    return database.sql("""
        select * from (
          select 'USER' as "subjectType",user_id as "subjectId",role_id as "roleId",version
          from user_role_assignment where expires_at is not null and expires_at<=now()
          for update skip locked
        ) u
        union all
        select * from (
          select 'DIRECTORY_GROUP',group_id,role_id,version
          from group_role_assignment where expires_at is not null and expires_at<=now()
          for update skip locked
        ) g
        union all
        select * from (
          select 'ACCESS_GROUP',access_group_id,role_id,version
          from access_group_role_assignment where expires_at is not null and expires_at<=now()
          for update skip locked
        ) a
        """).query(ExpiredRoleAssignment.class).list();
  }

  @Override public int archiveGrant(UUID id) {
    return database.sql("""
        update authorization_grant set status='ARCHIVED',version=version+1
        where id=:id and status='ACTIVE'
        """).param("id", id).update();
  }
}

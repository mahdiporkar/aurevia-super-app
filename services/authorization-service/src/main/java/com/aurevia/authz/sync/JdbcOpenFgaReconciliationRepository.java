package com.aurevia.authz.sync;

import java.util.LinkedHashSet;
import java.util.Set;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcOpenFgaReconciliationRepository implements OpenFgaReconciliationRepository {
  private final JdbcClient database;
  JdbcOpenFgaReconciliationRepository(JdbcClient database) { this.database=database; }

  @Override public Set<ReconciliationTuple> expectedTuples() {
    Set<ReconciliationTuple> tuples=new LinkedHashSet<>();
    tuples.addAll(database.sql("""
      select distinct "user",relation,object from catalog_grant_tuple
      """).query(ReconciliationTuple.class).list());
    tuples.addAll(database.sql("""
      select case p.type when 'APPLICATION' then 'application:'||regexp_replace(p.resource_key,'^application:','')
        when 'EXTERNAL_RESOURCE' then 'external_resource:'||replace(regexp_replace(p.resource_key,'^external_resource:',''),':','/')
        else 'resource:'||replace(p.resource_key,':','/') end "user",'parent' relation,
        case c.type when 'APPLICATION' then 'application:'||regexp_replace(c.resource_key,'^application:','')
        when 'EXTERNAL_RESOURCE' then 'external_resource:'||replace(regexp_replace(c.resource_key,'^external_resource:',''),':','/')
        else 'resource:'||replace(c.resource_key,':','/') end object
      from resource c join resource p on p.id=c.parent_id where c.status='ACTIVE'
      """).query(ReconciliationTuple.class).list());
    tuples.addAll(database.sql("""
      select 'user:'||u.canonical_user_id "user",'member' relation,'group:directory/'||g.id object
      from user_group_membership m join app_user u on u.id=m.user_id
      join directory_group g on g.id=m.group_id where g.status='ACTIVE'
      """).query(ReconciliationTuple.class).list());
    tuples.addAll(database.sql("""
      select distinct 'user:'||u.canonical_user_id "user",'member' relation,'group:'||lower(g.code) object
      from effective_group_membership m join app_user u on u.id=m.user_id
      join access_group g on g.id=m.access_group_id where m.active and g.active
      """).query(ReconciliationTuple.class).list());
    tuples.addAll(database.sql("""
      select 'group:'||lower(a.code)||'#member' "user",'viewer' relation,
        'application:aurevia/'||p.slug object
      from application_group_grant g join access_group a on a.id=g.access_group_id
      join panel p on p.id=g.application_id
      where g.revoked_at is null and a.active and p.active
      """).query(ReconciliationTuple.class).list());
    tuples.addAll(database.sql("""
      select 'user:'||u.canonical_user_id "user",'assignee' relation,'role:'||r.role_key object
      from user_role_assignment x join app_user u on u.id=x.user_id
      join application_role r on r.id=x.role_id where r.status='ACTIVE'
        and (x.expires_at is null or x.expires_at>now())
      union all
      select 'group:directory/'||g.id||'#member','assignee','role:'||r.role_key
      from group_role_assignment x join directory_group g on g.id=x.group_id
      join application_role r on r.id=x.role_id where r.status='ACTIVE' and g.status='ACTIVE'
        and (x.expires_at is null or x.expires_at>now())
      union all
      select 'group:'||lower(g.code)||'#member','assignee','role:'||r.role_key
      from access_group_role_assignment x join access_group g on g.id=x.access_group_id
      join application_role r on r.id=x.role_id where r.status='ACTIVE' and g.active
        and (x.expires_at is null or x.expires_at>now())
      """).query(ReconciliationTuple.class).list());
    return tuples;
  }
}

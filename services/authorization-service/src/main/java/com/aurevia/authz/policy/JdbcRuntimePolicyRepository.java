package com.aurevia.authz.policy;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcRuntimePolicyRepository implements RuntimePolicyRepository {
  private final JdbcClient database;
  JdbcRuntimePolicyRepository(JdbcClient database) { this.database=database; }
  @Override public Optional<ResourceContext> activeResource(String key,String registryKey) {
    return database.sql("""
        select r.id,r.classification,r.owner_domain as "ownerDomain",
          coalesce(sa.owner_external_id,r.external_id) as "ownerId"
        from resource r left join superset_asset sa on sa.resource_id=r.id
        where (r.resource_key=:key or r.resource_key=:registryKey) and r.status='ACTIVE'
        """).param("key",key).param("registryKey",registryKey)
        .query(ResourceContext.class).optional();
  }
  @Override public List<PolicyRow> activePolicies(UUID resourceId,String actionKey) {
    return database.sql("""
        select p.policy_key as "policyKey",p.version,c.expression::text expression,
          p.obligations::text obligations from data_policy p
        left join condition_definition c on c.id=p.condition_id and c.active=true
        join action a on a.id=p.action_id
        where p.resource_id=:resource and p.active=true and a.action_key=:action
        order by p.policy_key
        """).param("resource",resourceId).param("action",actionKey)
        .query(PolicyRow.class).list();
  }
  @Override public Optional<OrgContext> primaryOrganization(String issuer,String subject) {
    return database.sql("""
        select g.external_id as "orgUnit",g.normalized_path branch
        from app_user u join user_group_membership m on m.user_id=u.id
        join directory_group g on g.id=m.group_id and g.status='ACTIVE'
        where u.issuer=:issuer and u.external_id=:subject
        order by g.normalized_path limit 1
        """).param("issuer",issuer).param("subject",subject)
        .query(OrgContext.class).optional();
  }
}

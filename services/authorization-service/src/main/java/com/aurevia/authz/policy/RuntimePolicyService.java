package com.aurevia.authz.policy;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

/** Loads mandatory policies and evaluates them only against server-derived context. */
@Service
public class RuntimePolicyService {
  private final JdbcClient database;
  private final ObjectMapper json;
  private final StructuredPolicyEvaluator evaluator;

  public RuntimePolicyService(JdbcClient database, ObjectMapper json,
      StructuredPolicyEvaluator evaluator) {
    this.database = database;
    this.json = json;
    this.evaluator = evaluator;
  }

  public Evaluation evaluate(String subject, String canonicalObject, String action) {
    try {
      ResourceContext resource = loadResource(canonicalObject);
      Map<String, Object> trusted = trustedContext(subject, resource);
      List<PolicyRow> policies = database.sql("""
          select p.policy_key,p.version,c.expression::text expression,p.obligations::text obligations
          from data_policy p
          left join condition_definition c on c.id=p.condition_id and c.active=true
          join action a on a.id=p.action_id
          where p.resource_id=:resource and p.active=true and a.action_key=:action
          order by p.policy_key
          """).param("resource", resource.id()).param("action", action)
          .query(PolicyRow.class).list();
      Map<String, Object> combined = new LinkedHashMap<>();
      List<String> applied = new ArrayList<>();
      for (PolicyRow policy : policies) {
        if (policy.expression() == null) return Evaluation.deny("POLICY_DEFINITION_MISSING", applied);
        List<StructuredPolicyEvaluator.Clause> clauses = json.readValue(policy.expression(),
            new TypeReference<>() {});
        Map<String, Object> obligations = json.readValue(policy.obligations(),
            new TypeReference<>() {});
        var result = evaluator.evaluate(clauses, trusted, obligations);
        applied.add(policy.policyKey() + ":" + policy.version());
        if (!result.allowed()) return Evaluation.deny(result.reasonCode(), applied);
        combined.putAll(result.obligations());
      }
      return new Evaluation(true, policies.isEmpty() ? "NO_APPLICABLE_POLICY" : "POLICY_ALLOWED",
          Map.copyOf(combined), List.copyOf(applied));
    } catch (RuntimeException | java.io.IOException invalid) {
      return Evaluation.deny("POLICY_EVALUATION_ERROR", List.of());
    }
  }

  private ResourceContext loadResource(String canonicalObject) {
    String key;
    if (canonicalObject.startsWith("application:")) {
      key = canonicalObject;
    } else if (canonicalObject.startsWith("external_resource:")) {
      key = "external_resource:" + canonicalObject.substring("external_resource:".length())
          .replace('/', ':');
    } else if (canonicalObject.startsWith("resource:")) {
      key = canonicalObject.substring("resource:".length());
    } else {
      throw new IllegalArgumentException("Unsupported canonical object");
    }
    return database.sql("""
        select r.id,r.classification,r.owner_domain,
          coalesce(sa.owner_external_id,r.external_id) owner_id
        from resource r left join superset_asset sa on sa.resource_id=r.id
        where r.resource_key=:key and r.status='ACTIVE'
        """).param("key", key).query(ResourceContext.class).optional()
        .orElseThrow(() -> new IllegalArgumentException("Resource is not registered"));
  }

  private Map<String, Object> trustedContext(String subject, ResourceContext resource) {
    Map<String, Object> context = new LinkedHashMap<>();
    put(context, "ownerId", resource.ownerId());
    put(context, "classification", resource.classification());
    put(context, "time", Instant.now().toString());
    database.sql("""
        select g.external_id org_unit,g.normalized_path branch
        from app_user u join user_group_membership m on m.user_id=u.id
        join directory_group g on g.id=m.group_id and g.status='ACTIVE'
        where u.external_id=:subject or u.username=:subject
        order by g.normalized_path limit 1
        """).param("subject", subject).query(OrgContext.class).optional().ifPresent(org -> {
          put(context, "orgUnit", org.orgUnit());
          put(context, "branch", org.branch());
        });
    return Map.copyOf(context);
  }

  private static void put(Map<String, Object> context, String key, Object value) {
    if (value != null) context.put(key, value);
  }

  record ResourceContext(java.util.UUID id,String classification,String ownerDomain,String ownerId) {}
  record OrgContext(String orgUnit,String branch) {}
  record PolicyRow(String policyKey,long version,String expression,String obligations) {}
  public record Evaluation(boolean allowed,String reasonCode,Map<String,Object> obligations,
      List<String> policies) {
    static Evaluation deny(String reason,List<String> policies) {
      return new Evaluation(false,reason,Map.of(),List.copyOf(policies));
    }
  }
}

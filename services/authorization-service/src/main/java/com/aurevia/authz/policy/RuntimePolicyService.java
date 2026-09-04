package com.aurevia.authz.policy;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Loads mandatory policies and evaluates them only against server-derived context. */
@Service
public class RuntimePolicyService {
  private static final Logger LOG = LoggerFactory.getLogger(RuntimePolicyService.class);
  private final RuntimePolicyRepository policies;
  private final ObjectMapper json;
  private final StructuredPolicyEvaluator evaluator;

  public RuntimePolicyService(RuntimePolicyRepository policies, ObjectMapper json,
      StructuredPolicyEvaluator evaluator) {
    this.policies = policies;
    this.json = json;
    this.evaluator = evaluator;
  }

  public Evaluation evaluate(String issuer, String subject, String canonicalObject, String action) {
    try {
      RuntimePolicyRepository.ResourceContext resource = loadResource(canonicalObject);
      Map<String, Object> trusted = trustedContext(issuer, subject, resource);
      List<RuntimePolicyRepository.PolicyRow> policies = this.policies.activePolicies(
          resource.id(),action);
      Map<String, Object> combined = new LinkedHashMap<>();
      List<String> applied = new ArrayList<>();
      for (RuntimePolicyRepository.PolicyRow policy : policies) {
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
      LOG.warn("Runtime policy evaluation failed for resource={} action={}",
          canonicalObject, action, invalid);
      return Evaluation.deny("POLICY_EVALUATION_ERROR", List.of());
    }
  }

  private RuntimePolicyRepository.ResourceContext loadResource(String canonicalObject) {
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
    // OpenFGA canonicalizes only the resource-type separator. The resource id itself may
    // legitimately contain dots or further slashes and must remain byte-for-byte stable.
    String registryKey = toRegistryKey(key);
    return policies.activeResource(key,registryKey)
        .orElseThrow(() -> new IllegalArgumentException(
            "Resource is not registered for canonical key '" + registryKey + "'"));
  }

  static String toRegistryKey(String canonicalId) {
    int typeSeparator = canonicalId.indexOf('/');
    int existingSeparator = canonicalId.indexOf(':');
    return typeSeparator < 0 || (existingSeparator >= 0 && existingSeparator < typeSeparator)
        ? canonicalId
        : canonicalId.substring(0, typeSeparator) + ':' + canonicalId.substring(typeSeparator + 1);
  }

  private Map<String, Object> trustedContext(String issuer, String subject,
      RuntimePolicyRepository.ResourceContext resource) {
    Map<String, Object> context = new LinkedHashMap<>();
    put(context, "ownerId", resource.ownerId());
    put(context, "classification", resource.classification());
    put(context, "time", Instant.now().toString());
    policies.primaryOrganization(issuer,subject).ifPresent(org -> {
          put(context, "orgUnit", org.orgUnit());
          put(context, "branch", org.branch());
        });
    return Map.copyOf(context);
  }

  private static void put(Map<String, Object> context, String key, Object value) {
    if (value != null) context.put(key, value);
  }

  public record Evaluation(boolean allowed,String reasonCode,Map<String,Object> obligations,
      List<String> policies) {
    static Evaluation deny(String reason,List<String> policies) {
      return new Evaluation(false,reason,Map.of(),List.copyOf(policies));
    }
  }
}

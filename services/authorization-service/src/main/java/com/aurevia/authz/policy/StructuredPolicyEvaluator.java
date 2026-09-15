package com.aurevia.authz.policy;

import java.time.Instant;
import java.util.*;
import org.springframework.stereotype.Service;

@Service
public class StructuredPolicyEvaluator {
  private static final Set<String> FIELDS = Set.of("ownerId","orgUnit","branch","classification","request.ipClass","time");
  private static final Set<String> OPERATORS = Set.of("eq","in","before","after");
  public Result evaluate(List<Clause> clauses, Map<String,Object> context, Map<String,Object> obligations) {
    if (clauses == null || context == null) return Result.deny("POLICY_CONTEXT_MISSING");
    try {
      for (Clause c : clauses) {
        if (!FIELDS.contains(c.field()) || !OPERATORS.contains(c.operator())) return Result.deny("POLICY_SCHEMA_INVALID");
        if (!context.containsKey(c.field()) || context.get(c.field()) == null) return Result.deny("POLICY_CONTEXT_MISSING");
        if (!matches(c, context.get(c.field()))) return Result.deny("POLICY_CONDITION_FAILED");
      }
      validateObligations(obligations);
      return new Result(true, "POLICY_ALLOWED", Map.copyOf(obligations == null ? Map.of() : obligations));
    } catch (RuntimeException invalid) { return Result.deny("POLICY_EVALUATION_ERROR"); }
  }
  private boolean matches(Clause c,Object actual) { return switch(c.operator()) { case "eq" -> Objects.equals(actual,c.value()); case "in" -> c.value() instanceof Collection<?> values && values.contains(actual); case "before" -> Instant.parse(String.valueOf(actual)).isBefore(Instant.parse(String.valueOf(c.value()))); case "after" -> Instant.parse(String.valueOf(actual)).isAfter(Instant.parse(String.valueOf(c.value()))); default -> false; }; }
  private void validateObligations(Map<String,Object> o) { if(o==null)return; var allowed=Set.of("rowFilters","allowedColumns","maskedColumns","maximumRows","exportAllowed","printAllowed","watermark");if(!allowed.containsAll(o.keySet()))throw new IllegalArgumentException("Unknown obligation"); }
  public record Clause(String field,String operator,Object value) {}
  public record Result(boolean allowed,String reasonCode,Map<String,Object> obligations) { static Result deny(String code){return new Result(false,code,Map.of());} }
}

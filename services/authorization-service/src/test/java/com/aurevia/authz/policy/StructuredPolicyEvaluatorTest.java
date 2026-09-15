package com.aurevia.authz.policy;
import static org.assertj.core.api.Assertions.*;import java.util.*;import org.junit.jupiter.api.Test;
class StructuredPolicyEvaluatorTest {
  final StructuredPolicyEvaluator evaluator=new StructuredPolicyEvaluator();
  @Test void returnsScopedObligation(){var result=evaluator.evaluate(List.of(new StructuredPolicyEvaluator.Clause("orgUnit","eq","tehran")),Map.of("orgUnit","tehran"),Map.of("rowFilters",Map.of("orgUnit","tehran"),"maximumRows",100));assertThat(result.allowed()).isTrue();assertThat(result.obligations()).containsKey("rowFilters");}
  @Test void failsClosedOnMissingContext(){assertThat(evaluator.evaluate(List.of(),null,Map.of()).allowed()).isFalse();}
  @Test void rejectsUnknownExecutablePolicyField(){assertThat(evaluator.evaluate(List.of(new StructuredPolicyEvaluator.Clause("spel","eq","permitAll")),Map.of("spel","permitAll"),Map.of()).allowed()).isFalse();}
}

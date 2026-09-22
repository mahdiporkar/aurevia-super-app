package com.aurevia.authz.docs;

import static org.assertj.core.api.Assertions.assertThat;

import io.swagger.v3.oas.annotations.Hidden;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Structural synchronization between the controllers and the Persian documentation catalogue.
 * Compares SETS in both directions: a new endpoint without documentation fails, and a catalogue
 * entry whose endpoint was renamed or removed fails too. Fixed endpoint counts are deliberately
 * not used. The runtime contract (paths, parameters, schemas, examples) is verified against the
 * generated document by {@code AuthorizationOpenApiContractIntegrationTest}.
 */
class OpenApiDocumentationCoverageTest {
  private static final Pattern PERSIAN = Pattern.compile("[\\u0600-\\u06FF]");

  /** Hidden controllers must be listed here with their reason; hiding to pass coverage is not allowed. */
  static final Set<String> INTENTIONALLY_HIDDEN = Set.of();

  @Test
  void controllersAndCatalogueDescribeExactlyTheSameOperations() throws Exception {
    Set<String> mapped = mappedOperations();
    Set<String> documented = new TreeSet<>(ApiDocumentationCatalog.documentedOperations());
    assertThat(documented).as("catalogue entries whose endpoint no longer exists (stale documentation)")
        .isSubsetOf(mapped);
    assertThat(mapped).as("endpoints without Persian documentation").isSubsetOf(documented);
    assertThat(mapped).isNotEmpty();
  }

  @Test
  void everyControllerHasATagAndEveryTagHasAController() throws Exception {
    Set<String> controllers = new TreeSet<>();
    for (Class<?> controller : controllers()) controllers.add(controller.getSimpleName());
    Set<String> tagged = new TreeSet<>(ApiDocumentationCatalog.tags().keySet());
    // AuthorizationDiagnosticsController shares the diagnostics tag through the default group.
    assertThat(tagged).as("tags for controllers that no longer exist").isSubsetOf(controllers);
    assertThat(controllers.stream().filter(name -> !tagged.contains(name)).toList())
        .as("controllers without a dedicated Swagger tag").containsExactly("AuthorizationDiagnosticsController");
  }

  @Test
  void summariesAreMeaningfulPersianAndUniqueWithinAController() {
    var seen = new LinkedHashSet<String>();
    for (String key : ApiDocumentationCatalog.documentedOperations()) {
      String summary = ApiDocumentationCatalog.summary(key);
      assertThat(summary).as(key).isNotBlank().hasSizeGreaterThan(8);
      assertThat(PERSIAN.matcher(summary).find()).as("Persian summary for " + key).isTrue();
      assertThat(seen.add(key.substring(0, key.indexOf('#')) + "|" + summary)).as("duplicate summary " + summary).isTrue();
      String description = ApiDocumentationCatalog.description(key, summary);
      assertThat(description).as("description of " + key).hasSizeGreaterThan(summary.length() + 20);
    }
  }

  @Test
  void everyRequestBodyHasAnExampleAndNoExampleIsStale() throws Exception {
    List<String> withoutExample = new ArrayList<>();
    for (Class<?> controller : controllers()) {
      for (Method method : controller.getDeclaredMethods()) {
        if (!AnnotatedElementUtils.hasAnnotation(method, RequestMapping.class)) continue;
        String key = controller.getSimpleName() + "#" + method.getName();
        for (var parameter : method.getParameters()) {
          if (!parameter.isAnnotationPresent(RequestBody.class)) continue;
          Object example = ApiDocumentationExamples.forOperation(key);
          if (example == null) example = ApiDocumentationExamples.forType(parameter.getType());
          if (example == null) withoutExample.add(key + " (" + parameter.getType().getSimpleName() + ")");
        }
      }
    }
    assertThat(withoutExample).as("request bodies without a documented example").isEmpty();
    // Operation-keyed examples must point at real operations.
    assertThat(ApiDocumentationExamples.operationKeys()).isSubsetOf(mappedOperations());
  }

  static Set<String> mappedOperations() throws Exception {
    Set<String> keys = new TreeSet<>();
    for (Class<?> controller : controllers()) {
      for (Method method : controller.getDeclaredMethods()) {
        if (!AnnotatedElementUtils.hasAnnotation(method, RequestMapping.class)
            || AnnotatedElementUtils.hasAnnotation(method, Hidden.class)) continue;
        keys.add(controller.getSimpleName() + "#" + method.getName());
      }
    }
    return keys;
  }

  static List<Class<?>> controllers() throws Exception {
    var scanner = new ClassPathScanningCandidateComponentProvider(false);
    scanner.addIncludeFilter(new AnnotationTypeFilter(RestController.class));
    List<Class<?>> result = new ArrayList<>();
    for (var candidate : scanner.findCandidateComponents("com.aurevia.authz.api")) {
      Class<?> controller = Class.forName(candidate.getBeanClassName());
      boolean hidden = AnnotatedElementUtils.hasAnnotation(controller, Hidden.class);
      assertThat(hidden && !INTENTIONALLY_HIDDEN.contains(controller.getSimpleName()))
          .as(controller.getSimpleName() + " is @Hidden without an entry in INTENTIONALLY_HIDDEN").isFalse();
      if (!hidden) result.add(controller);
    }
    return result;
  }
}

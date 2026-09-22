package com.aurevia.bff.docs;

import static org.assertj.core.api.Assertions.assertThat;

import io.swagger.v3.oas.annotations.Hidden;
import java.lang.reflect.Method;
import java.util.ArrayList;
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
 * Set-based synchronization between the browser-facing controllers and the Persian catalogue:
 * an endpoint without documentation fails, a catalogue entry without an endpoint fails.
 * The generated runtime document is verified on the running stack by tools/verify-swagger.mjs.
 */
class OpenApiDocumentationCoverageTest {
  private static final Pattern PERSIAN = Pattern.compile("[\\u0600-\\u06FF]");
  /** Hidden controllers need an entry here with a reason; hiding to pass coverage is not allowed. */
  static final Set<String> INTENTIONALLY_HIDDEN = Set.of(
      // Development-only Swagger facade (@Profile("!prod")): serves and proxies the documents themselves.
      "DeveloperDocumentationController");

  @Test void controllersAndCatalogueDescribeExactlyTheSameOperations() throws Exception {
    Set<String> mapped = mappedOperations();
    Set<String> documented = new TreeSet<>(BffOpenApiConfiguration.documentedOperations());
    assertThat(documented).as("stale catalogue entries").isSubsetOf(mapped);
    assertThat(mapped).as("undocumented endpoints").isSubsetOf(documented);
    assertThat(mapped).isNotEmpty();
  }

  @Test void everyVisibleControllerHasATagAndNoTagIsStale() throws Exception {
    Set<String> controllers = new TreeSet<>();
    for (Class<?> controller : controllers()) controllers.add(controller.getSimpleName());
    assertThat(BffOpenApiConfiguration.taggedControllers()).as("stale tags").isSubsetOf(controllers);
    assertThat(controllers).as("controllers without a tag").isSubsetOf(BffOpenApiConfiguration.taggedControllers());
  }

  @Test void summariesAndDescriptionsArePersianAndRequestBodiesHaveExamples() throws Exception {
    for (String key : BffOpenApiConfiguration.documentedOperations()) {
      String summary = BffOpenApiConfiguration.summary(key);
      assertThat(PERSIAN.matcher(summary).find()).as("Persian summary " + key).isTrue();
      assertThat(summary.length()).as("meaningful summary " + key).isGreaterThan(8);
    }
    List<String> missing = new ArrayList<>();
    for (Class<?> controller : controllers()) {
      for (Method method : controller.getDeclaredMethods()) {
        if (!AnnotatedElementUtils.hasAnnotation(method, RequestMapping.class)) continue;
        String key = controller.getSimpleName() + "#" + method.getName();
        for (var parameter : method.getParameters()) {
          if (parameter.isAnnotationPresent(RequestBody.class) && BffOpenApiConfiguration.requestExample(key) == null)
            missing.add(key);
        }
      }
    }
    assertThat(missing).as("request bodies without example").isEmpty();
  }

  @Test void examplesNeverContainSecretsOrDemoBusinessIdentifiers() {
    for (String key : BffOpenApiConfiguration.documentedOperations()) {
      Object example = BffOpenApiConfiguration.requestExample(key);
      if (example == null) continue;
      String text = String.valueOf(example).toLowerCase();
      assertThat(text).as(key).doesNotContain("eyj", "local-change-me", "finance.", "hr.employee", "payroll");
    }
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
    for (var candidate : scanner.findCandidateComponents("com.aurevia.bff.api")) {
      Class<?> controller = Class.forName(candidate.getBeanClassName());
      if (controller.getEnclosingClass() != null) continue; // test probes nested in test classes
      boolean hidden = AnnotatedElementUtils.hasAnnotation(controller, Hidden.class);
      assertThat(hidden && !INTENTIONALLY_HIDDEN.contains(controller.getSimpleName()))
          .as(controller.getSimpleName() + " is @Hidden without an INTENTIONALLY_HIDDEN entry").isFalse();
      if (!hidden) result.add(controller);
    }
    return result;
  }
}

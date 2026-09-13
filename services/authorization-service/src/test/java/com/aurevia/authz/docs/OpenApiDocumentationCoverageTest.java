package com.aurevia.authz.docs;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import io.swagger.v3.oas.annotations.Hidden;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.web.bind.annotation.RestController;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

class OpenApiDocumentationCoverageTest {
  @Test
  void everyMappedEndpointHasPersianSummaryAndEveryBodyHasExample() throws Exception {
    AtomicInteger endpoints = new AtomicInteger();
    var scanner = new ClassPathScanningCandidateComponentProvider(false);
    scanner.addIncludeFilter(new AnnotationTypeFilter(RestController.class));
    for (var candidate : scanner.findCandidateComponents("com.aurevia.authz.api")) {
      Class<?> controller = Class.forName(candidate.getBeanClassName());
      if (AnnotatedElementUtils.hasAnnotation(controller, Hidden.class)) continue;
      String simpleName = controller.getSimpleName();
      for (Method method : controller.getDeclaredMethods()) {
        if (!AnnotatedElementUtils.hasAnnotation(method, RequestMapping.class)
            || AnnotatedElementUtils.hasAnnotation(method, Hidden.class)) continue;
        endpoints.incrementAndGet();
        String key = simpleName + "#" + method.getName();
        assertDoesNotThrow(() -> ApiDocumentationCatalog.summary(key),
            () -> "OpenAPI summary is missing for " + key);
        for (var parameter : method.getParameters()) {
          if (!parameter.isAnnotationPresent(RequestBody.class)) continue;
          Object example = ApiDocumentationExamples.forOperation(key);
          if (example == null) example = ApiDocumentationExamples.forType(parameter.getType());
          assertNotNull(example, () -> "OpenAPI request example is missing for " + key);
        }
      }
    }
    assertTrue(endpoints.get() >= 75, "Controller inventory unexpectedly shrank: " + endpoints);
  }
}

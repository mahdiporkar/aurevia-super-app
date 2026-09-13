package com.aurevia.bff.docs;

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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestBody;

class OpenApiDocumentationCoverageTest {
  @Test
  void everyBrowserFacingEndpointHasPersianSummary() throws Exception {
    AtomicInteger endpoints = new AtomicInteger();
    var scanner = new ClassPathScanningCandidateComponentProvider(false);
    scanner.addIncludeFilter(new AnnotationTypeFilter(RestController.class));
    for (var candidate : scanner.findCandidateComponents("com.aurevia.bff.api")) {
      Class<?> controller = Class.forName(candidate.getBeanClassName());
      if (AnnotatedElementUtils.hasAnnotation(controller, Hidden.class)) continue;
      String simpleName = controller.getSimpleName();
      for (Method method : controller.getDeclaredMethods()) {
        if (!AnnotatedElementUtils.hasAnnotation(method, RequestMapping.class)
            || AnnotatedElementUtils.hasAnnotation(method, Hidden.class)) continue;
        endpoints.incrementAndGet();
        String key = simpleName + "#" + method.getName();
        assertNotNull(BffOpenApiConfiguration.summary(key),
            () -> "Persian OpenAPI summary is missing for " + key);
        for (var parameter : method.getParameters()) {
          if (!parameter.isAnnotationPresent(RequestBody.class)) continue;
          assertNotNull(BffOpenApiConfiguration.requestExample(key),
              () -> "OpenAPI request example is missing for " + key);
        }
      }
    }
    assertTrue(endpoints.get() >= 17, "Controller inventory unexpectedly shrank: " + endpoints);
  }
}

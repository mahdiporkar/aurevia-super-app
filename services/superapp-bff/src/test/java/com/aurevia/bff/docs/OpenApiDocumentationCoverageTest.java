package com.aurevia.bff.docs;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.web.bind.annotation.RequestMapping;

class OpenApiDocumentationCoverageTest {
  private static final List<String> CONTROLLERS = List.of(
      "AdminProxyController", "CsrfController", "MeController", "ReportsController",
      "OperationalProxyController", "OperationSupersetProxyController");

  @Test
  void everyBrowserFacingEndpointHasPersianSummary() throws Exception {
    AtomicInteger endpoints = new AtomicInteger();
    for (String simpleName : CONTROLLERS) {
      Class<?> controller = Class.forName("com.aurevia.bff.api." + simpleName);
      for (Method method : controller.getDeclaredMethods()) {
        if (!AnnotatedElementUtils.hasAnnotation(method, RequestMapping.class)) continue;
        endpoints.incrementAndGet();
        String key = simpleName + "#" + method.getName();
        assertNotNull(BffOpenApiConfiguration.summary(key),
            () -> "Persian OpenAPI summary is missing for " + key);
      }
    }
    assertTrue(endpoints.get() >= 13, "Controller inventory unexpectedly shrank: " + endpoints);
  }
}

package com.aurevia.authz.docs;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

class OpenApiDocumentationCoverageTest {
  private static final List<String> CONTROLLERS = List.of(
      "AccessAdminController", "AuthorizationController", "IdentityAdminController",
      "IdentitySyncController", "LogIngestionController", "LogQueryController",
      "OperationsController", "OuAccessAdminController", "OutboundAuthProfileController",
      "OutboundConnectionController", "ProxyRouteAdminController", "RegistryController",
      "ResourceManifestController", "RouteResolutionController", "SupersetAssetController",
      "SupersetInstanceController", "SupersetProxyResolutionController",
      "UiPluginRegistryController");

  @Test
  void everyMappedEndpointHasPersianSummaryAndEveryBodyHasExample() throws Exception {
    AtomicInteger endpoints = new AtomicInteger();
    for (String simpleName : CONTROLLERS) {
      Class<?> controller = Class.forName("com.aurevia.authz.api." + simpleName);
      for (Method method : controller.getDeclaredMethods()) {
        if (!AnnotatedElementUtils.hasAnnotation(method, RequestMapping.class)) continue;
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

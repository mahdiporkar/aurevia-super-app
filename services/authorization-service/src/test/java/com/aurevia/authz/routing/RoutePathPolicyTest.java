package com.aurevia.authz.routing;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class RoutePathPolicyTest {
  @Test void canonicalizesPrefixesAndMatchesSafeTemplates() {
    assertEquals("/hr-micro/",RoutePathPolicy.prefix("/hr-micro"));
    assertTrue(RoutePathPolicy.matches("/api/v1/employees/{id}","/api/v1/employees/42"));
    assertTrue(RoutePathPolicy.matches("/api/v1/**","/api/v1/employees/42"));
    assertFalse(RoutePathPolicy.matches("/api/v1/employees/{id}","/api/v1/employees/42/audit"));
  }

  @Test void rejectsTraversalEncodedSlashDuplicateSlashAndAdministratorRegex() {
    for(String attack:new String[]{"/hr/../secret","/hr/%2e%2e/secret","/hr//employees","/hr\\employees","/hr/%2fadmin"})
      assertThrows(IllegalArgumentException.class,()->RoutePathPolicy.path(attack));
    assertThrows(IllegalArgumentException.class,()->RoutePathPolicy.pattern("/api/(.*)"));
  }

  @Test void literalPatternsAreMoreSpecificThanVariablesAndWildcards() {
    assertTrue(RoutePathPolicy.specificity("/api/v1/employees/current")
        > RoutePathPolicy.specificity("/api/v1/employees/{id}"));
    assertTrue(RoutePathPolicy.specificity("/api/v1/employees/{id}")
        > RoutePathPolicy.specificity("/api/v1/**"));
  }
}

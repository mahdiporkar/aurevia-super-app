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

  @Test void administratorPatternLanguageIsExactlyLiteralsVariablesStarAndTerminalDoubleStar() {
    for(String valid:new String[]{"/","/employees","/employees/{id}","/employees/*","/files/**","/**",
        "/a.b_c~d-e/{orderId}/lines/*"})
      assertDoesNotThrow(()->RoutePathPolicy.pattern(valid),valid);
    for(String invalid:new String[]{"/files/**/more","/employees/{id","/employees/{1id}","/emp loyees",
        "/employees?x=1","/employees#frag","/employees/%20","/employees/..","/employees//x","/employees\\x",
        "/employees/{a-b}","/emp*loyees"})
      assertThrows(IllegalArgumentException.class,()->RoutePathPolicy.pattern(invalid),invalid);
  }

  @Test void runtimePathsKeepSafeEncodingsButRejectStructuralOrMalformedOnes() {
    assertEquals("/employees/John%20Doe",RoutePathPolicy.path("/employees/John%20Doe"));
    assertEquals("/x/%D8%B9%D9%84%DB%8C",RoutePathPolicy.path("/x/%D8%B9%D9%84%DB%8C"));
    for(String bad:new String[]{"/a/%2F","/a/%5c","/a/%2E%2E","/a/%3f","/a/%23","/a/%25","/a/%zz","/a/%2"})
      assertThrows(IllegalArgumentException.class,()->RoutePathPolicy.path(bad),bad);
    // Definitions never carry encodings at all.
    assertThrows(IllegalArgumentException.class,()->RoutePathPolicy.definition("/a/%20b"));
    assertThrows(IllegalArgumentException.class,()->RoutePathPolicy.prefix("/api/%20"));
  }

  @Test void rootPatternMatchesOnlyThePrefixItself() {
    assertTrue(RoutePathPolicy.matches("/","/"));
    assertFalse(RoutePathPolicy.matches("/","/anything"));
    assertTrue(RoutePathPolicy.matches("/employees/*","/employees/42"));
    assertFalse(RoutePathPolicy.matches("/employees/*","/employees/42/x"));
    assertFalse(RoutePathPolicy.matches("/employees/*","/employees"));
  }

  @Test void literalPatternsAreMoreSpecificThanVariablesAndWildcards() {
    assertTrue(RoutePathPolicy.specificity("/api/v1/employees/current")
        > RoutePathPolicy.specificity("/api/v1/employees/{id}"));
    assertTrue(RoutePathPolicy.specificity("/api/v1/employees/{id}")
        > RoutePathPolicy.specificity("/api/v1/**"));
  }
}

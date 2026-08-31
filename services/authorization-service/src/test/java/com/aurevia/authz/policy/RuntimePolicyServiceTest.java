package com.aurevia.authz.policy;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class RuntimePolicyServiceTest {
  @Test
  void restoresOnlyTheCanonicalTypeSeparator() {
    assertEquals("page:hr.departments",
        RuntimePolicyService.toRegistryKey("page/hr.departments"));
    assertEquals("business:hr/employee/salary",
        RuntimePolicyService.toRegistryKey("business/hr/employee/salary"));
  }

  @Test
  void leavesAlreadyRegistryShapedKeysUntouched() {
    assertEquals("application:aurevia/hr",
        RuntimePolicyService.toRegistryKey("application:aurevia/hr"));
  }
}

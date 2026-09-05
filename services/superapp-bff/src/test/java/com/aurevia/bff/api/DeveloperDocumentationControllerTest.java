package com.aurevia.bff.api;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

class DeveloperDocumentationControllerTest {
  private final WebClient authorization=WebClient.builder()
      .baseUrl("http://authorization.test").build();

  @Test
  void acceptsBoundedDocumentationBuffer() {
    assertDoesNotThrow(() -> new DeveloperDocumentationController(authorization,2_097_152));
  }

  @Test
  void rejectsUnsafeDocumentationBufferLimits() {
    assertThrows(IllegalArgumentException.class,
        () -> new DeveloperDocumentationController(authorization,262_143));
    assertThrows(IllegalArgumentException.class,
        () -> new DeveloperDocumentationController(authorization,8_388_609));
  }
}

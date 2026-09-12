package com.aurevia.testsso;
import java.time.Instant;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
class DiagnosticController {
  private static final Logger LOG = LoggerFactory.getLogger(DiagnosticController.class);
  @GetMapping("/health") Map<String,Object> health() { return Map.of("status", "UP"); }
  @GetMapping({"/api/test/whoami", "/api/test/data"})
  Map<String,Object> whoami(@AuthenticationPrincipal Jwt jwt,
      @RequestHeader(value="X-Correlation-ID", defaultValue="") String correlation) {
    String id = correlation.matches("[A-Za-z0-9_-]{1,100}") ? correlation : UUID.randomUUID().toString();
    LOG.info("DOWNSTREAM service=test-sso-service mode=SSO tokenType=user-oauth2 subject={} correlation={} status=200 time={}",
        jwt.getSubject(), id, Instant.now());
    return Map.of("service", "test-sso-service", "authenticated", true,
        "authMode", "SSO", "tokenType", "user-oauth2", "subject", jwt.getSubject(),
        "username", Optional.ofNullable(jwt.getClaimAsString("preferred_username")).orElse(jwt.getSubject()),
        "correlationId", id, "data", List.of("SSO test data"));
  }
}

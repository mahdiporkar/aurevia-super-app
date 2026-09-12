package com.aurevia.testlegacy;
import java.time.Instant;
import java.util.*;
import org.slf4j.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
class DiagnosticController {
  private static final Logger LOG=LoggerFactory.getLogger(DiagnosticController.class);
  private final LegacyCredentials credentials;
  DiagnosticController(LegacyCredentials credentials) { this.credentials=credentials; }
  @GetMapping("/health") Map<String,Object> health() { return Map.of("status","UP"); }
  // This is a downstream fixture for the existing FORM_URLENCODED adapter.
  // It is reachable by the BFF token client only; the gateway exposes no token route.
  @PostMapping(value="/auth/token",consumes=MediaType.APPLICATION_FORM_URLENCODED_VALUE)
  ResponseEntity<Map<String,Object>> token(@RequestParam String username,@RequestParam String password) {
    String token=credentials.acquire(username,password);
    if (token==null) return ResponseEntity.status(401).body(Map.of("error","invalid_credentials"));
    return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(
      Map.of("access_token",token,"expires_in",credentials.ttl(),"token_type","Bearer"));
  }
  @GetMapping({"/api/test/whoami","/api/test/data"})
  Map<String,Object> whoami(@RequestHeader(value="X-Correlation-ID",defaultValue="") String correlation) {
    String id=correlation.matches("[A-Za-z0-9_-]{1,100}")?correlation:UUID.randomUUID().toString();
    LOG.info("DOWNSTREAM service=test-legacy-service mode=LEGACY tokenType=service/legacy correlation={} status=200 time={}",id,Instant.now());
    return Map.of("service","test-legacy-service","authenticated",true,"authMode","LEGACY",
      "tokenType","service/legacy","correlationId",id,"data",List.of("Legacy test data"));
  }
}

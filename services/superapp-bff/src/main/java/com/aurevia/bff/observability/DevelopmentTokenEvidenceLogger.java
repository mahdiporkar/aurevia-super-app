package com.aurevia.bff.observability;

import com.aurevia.bff.outboundauth.OutboundCredential;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

/**
 * Development-only proof that token selection occurred. It logs irreversible,
 * truncated fingerprints; raw tokens, credentials and Authorization headers
 * are structurally not accepted by any logging call.
 */
@Component
public final class DevelopmentTokenEvidenceLogger {
  private static final Logger LOG=LoggerFactory.getLogger("aurevia.dev.token-evidence");
  private final boolean enabled;

  public DevelopmentTokenEvidenceLogger(Environment environment,
      @Value("${aurevia.development.token-evidence-logging.enabled:false}") boolean configured) {
    this.enabled=configured && !environment.acceptsProfiles(Profiles.of("prod"));
  }

  public void dispatch(String correlationId,String routeId,String operationId,String authMode,
      String subject,String publicAccessToken,OutboundCredential outbound) {
    if(!enabled)return;
    LOG.info("DEV_TOKEN_EVIDENCE event=dispatch correlation={} route={} operation={} mode={} "
        +"subjectFp={} publicIamFp={} outboundFp={} legacy={}",correlationId,routeId,operationId,
        authMode,fingerprint(subject),fingerprint(publicAccessToken),fingerprint(outbound.token()),
        outbound.legacy());
  }

  public void result(String correlationId,String routeId,int status) {
    if(enabled)LOG.info("DEV_TOKEN_EVIDENCE event=result correlation={} route={} status={}",
        correlationId,routeId,status);
  }

  public void legacyCache(String event,String profileId) {
    if(enabled)LOG.info("DEV_TOKEN_EVIDENCE event=legacy-cache cache={} profile={}",event,profileId);
  }

  boolean enabled(){return enabled;}

  private static String fingerprint(String value) {
    if(value==null||value.isBlank())return "missing";
    try {
      byte[] digest=MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(digest,0,8);
    } catch(Exception impossible) {
      throw new IllegalStateException("SHA-256 unavailable",impossible);
    }
  }
}

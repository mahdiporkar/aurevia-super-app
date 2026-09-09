package com.aurevia.authz.ui;

import static com.aurevia.artifacts.security.UiArtifactUriPolicy.ArtifactType.JSON_MANIFEST;
import static com.aurevia.artifacts.security.UiArtifactUriPolicy.ArtifactType.REMOTE_ENTRY;

import com.aurevia.artifacts.security.UiArtifactUriPolicy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Server-side trust policy for executable browser artifacts. */
@Component
public final class UiArtifactPolicy {
  private final UiArtifactUriPolicy locations;
  private final boolean requireIntegrity;

  public UiArtifactPolicy(
      @Value("${aurevia.ui-artifacts.network-policy:PRODUCTION_INTERNET}") String networkPolicy,
      @Value("${aurevia.ui-artifacts.allow-http:false}") boolean allowHttp,
      @Value("${aurevia.ui-artifacts.require-integrity:true}") boolean requireIntegrity,
      @Value("${aurevia.ui-artifacts.development-host:}") String developmentHost,
      @Value("${aurevia.ui-artifacts.allowed-private-cidrs:}") String allowedPrivateCidrs) {
    this.locations=new UiArtifactUriPolicy(networkPolicy,allowHttp,developmentHost,
        allowedPrivateCidrs);
    this.requireIntegrity=requireIntegrity;
  }

  public String validate(String url,String integrity) {
    var uri=locations.validateConfigured(url,REMOTE_ENTRY,"Remote Entry");
    if(requireIntegrity&&(integrity==null||integrity.isBlank())) {
      throw new IllegalArgumentException("SRI is required for UI artifacts");
    }
    if(integrity!=null&&!integrity.isBlank()
        && !integrity.matches("sha(256|384|512)-[A-Za-z0-9+/]+={0,2}")) {
      throw new IllegalArgumentException("Invalid UI artifact SRI");
    }
    return uri.toString();
  }

  /** Resource metadata follows the same URL and network policy as executable UI artifacts. */
  public String validateResourceManifestUrl(String url) {
    return validateJsonManifestUrl(url,"Resource manifest");
  }

  public String validateMicroFrontendManifestUrl(String url) {
    return validateJsonManifestUrl(url,"MF manifest");
  }

  /** Compatibility alias for callers compiled against the original resource-only API. */
  public String validateManifestUrl(String url) {
    return validateResourceManifestUrl(url);
  }

  private String validateJsonManifestUrl(String url,String label) {
    return locations.validateConfigured(url,JSON_MANIFEST,label).toString();
  }

  public boolean requireIntegrity() { return requireIntegrity; }

  /** Fetch-time validation includes DNS/network policy and development-only loopback rewriting. */
  public String resolveManifestFetchUrl(String url) {
    return locations.prepareForFetch(url,JSON_MANIFEST,"Manifest").toString();
  }
}

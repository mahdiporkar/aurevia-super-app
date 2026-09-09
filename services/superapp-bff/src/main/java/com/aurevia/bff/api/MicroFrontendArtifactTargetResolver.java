package com.aurevia.bff.api;

import com.aurevia.artifacts.security.UiArtifactUriPolicy;
import java.net.URI;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Applies the shared SSRF policy and an optional development-only loopback host bridge. */
@Component
final class MicroFrontendArtifactTargetResolver {
  private final UiArtifactUriPolicy policy;

  MicroFrontendArtifactTargetResolver(
      @Value("${aurevia.mfe-proxy.network-policy:PRODUCTION_INTERNET}") String networkPolicy,
      @Value("${aurevia.mfe-proxy.allow-http:false}") boolean allowHttp,
      @Value("${aurevia.mfe-proxy.development-host:}") String developmentHost,
      @Value("${aurevia.mfe-proxy.allowed-private-cidrs:}") String allowedPrivateCidrs) {
    policy=new UiArtifactUriPolicy(networkPolicy,allowHttp,developmentHost,allowedPrivateCidrs);
  }

  URI resolve(String registeredUrl) {
    return policy.prepareForFetch(registeredUrl,
        UiArtifactUriPolicy.ArtifactType.REMOTE_ENTRY,"Remote Entry");
  }

  URI resolveAsset(String registeredUrl,String assetPath) {
    return policy.prepareAssetForFetch(registeredUrl,assetPath);
  }
}

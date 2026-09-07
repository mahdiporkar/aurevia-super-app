package com.aurevia.bff.api;

import java.net.URI;
import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Maps browser loopback artifact URLs to explicit Docker-network targets in local deployments. */
@Component
final class MicroFrontendArtifactTargetResolver {
  private final Map<Integer,URI> loopbackTargets;

  MicroFrontendArtifactTargetResolver(
      @Value("${aurevia.mfe-proxy.loopback-targets:}") String configuredTargets) {
    loopbackTargets=parse(configuredTargets);
  }

  URI resolve(String registeredUrl) {
    URI source=URI.create(registeredUrl);
    if(!isLoopback(source.getHost()))return source;
    URI target=loopbackTargets.get(source.getPort());
    if(target==null)return source;
    return URI.create(target.toString().replaceFirst("/+$","")+source.getRawPath());
  }

  URI resolveAsset(String registeredUrl,String assetPath) {
    if(assetPath==null||assetPath.isBlank()||assetPath.contains("..")||assetPath.contains("\\"))
      throw new IllegalArgumentException("invalid MFE asset path");
    URI registered=URI.create(registeredUrl);
    URI asset=registered.resolve(assetPath.replaceFirst("^/+",""));
    return resolve(asset.toString());
  }

  private static boolean isLoopback(String host) {
    return "localhost".equalsIgnoreCase(host)||"127.0.0.1".equals(host)||"::1".equals(host);
  }

  private static Map<Integer,URI> parse(String value) {
    if(value==null||value.isBlank())return Map.of();
    return Arrays.stream(value.split(",")).map(String::trim).filter(entry->!entry.isBlank())
        .map(entry->entry.split("=",2)).collect(Collectors.toUnmodifiableMap(
            entry->Integer.parseInt(entry[0]),entry->{
              URI uri=URI.create(entry[1]);
              if(!"http".equals(uri.getScheme())&&!"https".equals(uri.getScheme()))
                throw new IllegalArgumentException("MFE proxy target must use HTTP(S)");
              return uri;
            }));
  }
}

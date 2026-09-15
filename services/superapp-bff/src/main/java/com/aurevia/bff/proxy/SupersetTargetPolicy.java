package com.aurevia.bff.proxy;

import static com.aurevia.artifacts.security.UiArtifactUriPolicy.ArtifactType.EXTERNAL_ORIGIN;

import com.aurevia.artifacts.security.UiArtifactUriPolicy;
import java.net.URI;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Validates registry-provided Superset origins before the trusted Gateway sees them. */
@Component
public final class SupersetTargetPolicy {
  private final UiArtifactUriPolicy locations;

  public SupersetTargetPolicy(
      @Value("${aurevia.superset.network-policy:DEVELOPMENT}") String networkPolicy,
      @Value("${aurevia.superset.allow-http:false}") boolean allowHttp,
      @Value("${aurevia.superset.development-host:}") String developmentHost,
      @Value("${aurevia.superset.allowed-private-cidrs:}") String allowedPrivateCidrs) {
    this.locations=new UiArtifactUriPolicy(networkPolicy,allowHttp,developmentHost,
        allowedPrivateCidrs);
  }

  public URI validate(String registeredOrigin, boolean tlsRequired) {
    URI uri=locations.prepareForFetch(registeredOrigin,EXTERNAL_ORIGIN,"Superset target");
    if(tlsRequired && !"https".equalsIgnoreCase(uri.getScheme())) {
      throw new IllegalArgumentException("Superset registry requires TLS for this target");
    }
    return uri;
  }

  public URI resolve(String registeredBaseUrl,boolean tlsRequired,String safePath,String rawQuery) {
    URI base=validate(registeredBaseUrl,tlsRequired);
    return resolve(base,safePath,rawQuery);
  }

  public URI resolve(URI base,String safePath,String rawQuery) {
    String basePath=base.getRawPath()==null?"":base.getRawPath();
    if(basePath.endsWith("/")) basePath=basePath.substring(0,basePath.length()-1);
    String path=safePath.startsWith("/")?safePath:"/"+safePath;
    return URI.create(base.getScheme()+"://"+base.getRawAuthority()+basePath+path
        +(rawQuery==null||rawQuery.isBlank()?"":"?"+rawQuery));
  }
}

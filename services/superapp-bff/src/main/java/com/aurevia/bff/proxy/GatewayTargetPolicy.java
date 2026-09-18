package com.aurevia.bff.proxy;

import java.net.URI;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Resolves only registry targets whose exact origin is approved by the BFF deployment. */
@Component
public final class GatewayTargetPolicy {
  private final Set<String> approvedOrigins;

  public GatewayTargetPolicy(@Value("${aurevia.gateway.base-url}") String defaultBaseUrl,
      @Value("${aurevia.gateway.approved-base-urls:}") String configured) {
    var values=new LinkedHashSet<String>();
    values.add(origin(defaultBaseUrl));
    Arrays.stream(configured.split(",")).map(String::trim).filter(value->!value.isEmpty())
        .map(GatewayTargetPolicy::origin).forEach(values::add);
    approvedOrigins=Set.copyOf(values);
  }

  public URI resolve(String registeredBaseUrl,String path,String rawQuery) {
    String targetOrigin=origin(registeredBaseUrl);
    if(!approvedOrigins.contains(targetOrigin)) {
      throw new IllegalArgumentException("Registered Gateway origin is not approved by this BFF");
    }
    String normalizedPath=RouteNormalizer.normalizePath(path);
    return URI.create(targetOrigin+normalizedPath+(rawQuery==null||rawQuery.isBlank()?"":"?"+rawQuery));
  }

  private static String origin(String value) {
    final URI uri;
    try { uri=URI.create(value.trim()); }
    catch(RuntimeException invalid) { throw new IllegalArgumentException("Invalid Gateway origin",invalid); }
    String scheme=uri.getScheme()==null?"":uri.getScheme().toLowerCase(Locale.ROOT);
    String host=uri.getHost()==null?"":uri.getHost().toLowerCase(Locale.ROOT);
    if(!Set.of("http","https").contains(scheme)||host.isBlank()||uri.getUserInfo()!=null
        ||uri.getQuery()!=null||uri.getFragment()!=null
        ||!(uri.getPath().isEmpty()||"/".equals(uri.getPath()))) {
      throw new IllegalArgumentException("Gateway target must be an HTTP(S) origin");
    }
    boolean defaultPort=uri.getPort()<0||("http".equals(scheme)&&uri.getPort()==80)
        ||("https".equals(scheme)&&uri.getPort()==443);
    return scheme+"://"+host+(defaultPort?"":":"+uri.getPort());
  }
}

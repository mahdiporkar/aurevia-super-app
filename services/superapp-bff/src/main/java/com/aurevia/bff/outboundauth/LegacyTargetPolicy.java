package com.aurevia.bff.outboundauth;

import java.net.URI;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Enforces a second egress boundary around endpoints supplied by the registry. */
@Component
final class LegacyTargetPolicy {
  private final Set<String> allowedHosts;
  private final Set<Integer> allowedPorts;
  private final boolean allowHttp;

  LegacyTargetPolicy(@Value("${aurevia.legacy.allowed-hosts}") String hosts,
      @Value("${aurevia.legacy.allowed-ports:443}") String ports,
      @Value("${aurevia.legacy.allow-insecure-local:false}") boolean allowHttp) {
    allowedHosts=Arrays.stream(hosts.split(",")).map(String::trim)
        .map(value->value.toLowerCase(Locale.ROOT)).filter(value->!value.isEmpty())
        .collect(Collectors.toUnmodifiableSet());
    allowedPorts=Arrays.stream(ports.split(",")).map(String::trim)
        .filter(value->!value.isEmpty()).map(Integer::parseInt)
        .collect(Collectors.toUnmodifiableSet());
    this.allowHttp=allowHttp;
    if(allowedHosts.isEmpty() || allowedPorts.isEmpty()) {
      throw new IllegalStateException("Legacy endpoint allowlists must not be empty");
    }
  }

  URI validate(OutboundConnection connection) {
    URI uri;
    try { uri=URI.create(connection.baseUrl()).normalize(); }
    catch(RuntimeException invalid) {
      throw new IllegalArgumentException("Invalid registered Legacy origin",invalid);
    }
    String scheme=uri.getScheme();
    String host=uri.getHost()==null?null:uri.getHost().toLowerCase(Locale.ROOT);
    int port=uri.getPort()<0?("https".equals(scheme)?443:80):uri.getPort();
    if(host==null || uri.getUserInfo()!=null || uri.getQuery()!=null || uri.getFragment()!=null
        || !(uri.getPath().isEmpty() || "/".equals(uri.getPath()))) {
      throw new IllegalArgumentException("Registered Legacy target must be an origin");
    }
    if(!"https".equals(scheme) && !(allowHttp && "http".equals(scheme))) {
      throw new IllegalArgumentException("Registered Legacy target must use HTTPS");
    }
    if(connection.tlsRequired() && !"https".equals(scheme)) {
      throw new IllegalArgumentException("Legacy registry requires TLS for this target");
    }
    if(!allowedHosts.contains(host) || !allowedPorts.contains(port)) {
      throw new IllegalArgumentException("Registered Legacy target is outside the egress allowlist");
    }
    return URI.create(scheme+"://"+host+(uri.getPort()<0?"":":"+port));
  }
}

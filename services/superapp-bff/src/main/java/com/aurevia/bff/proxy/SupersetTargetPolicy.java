package com.aurevia.bff.proxy;

import java.net.URI;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Validates registry-provided Superset origins before the trusted Gateway sees them. */
@Component
public final class SupersetTargetPolicy {
  private final Set<String> allowedHosts;
  private final Set<Integer> allowedPorts;
  private final boolean allowHttp;

  public SupersetTargetPolicy(
      @Value("${aurevia.superset.allowed-hosts}") String hosts,
      @Value("${aurevia.superset.allowed-ports:443}") String ports,
      @Value("${aurevia.superset.allow-http:false}") boolean allowHttp) {
    this.allowedHosts=split(hosts);
    this.allowedPorts=Arrays.stream(ports.split(",")).map(String::trim)
        .filter(value->!value.isEmpty()).map(Integer::parseInt).collect(Collectors.toUnmodifiableSet());
    this.allowHttp=allowHttp;
    if(allowedHosts.isEmpty() || allowedPorts.isEmpty()) {
      throw new IllegalArgumentException("Superset host and port allowlists must not be empty");
    }
  }

  public URI validate(String registeredOrigin, boolean tlsRequired) {
    URI uri;
    try { uri=URI.create(registeredOrigin).normalize(); }
    catch(RuntimeException invalid) { throw new IllegalArgumentException("Invalid registered Superset origin",invalid); }
    String scheme=uri.getScheme();
    String host=uri.getHost()==null?null:uri.getHost().toLowerCase(Locale.ROOT);
    int port=uri.getPort()<0?("https".equals(scheme)?443:80):uri.getPort();
    if(host==null || uri.getUserInfo()!=null || uri.getQuery()!=null || uri.getFragment()!=null
        || !(uri.getPath().isEmpty() || "/".equals(uri.getPath()))) {
      throw new IllegalArgumentException("Registered Superset target must be an origin");
    }
    if(!"https".equals(scheme) && !(allowHttp && "http".equals(scheme))) {
      throw new IllegalArgumentException("Registered Superset target must use HTTPS");
    }
    if(tlsRequired && !"https".equals(scheme)) {
      throw new IllegalArgumentException("Superset registry requires TLS for this target");
    }
    if(!allowedHosts.contains(host) || !allowedPorts.contains(port)) {
      throw new IllegalArgumentException("Registered Superset target is outside the egress allowlist");
    }
    return URI.create(scheme+"://"+host+(uri.getPort()<0?"":":"+port));
  }

  private static Set<String> split(String values) {
    return Arrays.stream(values.split(",")).map(String::trim)
        .map(value->value.toLowerCase(Locale.ROOT)).filter(value->!value.isEmpty())
        .collect(Collectors.toUnmodifiableSet());
  }
}

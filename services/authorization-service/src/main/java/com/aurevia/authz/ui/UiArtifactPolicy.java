package com.aurevia.authz.ui;

import java.net.URI;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Server-side trust policy for executable browser artifacts. */
@Component
public final class UiArtifactPolicy {
  private final Set<String> allowedOrigins;
  private final boolean allowHttp;
  private final boolean requireIntegrity;

  public UiArtifactPolicy(@Value("${aurevia.ui-artifacts.allowed-origins}") String origins,
      @Value("${aurevia.ui-artifacts.allow-http:false}") boolean allowHttp,
      @Value("${aurevia.ui-artifacts.require-integrity:true}") boolean requireIntegrity) {
    this.allowedOrigins=Arrays.stream(origins.split(",")).map(String::trim)
        .filter(value->!value.isEmpty()).map(UiArtifactPolicy::origin)
        .collect(Collectors.toUnmodifiableSet());
    this.allowHttp=allowHttp;this.requireIntegrity=requireIntegrity;
    if(allowedOrigins.isEmpty()) throw new IllegalStateException("UI artifact origin allowlist is empty");
  }

  public String validate(String url,String integrity) {
    URI uri;
    try { uri=URI.create(url).normalize(); }
    catch(RuntimeException invalid) { throw new IllegalArgumentException("Invalid Remote Entry URL",invalid); }
    if(uri.getHost()==null||uri.getUserInfo()!=null||uri.getQuery()!=null||uri.getFragment()!=null
        || !uri.getPath().endsWith(".js")) {
      throw new IllegalArgumentException("Remote Entry must be an absolute JavaScript URL");
    }
    if(!"https".equals(uri.getScheme()) && !(allowHttp&&"http".equals(uri.getScheme()))) {
      throw new IllegalArgumentException("Remote Entry must use HTTPS");
    }
    if(!allowedOrigins.contains(origin(uri.toString()))) {
      throw new IllegalArgumentException("Remote Entry origin is not approved");
    }
    if(requireIntegrity&&(integrity==null||integrity.isBlank())) {
      throw new IllegalArgumentException("SRI is required for UI artifacts");
    }
    if(integrity!=null&&!integrity.isBlank()
        && !integrity.matches("sha(256|384|512)-[A-Za-z0-9+/]+={0,2}")) {
      throw new IllegalArgumentException("Invalid UI artifact SRI");
    }
    return uri.toString();
  }

  public boolean requireIntegrity() { return requireIntegrity; }

  private static String origin(String value) {
    URI uri=URI.create(value.trim());
    String scheme=uri.getScheme()==null?"":uri.getScheme().toLowerCase(Locale.ROOT);
    String host=uri.getHost()==null?"":uri.getHost().toLowerCase(Locale.ROOT);
    int port=uri.getPort();
    if(host.isEmpty()||!Set.of("http","https").contains(scheme)) {
      throw new IllegalArgumentException("Invalid UI artifact origin allowlist");
    }
    return scheme+"://"+host+(port<0?"":":"+port);
  }
}

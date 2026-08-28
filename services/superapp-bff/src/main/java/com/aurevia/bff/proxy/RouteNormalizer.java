package com.aurevia.bff.proxy;

import java.net.URI;
import java.util.Locale;
import java.util.Set;

public final class RouteNormalizer {
  private static final Set<String> ALLOWED_SCHEMES = Set.of("https", "http");
  private RouteNormalizer() {}
  public static String normalizePath(String raw) {
    if (raw == null || !raw.startsWith("/") || raw.contains("\\") || raw.toLowerCase(Locale.ROOT).contains("%2f") || raw.toLowerCase(Locale.ROOT).contains("%5c")) throw new IllegalArgumentException("Ambiguous path");
    URI uri = URI.create(raw).normalize();
    if (uri.getRawPath().contains("..") || uri.getRawQuery() != null || uri.getFragment() != null) throw new IllegalArgumentException("Unsafe path");
    return uri.getRawPath();
  }
  public static URI allowlistedTarget(String configured) {
    URI uri = URI.create(configured);
    if (!uri.isAbsolute() || !ALLOWED_SCHEMES.contains(uri.getScheme()) || uri.getUserInfo() != null || uri.getFragment() != null) throw new IllegalArgumentException("Unsafe target");
    return uri;
  }
}

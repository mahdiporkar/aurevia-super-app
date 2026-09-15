package com.aurevia.bff.proxy;

import java.net.URI;
import java.util.Locale;
import java.util.Set;

public final class RouteNormalizer {
  private static final Set<String> ALLOWED_SCHEMES = Set.of("https", "http");
  private RouteNormalizer() {}
  public static String normalizePath(String raw) {
    String lower = raw == null ? "" : raw.toLowerCase(Locale.ROOT);
    if (raw == null || !raw.startsWith("/") || raw.contains("\\")
        || raw.chars().anyMatch(character -> character < 0x20 || character == 0x7f)
        || lower.contains("%2f") || lower.contains("%5c") || lower.contains("%2e")
        || lower.contains("%25") || lower.contains("%3f") || lower.contains("%23")) {
      throw new IllegalArgumentException("Ambiguous path");
    }
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

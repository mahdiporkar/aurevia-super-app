package com.aurevia.authz.routing;

/**
 * The single definition of how an incoming public path becomes the downstream path.
 *
 * <p>Used by the administrator preview and by runtime resolution, so what the Admin UI shows is
 * byte-for-byte what the BFF sends. Order of operations:</p>
 * <ol>
 *   <li>{@code stripPrefix}: drop that many leading segments of the incoming path
 *       (never more than the route prefix has).</li>
 *   <li>{@code rewritePattern}/{@code rewriteReplacement}: if configured, the remaining path must
 *       start with the literal pattern (written as {@code ^/...}); that literal is replaced and the
 *       result is the complete downstream path. {@code upstreamBasePath} is <b>not</b> applied.</li>
 *   <li>Otherwise {@code upstreamBasePath} of the service target is prepended.</li>
 * </ol>
 */
public final class UpstreamPathPolicy {
  private UpstreamPathPolicy() {}

  public record Transformation(int stripPrefix, String rewritePattern, String rewriteReplacement,
      String upstreamBasePath) {}

  /**
   * @throws IllegalArgumentException with code {@code REWRITE_PREFIX_NOT_FOUND_AFTER_STRIP} when a
   *     configured rewrite does not apply, or {@code INVALID_CANONICAL_PATH} when the result is
   *     not a canonical path
   */
  public static String upstreamPath(Transformation route, String canonicalPath) {
    String relative = strip(canonicalPath, route.stripPrefix());
    String pattern = blankToNull(route.rewritePattern());
    String replacement = blankToNull(route.rewriteReplacement());
    String upstream;
    if (pattern != null) {
      String literal = pattern.substring(1);
      if (!relative.startsWith(literal)) {
        throw new IllegalArgumentException("REWRITE_PREFIX_NOT_FOUND_AFTER_STRIP");
      }
      upstream = replacement + relative.substring(literal.length());
    } else {
      upstream = join(route.upstreamBasePath(), relative);
    }
    return RoutePathPolicy.path(upstream.isEmpty() ? "/" : upstream);
  }

  /** Number of segments a prefix contributes; {@code /} contributes none. */
  public static int segments(String normalizedPrefix) {
    String bare = normalizedPrefix.equals("/") ? "" : normalizedPrefix.replaceAll("/+$", "");
    return bare.isEmpty() ? 0 : bare.substring(1).split("/").length;
  }

  static String strip(String path, int count) {
    int index = 0;
    for (int i = 0; i < count; i++) {
      index = path.indexOf('/', index + 1);
      if (index < 0) return "/";
    }
    return count == 0 ? path : path.substring(index);
  }

  private static String join(String base, String path) {
    if (base == null || base.isBlank() || "/".equals(base)) return path;
    String trimmed = base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
    return "/".equals(path) ? trimmed : trimmed + path;
  }

  private static String blankToNull(String value) {
    return value == null || value.isBlank() ? null : value;
  }
}

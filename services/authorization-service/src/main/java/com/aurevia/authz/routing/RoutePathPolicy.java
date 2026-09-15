package com.aurevia.authz.routing;

import java.util.Locale;
import java.util.regex.Pattern;

/** Canonical, deliberately small route-pattern language. Administrator regex is never executed. */
public final class RoutePathPolicy {
  private static final Pattern SEGMENT = Pattern.compile("[A-Za-z0-9._~-]+|\\{[A-Za-z][A-Za-z0-9_]{0,63}}|\\*");
  private RoutePathPolicy() {}

  public static String path(String value) {
    if (value == null || !value.startsWith("/") || value.length() > 2000) fail();
    String lower=value.toLowerCase(Locale.ROOT);
    if (value.contains("\\") || value.contains("//") || value.indexOf('\0') >= 0
        || value.chars().anyMatch(c -> c < 0x20 || c == 0x7f)
        || lower.contains("%") || value.contains("?") || value.contains("#")) fail();
    for (String segment:value.split("/",-1)) if (segment.equals(".") || segment.equals("..")) fail();
    return value.length()>1 && value.endsWith("/") ? value.substring(0,value.length()-1) : value;
  }

  public static String prefix(String value) {
    String normalized=path(value);
    return normalized.equals("/") ? "/" : normalized+"/";
  }

  public static String pattern(String value) {
    String normalized=path(value);
    if (normalized.equals("/**")) return normalized;
    String[] parts=normalized.substring(1).split("/",-1);
    for(int i=0;i<parts.length;i++) {
      if (parts[i].equals("**") && i==parts.length-1) continue;
      if (!SEGMENT.matcher(parts[i]).matches()) throw new IllegalArgumentException("INVALID_PATH_PATTERN");
    }
    return normalized;
  }

  public static boolean matches(String pattern,String relative) {
    String[] expected=pattern.substring(1).split("/",-1);
    String[] actual=relative.substring(1).split("/",-1);
    int i=0;
    for(;i<expected.length;i++) {
      if (expected[i].equals("**")) return i==expected.length-1;
      if (i>=actual.length) return false;
      if (!(expected[i].equals("*") || expected[i].startsWith("{") || expected[i].equals(actual[i]))) return false;
    }
    return i==actual.length;
  }

  public static int specificity(String pattern) {
    int score=0;
    for(String segment:pattern.split("/")) score += segment.equals("**")?0:(segment.equals("*")||segment.startsWith("{"))?1:10;
    return score;
  }

  private static void fail(){throw new IllegalArgumentException("INVALID_CANONICAL_PATH");}
}

package com.aurevia.authz.directory;

import java.util.List;

public final class OuRuleEvaluator {
  private OuRuleEvaluator() {}
  public static boolean matches(String userDn,String ruleDn,String mode) {
    if(userDn==null)return false;
    return "EXACT".equals(mode)
      ? DirectoryDnParser.canonicalDn(userDn).equalsIgnoreCase(DirectoryDnParser.canonicalDn(ruleDn))
      : "SUBTREE".equals(mode) && DirectoryDnParser.isWithinSubtree(userDn,ruleDn);
  }
  public static boolean combine(List<Boolean> results,String combiner) {
    if(results.isEmpty())return false;
    return "ALL_OF".equals(combiner)?results.stream().allMatch(Boolean::booleanValue)
      : "ANY_OF".equals(combiner)&&results.stream().anyMatch(Boolean::booleanValue);
  }
}

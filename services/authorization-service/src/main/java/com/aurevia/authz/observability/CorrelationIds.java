package com.aurevia.authz.observability;

import java.util.UUID;
import java.util.regex.Pattern;

public final class CorrelationIds {
  public static final String HEADER="X-Correlation-ID";
  private static final Pattern VALID=Pattern.compile("[A-Za-z0-9._:-]{1,128}");
  private CorrelationIds(){}
  public static String normalize(String candidate){
    return candidate!=null&&VALID.matcher(candidate).matches()?candidate:UUID.randomUUID().toString();
  }
}

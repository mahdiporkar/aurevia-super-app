package com.aurevia.bff.observability;
import java.util.UUID;import java.util.regex.Pattern;
public final class CorrelationIds{public static final String HEADER="X-Correlation-ID";public static final String CONTEXT_KEY="aurevia.correlation-id";private static final Pattern VALID=Pattern.compile("[A-Za-z0-9._:-]{1,128}");private CorrelationIds(){}static String normalize(String value){return value!=null&&VALID.matcher(value).matches()?value:UUID.randomUUID().toString();}}

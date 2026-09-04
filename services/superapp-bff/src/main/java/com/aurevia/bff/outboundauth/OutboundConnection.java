package com.aurevia.bff.outboundauth;

import java.util.Map;

/** Non-secret, administrator-approved outbound endpoint metadata. */
record OutboundConnection(String reference,String baseUrl,boolean tlsRequired,long version) {
  static OutboundConnection from(Map<?,?> value) {
    return new OutboundConnection(required(value,"connection_ref"),required(value,"base_url"),
        Boolean.TRUE.equals(value.get("tls_required")),number(value,"version").longValue());
  }

  private static String required(Map<?,?> value,String key) {
    Object item=value.get(key);
    if(item==null || String.valueOf(item).isBlank()) {
      throw new IllegalStateException("Outbound connection response is incomplete");
    }
    return String.valueOf(item);
  }

  private static Number number(Map<?,?> value,String key) {
    Object item=value.get(key);
    if(!(item instanceof Number number)) {
      throw new IllegalStateException("Outbound connection response is incomplete");
    }
    return number;
  }
}

package com.aurevia.bff.outboundauth;
/** Secret-bearing value: deliberately redacted from string representations. */
public final class OutboundCredential {
  private final String scheme; private final String token; private final boolean legacy;
  public OutboundCredential(String scheme,String token,boolean legacy){this.scheme=scheme;this.token=token;this.legacy=legacy;}
  public String scheme(){return scheme;} public String token(){return token;} public boolean legacy(){return legacy;}
  @Override public String toString(){return "OutboundCredential[REDACTED]";}
}

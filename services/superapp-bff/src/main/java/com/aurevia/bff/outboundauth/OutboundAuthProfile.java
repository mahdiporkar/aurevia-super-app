package com.aurevia.bff.outboundauth;
import java.util.Map;
record OutboundAuthProfile(String id,OutboundAuthMode mode,String connectionRef,String endpointPath,String requestFormat,
 String credentialSecretRef,String scope,String audience,String tokenPointer,String expiresPointer,String refreshPointer,
 String typePointer,String scheme,String transport,int skewSeconds,int connectTimeoutMs,int responseTimeoutMs,long maxSize,long version){
 static OutboundAuthProfile from(Map p){return new OutboundAuthProfile(String.valueOf(p.get("id")),OutboundAuthMode.valueOf(String.valueOf(p.get("auth_mode"))),
  string(p,"token_connection_ref"),string(p,"token_endpoint_path"),string(p,"request_format"),string(p,"credential_secret_ref"),
  string(p,"scope"),string(p,"audience"),string(p,"token_response_pointer"),string(p,"expires_in_response_pointer"),
  string(p,"refresh_token_response_pointer"),string(p,"token_type_response_pointer"),string(p,"authorization_scheme"),
  string(p,"credential_transport"),number(p,"expiry_skew_seconds").intValue(),number(p,"connect_timeout_ms").intValue(),
  number(p,"response_timeout_ms").intValue(),number(p,"max_token_response_size").longValue(),number(p,"version").longValue());}
 private static String string(Map p,String k){Object v=p.get(k);return v==null?null:String.valueOf(v);} private static Number number(Map p,String k){return (Number)p.get(k);}
}

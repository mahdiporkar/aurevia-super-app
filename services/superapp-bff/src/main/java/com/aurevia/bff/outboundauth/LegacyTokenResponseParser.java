package com.aurevia.bff.outboundauth;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import org.springframework.stereotype.Component;
@Component class LegacyTokenResponseParser {
 private final ObjectMapper json; LegacyTokenResponseParser(ObjectMapper json){this.json=json;}
 Parsed parse(byte[] body,OutboundAuthProfile p){if(body.length==0||body.length>p.maxSize())throw new IllegalStateException("Invalid token response");try{JsonNode root=json.readTree(body);String access=text(root,p.tokenPointer());if(access==null||access.isBlank()||access.length()>16384)throw new IllegalStateException("Invalid token response");long expires=number(root,p.expiresPointer());if(expires<=0||expires>86400)throw new IllegalStateException("Invalid token lifetime");return new Parsed(access,value(text(root,p.typePointer()),p.scheme()),Instant.now().plusSeconds(expires));}catch(IllegalStateException e){throw e;}catch(Exception e){throw new IllegalStateException("Malformed token response");}}
 private static JsonNode node(JsonNode root,String pointer){return pointer==null||pointer.isBlank()?null:root.at(pointer);} private static String text(JsonNode r,String p){JsonNode n=node(r,p);return n==null||n.isMissingNode()||n.isNull()?null:n.asText();} private static long number(JsonNode r,String p){JsonNode n=node(r,p);return n!=null&&n.canConvertToLong()?n.longValue():-1;} private static String value(String a,String b){return a==null||a.isBlank()?b:a;}
 record Parsed(String accessToken,String tokenType,Instant expiresAt){@Override public String toString(){return "Parsed[REDACTED]";}}
}

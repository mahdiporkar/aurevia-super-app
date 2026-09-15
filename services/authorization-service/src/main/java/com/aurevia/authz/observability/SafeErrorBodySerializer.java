package com.aurevia.authz.observability;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class SafeErrorBodySerializer {
  private final ObjectMapper json;private final SensitiveDataRedactor redactor;private final int maxBytes;
  public SafeErrorBodySerializer(ObjectMapper json,SensitiveDataRedactor redactor,
      @Value("${logging.error-response.max-bytes:8192}") int maxBytes){
    if(maxBytes<256||maxBytes>65536)throw new IllegalArgumentException("Unsafe error response limit");
    this.json=json;this.redactor=redactor;this.maxBytes=maxBytes;
  }
  public SafeBody serialize(int status,String contentType,byte[] body){
    if(status<400||body==null||body.length==0||contentType==null)return SafeBody.empty();
    String normalized=contentType.toLowerCase(Locale.ROOT);
    if(!normalized.contains("application/json")&&!normalized.contains("+json"))return SafeBody.empty();
    try{
      var safe=redactor.redact(json.readTree(body));byte[] encoded=json.writeValueAsBytes(safe.value());
      boolean truncated=encoded.length>maxBytes;
      String value=new String(encoded,0,Math.min(encoded.length,maxBytes),StandardCharsets.UTF_8);
      return new SafeBody(value,safe.redacted(),truncated);
    }catch(Exception malformed){return SafeBody.empty();}
  }
  public record SafeBody(String body,boolean redacted,boolean truncated){
    static SafeBody empty(){return new SafeBody(null,false,false);}
  }
}

package com.aurevia.authz.observability;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.Iterator;
import java.util.Map;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class SensitiveDataRedactor {
  private static final Pattern SENSITIVE = Pattern.compile(
      "(?i)^(password|passwd|pass|token|access[_-]?token|refresh[_-]?token|id[_-]?token|"
      + "authorization|cookie|set[_-]?cookie|secret|client[_-]?secret|api[_-]?key|otp|pin|"
      + "cvv|card[_-]?number|private[_-]?key)$");

  public Redaction redact(JsonNode source) {
    JsonNode copy=source.deepCopy();boolean changed=walk(copy);return new Redaction(copy,changed);
  }

  private boolean walk(JsonNode node){
    boolean changed=false;
    if(node instanceof ObjectNode object){
      Iterator<Map.Entry<String,JsonNode>> fields=object.fields();
      while(fields.hasNext()){
        var field=fields.next();
        if(SENSITIVE.matcher(field.getKey()).matches()){
          object.put(field.getKey(),"[REDACTED]");changed=true;
        }else changed|=walk(field.getValue());
      }
    }else if(node instanceof ArrayNode array){for(JsonNode child:array)changed|=walk(child);}
    return changed;
  }

  public record Redaction(JsonNode value,boolean redacted){}
}

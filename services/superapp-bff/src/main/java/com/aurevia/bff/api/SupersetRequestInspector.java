package com.aurevia.bff.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Iterator;
import java.util.Set;
import org.springframework.stereotype.Component;

/** Extracts the asset identity required to authorize Superset chart-data POSTs. */
@Component
final class SupersetRequestInspector {
  private static final Set<String> DASHBOARD_KEYS=Set.of("dashboard_id","dashboardId");
  private static final Set<String> CHART_KEYS=Set.of("slice_id","sliceId","chart_id","chartId");
  private final ObjectMapper json;
  SupersetRequestInspector(ObjectMapper json) { this.json=json; }

  Hint inspect(byte[] body) {
    if(body.length==0) return Hint.NONE;
    try {
      JsonNode root=json.readTree(body);
      String dashboard=find(root,DASHBOARD_KEYS);
      if(valid(dashboard)) return new Hint("DASHBOARD",dashboard);
      String chart=find(root,CHART_KEYS);
      return valid(chart)?new Hint("CHART",chart):Hint.NONE;
    } catch(Exception ignored) {
      return Hint.NONE;
    }
  }

  private static String find(JsonNode node,Set<String> names) {
    if(node==null) return null;
    if(node.isObject()) {
      Iterator<String> fields=node.fieldNames();
      while(fields.hasNext()) {
        String name=fields.next();
        JsonNode value=node.get(name);
        if(names.contains(name) && value.isValueNode()) return value.asText();
        String nested=find(value,names);if(nested!=null) return nested;
      }
    } else if(node.isArray()) {
      for(JsonNode child:node) { String nested=find(child,names);if(nested!=null) return nested; }
    }
    return null;
  }
  private static boolean valid(String value) {
    return value!=null&&value.matches("[A-Za-z0-9._-]{1,255}");
  }
  record Hint(String type,String id) { private static final Hint NONE=new Hint("",""); }
}

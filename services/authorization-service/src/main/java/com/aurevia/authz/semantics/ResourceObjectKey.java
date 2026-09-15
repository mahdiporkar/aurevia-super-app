package com.aurevia.authz.semantics;

/** Single canonical conversion from registry resource keys to OpenFGA object keys. */
public final class ResourceObjectKey {
  private ResourceObjectKey() {}

  public static String from(String resourceType, String resourceKey) {
    if(resourceType==null || resourceType.isBlank() || resourceKey==null
        || resourceKey.isBlank()) {
      throw new IllegalArgumentException("Resource type and canonical key are required");
    }
    String type=resourceType.trim().toUpperCase(java.util.Locale.ROOT);
    String key=resourceKey.trim();
    if("APPLICATION".equals(type)) {
      return "application:"+strip(key,"application:").replace(':','/');
    }
    if("EXTERNAL_RESOURCE".equals(type)) {
      String value=key.startsWith("external:")
          ? strip(key,"external:"):strip(key,"external_resource:");
      return "external_resource:"+value.replace(':','/');
    }
    // Early catalog migrations used stable dotted keys (for example hr.employee)
    // before typed prefixes were introduced. Both forms represent the same
    // OpenFGA resource namespace and must remain readable during upgrades.
    return "resource:"+key.replace(':','/');
  }

  private static String strip(String value,String prefix) {
    return value.startsWith(prefix)?value.substring(prefix.length()):value;
  }
}

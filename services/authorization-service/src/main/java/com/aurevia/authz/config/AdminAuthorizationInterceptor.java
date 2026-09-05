package com.aurevia.authz.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import com.aurevia.authz.openfga.RelationshipAuthorizationPort;
import com.aurevia.authz.identity.SubjectKey;

@Component
class AdminAuthorizationInterceptor implements HandlerInterceptor {
  private final RelationshipAuthorizationPort relationships;

  AdminAuthorizationInterceptor(RelationshipAuthorizationPort relationships) {
    this.relationships = relationships;
  }

  @Override
  public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
      throws Exception {
    String issuer = request.getHeader("X-Actor-Issuer");
    String subject = request.getHeader("X-Actor-Subject");
    String object=resourceFor(request.getRequestURI());
    String permission=permissionFor(request);
    boolean allowed = issuer != null && subject != null && relationships.check(
        new SubjectKey(issuer, subject).openFgaUser(), permission, object);
    if (!allowed) {
      response.sendError(HttpStatus.FORBIDDEN.value(),
          "Administrative permission required: " + permission);
    }
    return allowed;
  }

  private static String permissionFor(HttpServletRequest request) {
    String method=request.getMethod();
    String uri=request.getRequestURI();
    if (("GET".equals(method) || "HEAD".equals(method)) && uri.contains("/logs/audit")) {
      return "can_manage";
    }
    if ("GET".equals(method) || "HEAD".equals(method)) return "can_view";
    if ("DELETE".equals(method)) return "can_delete";
    if ("PUT".equals(method) || "PATCH".equals(method)) return "can_edit";
    if ("POST".equals(method) && isPrivilegedOperation(uri)) return "can_manage";
    if ("POST".equals(method)) return "can_create";
    return "can_manage";
  }

  private static boolean isPrivilegedOperation(String uri) {
    return uri.endsWith("/token-test") || uri.endsWith("/connection-test")
        || uri.endsWith("/invalidate-token") || uri.endsWith("/health")
        || uri.endsWith("/activate") || uri.endsWith("/deactivate")
        || uri.endsWith("/validate") || uri.endsWith("/preview")
        || uri.contains("/assignments") || uri.contains("/grants");
  }

  private static String resourceFor(String uri) {
    if(uri.contains("/superset-assets")) return "resource:module/admin.superset-catalog";
    if(uri.contains("/outbound-auth-profiles")||uri.contains("/outbound-connections")) {
      return "resource:integration.auth-profile";
    }
    if(uri.contains("/logs")) return "resource:business_resource/public-zone-logs";
    if(uri.contains("/service-targets")) return "resource:proxy.target";
    if(uri.contains("/proxy-routes/") && uri.contains("/operations")) return "resource:proxy.operation";
    if(uri.contains("/proxy-routes")) return "resource:proxy.route";
    return "application:aurevia";
  }
}

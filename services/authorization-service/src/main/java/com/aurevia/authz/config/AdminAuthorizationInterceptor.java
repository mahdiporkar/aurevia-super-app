package com.aurevia.authz.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import com.aurevia.authz.openfga.RelationshipAuthorizationPort;

@Component
class AdminAuthorizationInterceptor implements HandlerInterceptor {
  private final RelationshipAuthorizationPort relationships;

  AdminAuthorizationInterceptor(RelationshipAuthorizationPort relationships) {
    this.relationships = relationships;
  }

  @Override
  public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
      throws Exception {
    String actor = request.getHeader("X-Actor");
    String object=resourceFor(request.getRequestURI());
    boolean allowed = actor != null && relationships.check(
        "user:" + actor, "can_manage", object);
    if (!allowed) {
      response.sendError(HttpStatus.FORBIDDEN.value(), "Administrative permission required");
    }
    return allowed;
  }

  private static String resourceFor(String uri) {
    if(uri.contains("/outbound-auth-profiles")) return "resource:integration.auth-profile";
    if(uri.contains("/service-targets")) return "resource:proxy.target";
    if(uri.contains("/proxy-routes/") && uri.contains("/operations")) return "resource:proxy.operation";
    if(uri.contains("/proxy-routes")) return "resource:proxy.route";
    return "application:aurevia";
  }
}

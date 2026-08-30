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
    boolean allowed = actor != null && relationships.check(
        "user:" + actor, "can_manage", "application:aurevia");
    if (!allowed) {
      response.sendError(HttpStatus.FORBIDDEN.value(), "Administrative permission required");
    }
    return allowed;
  }
}

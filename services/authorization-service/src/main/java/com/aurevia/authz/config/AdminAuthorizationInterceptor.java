package com.aurevia.authz.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
class AdminAuthorizationInterceptor implements HandlerInterceptor {
  private final JdbcClient database;

  AdminAuthorizationInterceptor(JdbcClient database) {
    this.database = database;
  }

  @Override
  public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
      throws Exception {
    String actor = request.getHeader("X-Actor");
    boolean allowed = actor != null && database.sql("""
        select exists(
          select 1 from app_user u
          join authorization_grant g on g.subject_type='USER' and g.subject_id=u.id
          join resource r on r.id=g.resource_id and r.resource_key='application:aurevia'
          join action a on a.id=g.action_id and a.action_key='admin'
          where (u.external_id=:actor or u.username=:actor)
            and u.status='ACTIVE' and g.status='ACTIVE'
            and (g.expires_at is null or g.expires_at>now())
        )
        """).param("actor", actor).query(Boolean.class).single();
    if (!allowed) {
      response.sendError(HttpStatus.FORBIDDEN.value(), "Administrative permission required");
    }
    return allowed;
  }
}

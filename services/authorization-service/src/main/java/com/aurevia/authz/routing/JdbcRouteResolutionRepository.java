package com.aurevia.authz.routing;

import java.util.List;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcRouteResolutionRepository implements RouteResolutionRepository {
  private final JdbcClient database;

  JdbcRouteResolutionRepository(JdbcClient database) { this.database=database; }

  @Override
  public List<Candidate> activeCandidates(String httpMethod) {
    return database.sql("""
        select pr.id as "routeId",pr.code as "routeKey",pr.path_prefix as "pathPrefix",
          pr.normalized_path_prefix as "normalizedPrefix",pr.strip_prefix as "stripPrefix",
          pr.rewrite_pattern as "rewritePattern",pr.rewrite_replacement as "rewriteReplacement",
          pr.priority,array_to_string(pr.allowed_methods,',') as "allowedMethods",
          pr.retry_enabled as "retryEnabled",pr.max_retries as "maxRetries",
          p.id as "panelId",p.slug as "panelSlug",st.id as "targetId",
          st.code as "targetKey",st.tls_profile_ref as "tlsProfileRef",
          ro.id as "operationId",ro.normalized_path_pattern as "pathPattern",
          ro.resource_id as "resourceId",ro.resource_key as "resourceKey",
          ro.action_key as "actionKey",ro.authorization_required as "authorizationRequired",
          ro.data_policy_key as "dataPolicyKey",ro.max_body_bytes as "maxBodyBytes",
          st.connect_timeout_ms as "connectTimeoutMs",
          st.response_timeout_ms as "responseTimeoutMs",
          st.max_response_size as "maxResponseBytes",ap.id as "authProfileId",
          ap.auth_mode as "authMode",ap.version as "authProfileVersion",
          ap.credential_transport as "credentialTransport"
        from proxy_route pr join panel p on p.id=pr.panel_id
        join service_target st on st.id=pr.service_target_id
        join outbound_auth_profile ap on ap.id=st.outbound_auth_profile_id
        join route_operation ro on ro.proxy_route_id=pr.id
        where p.active and pr.active and st.active and ap.active and ro.active
          and ro.http_method=:method
        """).param("method",httpMethod).query(Candidate.class).list();
  }
}

package com.aurevia.authz.config;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aurevia.authz.openfga.RelationshipAuthorizationPort;
import com.aurevia.authz.identity.CanonicalIdentityResolver;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class AdminAuthorizationInterceptorTest {
  private final RelationshipAuthorizationPort relationships=org.mockito.Mockito.mock(RelationshipAuthorizationPort.class);
  private final CanonicalIdentityResolver identities=org.mockito.Mockito.mock(CanonicalIdentityResolver.class);
  private final AdminAuthorizationInterceptor interceptor=new AdminAuthorizationInterceptor(relationships,identities);

  @Test void mapsReadAndMutationToLeastPrivilegePermissions() throws Exception {
    assertAllowed("GET","/internal/v1/registry/proxy-routes","can_view","resource:proxy.route");
    assertAllowed("POST","/internal/v1/registry/proxy-routes","can_create","resource:proxy.route");
    assertAllowed("PATCH","/internal/v1/registry/proxy-routes/1","can_edit","resource:proxy.route");
    assertAllowed("DELETE","/internal/v1/registry/proxy-routes/1","can_delete","resource:proxy.route");
    assertAllowed("POST","/internal/v1/registry/outbound-auth-profiles/1/token-test","can_manage","resource:integration.auth-profile");
    assertAllowed("GET","/internal/v1/registry/outbound-connections","can_view","resource:integration.auth-profile");
    assertAllowed("GET","/internal/v1/registry/logs/api","can_view","resource:business_resource/public-zone-logs");
    assertAllowed("GET","/internal/v1/registry/logs/audit","can_manage","resource:business_resource/public-zone-logs");
  }

  private void assertAllowed(String method,String uri,String permission,String object) throws Exception {
    var request=new MockHttpServletRequest(method,uri);
    request.addHeader("X-Actor","alice");
    request.addHeader("X-Actor-Issuer","https://issuer.example");
    request.addHeader("X-Actor-Subject","subject-1");
    String canonical="user:usr_canonical";
    when(identities.openFgaUser("https://issuer.example","subject-1")).thenReturn(canonical);
    when(relationships.check(canonical,permission,object)).thenReturn(true);
    assertTrue(interceptor.preHandle(request,new MockHttpServletResponse(),new Object()));
    verify(relationships).check(canonical,permission,object);
  }
}

package com.aurevia.authz.config;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aurevia.authz.openfga.RelationshipAuthorizationPort;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class AdminAuthorizationInterceptorTest {
  private final RelationshipAuthorizationPort relationships=org.mockito.Mockito.mock(RelationshipAuthorizationPort.class);
  private final AdminAuthorizationInterceptor interceptor=new AdminAuthorizationInterceptor(relationships);

  @Test void mapsReadAndMutationToLeastPrivilegePermissions() throws Exception {
    assertAllowed("GET","/internal/v1/registry/proxy-routes","can_view","resource:proxy.route");
    assertAllowed("POST","/internal/v1/registry/proxy-routes","can_create","resource:proxy.route");
    assertAllowed("PATCH","/internal/v1/registry/proxy-routes/1","can_edit","resource:proxy.route");
    assertAllowed("DELETE","/internal/v1/registry/proxy-routes/1","can_delete","resource:proxy.route");
    assertAllowed("POST","/internal/v1/registry/outbound-auth-profiles/1/token-test","can_manage","resource:integration.auth-profile");
  }

  private void assertAllowed(String method,String uri,String permission,String object) throws Exception {
    var request=new MockHttpServletRequest(method,uri);request.addHeader("X-Actor","alice");
    when(relationships.check("user:alice",permission,object)).thenReturn(true);
    assertTrue(interceptor.preHandle(request,new MockHttpServletResponse(),new Object()));
    verify(relationships).check("user:alice",permission,object);
  }
}

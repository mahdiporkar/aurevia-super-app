package com.aurevia.authz.superset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.aurevia.authz.api.dto.SupersetInstanceDtos.InstanceRequest;
import com.aurevia.authz.observability.AuditTrail;
import com.aurevia.authz.identity.CanonicalIdentityResolver;
import com.aurevia.authz.openfga.RelationshipAuthorizationPort;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class SupersetInstanceServiceTest {
  @Test void registersAnExternalHttpsBasePathWithoutEnvironmentChanges() {
    SupersetInstanceRepository repository=mock(SupersetInstanceRepository.class);
    var service=new SupersetInstanceService(repository,mock(SupersetAssetRepository.class),
        mock(RelationshipAuthorizationPort.class),mock(AuditTrail.class),new ObjectMapper(),
        mock(CanonicalIdentityResolver.class),
        "PRODUCTION_INTERNET",false,"","");
    var request=new InstanceRequest("superset-public","Public BI","PUBLIC",
        "https://example.com/superset",null,"OIDC",true,true,true,
        Map.of("owner","BI"),0);

    service.create(request,"administrator");

    var value=ArgumentCaptor.forClass(SupersetInstanceRepository.InstanceValue.class);
    verify(repository).insert(value.capture(),eq("administrator"));
    verify(repository).ensureApplicationResource(any());
    assertThat(value.getValue().baseUrl()).isEqualTo("https://example.com/superset");
    assertThat(value.getValue().connectionRef())
        .isEqualTo("connection://superset/superset-public");
    assertThat(value.getValue().proxyMode()).isTrue();
  }

  @Test void rejectsLoopbackAndCloudMetadataInProduction() {
    var service=new SupersetInstanceService(mock(SupersetInstanceRepository.class),
        mock(SupersetAssetRepository.class),mock(RelationshipAuthorizationPort.class),
        mock(AuditTrail.class),new ObjectMapper(),mock(CanonicalIdentityResolver.class),
        "PRODUCTION_INTERNET",false,"","");
    for(String url:new String[]{"https://127.0.0.1","https://169.254.169.254/latest"}) {
      var request=new InstanceRequest("superset-public","Public BI","PUBLIC",url,null,
          "OIDC",true,true,true,Map.of(),0);
      assertThatThrownBy(()->service.create(request,"administrator"))
          .as(url).isInstanceOf(IllegalArgumentException.class);
    }
  }
}

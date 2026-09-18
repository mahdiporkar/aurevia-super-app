package com.aurevia.authz.routing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aurevia.authz.api.dto.ProxyRouteDtos.RouteRequest;
import com.aurevia.authz.observability.AuditTrail;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ProxyRouteAdministrationServiceTest {
  @Test void allowsAnySafePrefixAndMultipleAuthModesForTheSameMicrofrontendAndTarget() {
    var repository=mock(ProxyRouteRepository.class);
    UUID panel=UUID.randomUUID();
    UUID target=UUID.randomUUID();
    UUID forward=UUID.randomUUID();
    UUID legacy=UUID.randomUUID();
    when(repository.panelSlug(panel)).thenReturn(Optional.of("mixed-micro"));
    when(repository.targetExists(target)).thenReturn(true);
    when(repository.authProfileExists(forward)).thenReturn(true);
    when(repository.authProfileExists(legacy)).thenReturn(true);
    when(repository.route(any())).thenAnswer(invocation -> Optional.of(
        Map.<String,Object>of("id",invocation.getArgument(0,UUID.class),"active",true)));
    var service=new ProxyRouteAdministrationService(repository,mock(AuditTrail.class),
        mock(RouteResolutionService.class),"operation-gateway");

    service.createRoute(route("mixed-forward",panel,target,forward),"operator");
    service.createRoute(route("mixed-legacy",panel,target,legacy),"operator");

    var values=ArgumentCaptor.forClass(ProxyRouteRepository.RouteValue.class);
    verify(repository,times(2)).insertRoute(values.capture());
    assertThat(values.getAllValues()).extracting(ProxyRouteRepository.RouteValue::outboundAuthProfileId)
        .containsExactly(forward,legacy);
    assertThat(values.getAllValues()).extracting(ProxyRouteRepository.RouteValue::pathPrefix)
        .containsOnly("/custom/mixed-api");
  }

  private static RouteRequest route(String code,UUID panel,UUID target,UUID profile) {
    return new RouteRequest(code,panel,target,profile,"mixed-service","/custom/mixed-api",
        2,null,null,100,List.of("GET"),false,false,0,true);
  }
}

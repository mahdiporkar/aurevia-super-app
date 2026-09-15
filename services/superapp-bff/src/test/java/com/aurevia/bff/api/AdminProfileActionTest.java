package com.aurevia.bff.api;
import com.aurevia.bff.outboundauth.*;
import com.aurevia.bff.security.*;
import java.util.Map;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.assertThat;

class AdminProfileActionTest {
  @ParameterizedTest @ValueSource(strings={"token-test","connection-test","invalidate-token"})
  void usesActionAttachedToRealAuthProfileResource(String operation) {
    var client=mock(WebClient.class);
    var post=mock(WebClient.RequestBodyUriSpec.class);
    var request=mock(WebClient.RequestBodySpec.class);
    var headers=mock(WebClient.RequestHeadersSpec.class);
    var response=mock(WebClient.ResponseSpec.class);
    when(client.post()).thenReturn(post);
    when(post.uri("/internal/v1/authorize/check")).thenReturn(request);
    when(request.contentType(MediaType.APPLICATION_JSON)).thenReturn(request);
    when(request.bodyValue(any())).thenReturn(headers);
    when(headers.retrieve()).thenReturn(response);
    when(response.bodyToMono(Map.class)).thenReturn(Mono.just(Map.of("result","ALLOW")));
    var manager=mock(LegacyTokenManager.class);
    when(manager.test("profile")).thenReturn(Mono.just(new OutboundCredential("Bearer","opaque-test",true)));
    when(manager.validateConnection("profile")).thenReturn(Mono.empty());
    when(manager.invalidate("profile")).thenReturn(Mono.empty());
    var controller=new AdminProxyController(client,WebClient.create(),manager,mock(DynamicClientRegistrationRepository.class));
    var user=new SessionIdentity("https://issuer.example","admin-subject","admin");
    var result=switch(operation){
      case "token-test"->controller.tokenTest("profile",user);
      case "connection-test"->controller.connectionTest("profile",user);
      default->controller.invalidate("profile",user);
    };
    assertThat(result.block().getStatusCode().value()).isEqualTo(200);
    verify(request).bodyValue(argThat(body->{
      Map<?,?> check=(Map<?,?>)body;
      return "resource:integration.auth-profile".equals(check.get("resource"))
        && (operation.equals("invalidate-token")?"invalidate-token":"test").equals(check.get("action"));
    }));
  }
}

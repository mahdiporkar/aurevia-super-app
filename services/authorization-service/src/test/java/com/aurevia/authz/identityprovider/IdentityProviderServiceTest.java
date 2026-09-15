package com.aurevia.authz.identityprovider;

import static com.aurevia.authz.identityprovider.IdentityProviderModels.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import com.aurevia.authz.observability.AuditTrail;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class IdentityProviderServiceTest {
  @Test void routesTwoProvidersByTenantAndDomain(){
    IdentityProviderRepository repository=mock(IdentityProviderRepository.class);
    var first=provider("bank-a","tenant-a","a.example");
    var second=provider("bank-b","tenant-b","b.example");
    when(repository.findEnabled()).thenReturn(List.of(first,second));
    var service=service(repository,new IdentityProviderUriPolicy(false,true,false,""));
    assertThat(service.route(null,"tenant-b",null).code()).isEqualTo("bank-b");
    assertThat(service.route(null,null,"user@a.example").code()).isEqualTo("bank-a");
    assertThatThrownBy(()->service.route(null,null,null)).isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("Identity provider selection required");
  }

  @Test void productionPolicyRejectsHttpAndNonAllowlistedIssuer(){
    IdentityProviderUriPolicy policy=new IdentityProviderUriPolicy(false,false,true,"sso.example.com");
    assertThatThrownBy(()->policy.validate("http://sso.example.com/realms/main","issuer_url"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(()->policy.validate("https://attacker.example/realms/main","issuer_url"))
        .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("allowlisted");
  }

  private static IdentityProviderService service(IdentityProviderRepository repository,
      IdentityProviderUriPolicy policy){return new IdentityProviderService(repository,policy,
      mock(IdentityProviderHealthProbe.class),mock(AuditTrail.class));}
  private static ProviderView provider(String code,String tenant,String domain){return new ProviderView(
      UUID.randomUUID(),code,code,"KEYCLOAK","https://"+domain+"/realms/main",
      "https://"+domain+"/auth","https://"+domain+"/token","https://"+domain+"/jwks",null,
      "aurevia","secret://identity/"+code,true,tenant,List.of(domain),
      List.of("openid"),List.of(),"sub","preferred_username","groups","ACTIVE",null,null,0);}
}

package com.aurevia.authz.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class CanonicalIdentityResolverTest {
  @Test void changingExternalProviderDoesNotChangeOpenFgaSubject(){
    CanonicalIdentityRepository repository=mock(CanonicalIdentityRepository.class);
    when(repository.canonicalUserId("https://keycloak.example/realms/main","old-sub"))
        .thenReturn(Optional.of("usr_789"));
    when(repository.canonicalUserId("https://login.microsoftonline.com/tenant/v2.0","new-sub"))
        .thenReturn(Optional.of("usr_789"));
    var resolver=new CanonicalIdentityResolver(repository);
    assertThat(resolver.openFgaUser("https://keycloak.example/realms/main","old-sub"))
        .isEqualTo("user:usr_789")
        .isEqualTo(resolver.openFgaUser("https://login.microsoftonline.com/tenant/v2.0","new-sub"));
  }
}

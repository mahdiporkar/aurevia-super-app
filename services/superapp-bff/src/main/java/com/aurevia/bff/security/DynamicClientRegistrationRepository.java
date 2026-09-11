package com.aurevia.bff.security;

import com.aurevia.bff.api.AuthorizationServiceClient;
import com.aurevia.bff.api.AuthorizationServiceClient.IdentityProviderRuntime;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistration.ClientSettings;
import org.springframework.security.oauth2.client.registration.ReactiveClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/** Lazy registry adapter: Aurevia can start when no IdP is configured or reachable. */
@Component
public class DynamicClientRegistrationRepository implements ReactiveClientRegistrationRepository {
  static final String SUBJECT_CLAIM="aurevia_subject_claim";
  static final String USERNAME_CLAIM="aurevia_username_claim";
  static final String GROUPS_CLAIM="aurevia_groups_claim";
  static final String AUDIENCES="aurevia_audiences";
  private final AuthorizationServiceClient authorization;
  private final IdentityProviderSecretResolver secrets;
  private final Duration ttl;
  private final Duration timeout;
  private final Map<String,CachedRegistration> cache=new ConcurrentHashMap<>();
  public DynamicClientRegistrationRepository(AuthorizationServiceClient authorization,
      IdentityProviderSecretResolver secrets,
      @Value("${aurevia.identity-providers.cache-ttl:30s}") Duration ttl,
      @Value("${aurevia.identity-providers.request-timeout:10s}") Duration timeout){
    this.authorization=authorization;this.secrets=secrets;this.ttl=ttl;this.timeout=timeout;}

  @Override public Mono<ClientRegistration> findByRegistrationId(String registrationId){
    if(registrationId==null||!registrationId.matches("[a-z][a-z0-9-]{2,79}"))return Mono.empty();
    CachedRegistration cached=cache.get(registrationId);
    if(cached!=null&&cached.expiresAt().isAfter(Instant.now()))return Mono.just(cached.registration());
    return authorization.identityProvider(registrationId).timeout(timeout)
        .flatMap(provider->secrets.resolve(provider.clientSecretReference()).timeout(timeout)
            .map(secret->registration(provider,secret)))
        .doOnNext(value->cache.put(registrationId,new CachedRegistration(value,Instant.now().plus(ttl))));
  }
  public void invalidateAll(){cache.clear();}
  private static ClientRegistration registration(IdentityProviderRuntime provider,String secret){
    Map<String,Object> metadata=new LinkedHashMap<>();
    metadata.put(SUBJECT_CLAIM,provider.subjectClaim());metadata.put(USERNAME_CLAIM,provider.usernameClaim());
    metadata.put(GROUPS_CLAIM,provider.groupsClaim());metadata.put(AUDIENCES,provider.audiences());
    var builder=ClientRegistration.withRegistrationId(provider.code()).clientName(provider.name())
        .clientId(provider.clientId()).clientSecret(secret)
        .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
        .clientSettings(ClientSettings.builder().requireProofKey(true).build())
        .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
        .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
        .scope(provider.scopes()).authorizationUri(provider.authorizationEndpoint())
        .tokenUri(provider.tokenEndpoint()).jwkSetUri(provider.jwksUri())
        .issuerUri(provider.issuerUrl()).userNameAttributeName(provider.subjectClaim())
        .providerConfigurationMetadata(metadata);
    if(provider.userInfoEndpoint()!=null&&!provider.userInfoEndpoint().isBlank())
      builder.userInfoUri(provider.userInfoEndpoint());
    return builder.build();
  }
  private record CachedRegistration(ClientRegistration registration,Instant expiresAt) {}
}

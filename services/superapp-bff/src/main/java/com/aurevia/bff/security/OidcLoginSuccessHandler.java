package com.aurevia.bff.security;

import com.aurevia.bff.api.AuthorizationServiceClient;
import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.client.web.server.ServerOAuth2AuthorizedClientRepository;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.web.server.WebFilterExchange;
import org.springframework.security.web.server.authentication.ServerAuthenticationSuccessHandler;
import org.springframework.security.web.server.authentication.RedirectServerAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/** Finalizes server-side login without exposing any OAuth token to the browser. */
@Component
public class OidcLoginSuccessHandler implements ServerAuthenticationSuccessHandler {
  private final ServerOAuth2AuthorizedClientRepository authorizedClients;
  private final TokenVaultService tokenVault;
  private final AuthorizationServiceClient authorization;
  private final RedirectServerAuthenticationSuccessHandler redirect =
      new RedirectServerAuthenticationSuccessHandler("/");

  public OidcLoginSuccessHandler(ServerOAuth2AuthorizedClientRepository authorizedClients,
      TokenVaultService tokenVault, AuthorizationServiceClient authorization) {
    this.authorizedClients = authorizedClients;
    this.tokenVault = tokenVault;
    this.authorization = authorization;
  }

  @Override
  public Mono<Void> onAuthenticationSuccess(WebFilterExchange webFilterExchange,
      Authentication authentication) {
    if (!(authentication instanceof OAuth2AuthenticationToken oauth)) {
      return Mono.error(new IllegalStateException("OIDC authentication required"));
    }
    var exchange = webFilterExchange.getExchange();
    // A Superset session belongs to the previous public-IAM identity. Expire it
    // on every successful OIDC login so Remote User authentication cannot reuse
    // another user's Superset roles in a new Super App session.
    VaultLogoutHandler.expireSupersetSession(exchange);
    return authorizedClients.loadAuthorizedClient(
            oauth.getAuthorizedClientRegistrationId(), authentication, exchange)
        .switchIfEmpty(Mono.error(new IllegalStateException("Authorized client missing")))
        .flatMap(client -> {
          Instant accessExpiry = client.getAccessToken().getExpiresAt();
          if (accessExpiry == null) accessExpiry = Instant.now().plusSeconds(300);
          String refreshToken = client.getRefreshToken() == null
              ? null : client.getRefreshToken().getTokenValue();
          var tokens = new TokenVaultService.Tokens(
              client.getAccessToken().getTokenValue(), refreshToken, accessExpiry,
              refreshToken==null?accessExpiry:accessExpiry.plusSeconds(1800),
              oauth.getAuthorizedClientRegistrationId());
          return authorization.syncLogin(identity(oauth,client.getClientRegistration()))
              .then(exchange.getSession())
              .flatMap(session -> {
                Object previous = session.getAttribute(VaultLogoutHandler.HANDLE);
                Mono<Boolean> deletePrevious = previous instanceof String handle
                    ? tokenVault.delete(handle) : Mono.just(false);
                return deletePrevious.then(tokenVault.store(tokens))
                    .flatMap(handle -> {
                      session.getAttributes().put(VaultLogoutHandler.HANDLE, handle);
                      return session.changeSessionId();
                    });
              });
        })
        .then(authorizedClients.removeAuthorizedClient(
            oauth.getAuthorizedClientRegistrationId(), authentication, exchange))
        .then(redirect.onAuthenticationSuccess(webFilterExchange, authentication));
  }

  private static Map<String, Object> identity(OAuth2AuthenticationToken authentication,
      ClientRegistration registration) {
    Map<String, Object> claims = authentication.getPrincipal().getAttributes();
    Map<String,Object> metadata=registration.getProviderDetails().getConfigurationMetadata();
    String subjectClaim=string(metadata.get(DynamicClientRegistrationRepository.SUBJECT_CLAIM),"sub");
    String usernameClaim=string(metadata.get(DynamicClientRegistrationRepository.USERNAME_CLAIM),"preferred_username");
    String groupsClaim=string(metadata.get(DynamicClientRegistrationRepository.GROUPS_CLAIM),"groups");
    String subject = string(claims.get(subjectClaim), authentication.getName());
    String issuer = authentication.getPrincipal() instanceof OidcUser oidc
        && oidc.getIdToken().getIssuer() != null
        ? oidc.getIdToken().getIssuer().toString()
        : string(claims.get("iss"), "unknown-issuer");
    List<Map<String, String>> groups = new ArrayList<>();
    String expectedIssuer=registration.getProviderDetails().getIssuerUri();
    if(!issuer.equals(expectedIssuer))throw new IllegalStateException("Validated issuer does not match registration");
    Object claimGroups = claims.get(groupsClaim);
    if (claimGroups instanceof Iterable<?> values) {
      for (Object value : values) {
        String path = String.valueOf(value);
        groups.add(Map.of("externalId", path, "path", path,
            "displayName", path.substring(path.lastIndexOf('/') + 1)));
      }
    }
    Map<String, Object> identity = new LinkedHashMap<>();
    identity.put("providerCode",registration.getRegistrationId());
    identity.put("issuer", issuer);
    identity.put("subject", subject);
    identity.put("username", string(claims.get(usernameClaim), subject));
    identity.put("displayName", string(claims.get("name"), subject));
    identity.put("email", claims.get("email"));
    identity.put("groups", groups);
    // These values originate only from the validated OIDC principal. Browser input is never used.
    identity.put("distinguishedName", firstClaim(claims,"distinguished_name","distinguishedName","ldap_dn"));
    identity.put("ouExternalId", firstClaim(claims,"ou_object_guid","ouObjectGuid"));
    identity.put("directoryExternalId", firstClaim(claims,"ldap_user_id","LDAP_ID","objectGUID"));
    Map<String,String> attributes=new LinkedHashMap<>();
    for(String allowed:List.of("department","title","employeeType")) {
      Object value=claims.get(allowed);if(value!=null)attributes.put(allowed,String.valueOf(value));
    }
    identity.put("attributes",attributes);
    return identity;
  }

  private static Object firstClaim(Map<String,Object> claims,String... names) {
    for(String name:names) { Object value=claims.get(name);if(value!=null&&!String.valueOf(value).isBlank())return value; }
    return null;
  }

  private static String string(Object value, String fallback) {
    return value == null || String.valueOf(value).isBlank() ? fallback : String.valueOf(value);
  }
}

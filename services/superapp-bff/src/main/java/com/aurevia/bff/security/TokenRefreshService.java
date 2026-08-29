package com.aurevia.bff.security;

import java.time.Duration;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

/** Refreshes Public-IAM tokens inside the server-side vault; tokens never reach the browser. */
@Service
public class TokenRefreshService {
  private final WebClient client;
  private final RefreshCoordinator coordinator;
  private final TokenVaultService vault;
  private final String tokenUri;
  private final String clientId;
  private final String clientSecret;

  public TokenRefreshService(WebClient.Builder builder, RefreshCoordinator coordinator,
      TokenVaultService vault,
      @Value("${spring.security.oauth2.client.provider.public-iam.token-uri}") String tokenUri,
      @Value("${spring.security.oauth2.client.registration.public-iam.client-id}") String clientId,
      @Value("${spring.security.oauth2.client.registration.public-iam.client-secret}") String clientSecret) {
    this.client = builder.build();
    this.coordinator = coordinator;
    this.vault = vault;
    this.tokenUri = tokenUri;
    this.clientId = clientId;
    this.clientSecret = clientSecret;
  }

  public Mono<TokenVaultService.Tokens> ensureFresh(String handle,
      TokenVaultService.Tokens tokens) {
    if (tokens.expiresAt().isAfter(Instant.now().plusSeconds(15))) {
      return Mono.just(tokens);
    }
    return refresh(handle, tokens);
  }

  public Mono<TokenVaultService.Tokens> refresh(String handle,
      TokenVaultService.Tokens staleTokens) {
    if (staleTokens.refreshToken() == null || staleTokens.refreshToken().isBlank()) {
      return Mono.error(new ResponseStatusException(HttpStatus.UNAUTHORIZED,
          "Public IAM session cannot be refreshed"));
    }
    return coordinator.once(handle, () -> requestRefresh(staleTokens)
        .flatMap(refreshed -> vault.write(handle, refreshed).thenReturn(refreshed)));
  }

  private Mono<TokenVaultService.Tokens> requestRefresh(TokenVaultService.Tokens staleTokens) {
    var form = new LinkedMultiValueMap<String, String>();
    form.add("grant_type", "refresh_token");
    form.add("refresh_token", staleTokens.refreshToken());
    form.add("client_id", clientId);
    form.add("client_secret", clientSecret);
    return client.post().uri(tokenUri)
        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
        .body(BodyInserters.fromFormData(form))
        .retrieve()
        .onStatus(status -> status.is4xxClientError(), response ->
            Mono.error(new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                "Public IAM refresh rejected")))
        .bodyToMono(TokenResponse.class)
        .timeout(Duration.ofSeconds(10))
        .map(response -> {
          if (response.access_token == null || response.access_token.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                "Public IAM refresh response is invalid");
          }
          Instant accessExpiry = Instant.now().plusSeconds(Math.max(30, response.expires_in));
          String refreshToken = response.refresh_token == null || response.refresh_token.isBlank()
              ? staleTokens.refreshToken() : response.refresh_token;
          Instant vaultExpiry = staleTokens.vaultExpiresAt() == null
              ? accessExpiry.plus(Duration.ofMinutes(30)) : staleTokens.vaultExpiresAt();
          if (vaultExpiry.isBefore(accessExpiry)) vaultExpiry = accessExpiry;
          return new TokenVaultService.Tokens(response.access_token, refreshToken,
              accessExpiry, vaultExpiry);
        });
  }

  private static final class TokenResponse {
    public String access_token;
    public String refresh_token;
    public long expires_in;
  }
}

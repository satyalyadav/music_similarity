package com.music.api.auth;

import java.net.URI;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.web.util.UriUtils;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.music.api.auth.OAuthStateStore.OAuthState;
import com.music.api.spotify.SpotifyApiClient;
import com.music.api.spotify.SpotifyUserProfile;

@Service
public class SpotifyAuthService {

    private static final Logger log = LoggerFactory.getLogger(SpotifyAuthService.class);
    private static final String AUTHORIZATION_ENDPOINT = "https://accounts.spotify.com/authorize";
    private static final List<String> REQUIRED_SCOPES = List.of("user-top-read", "user-read-recently-played");

    private final SpotifyOAuthProperties properties;
    private final OAuthStateStore stateStore;
    private final WebClient spotifyAccountsClient;
    private final SpotifyApiClient spotifyApiClient;
    private final UserAuthRepository userAuthRepository;

    public SpotifyAuthService(
        SpotifyOAuthProperties properties,
        OAuthStateStore stateStore,
        WebClient spotifyAccountsClient,
        SpotifyApiClient spotifyApiClient,
        UserAuthRepository userAuthRepository
    ) {
        this.properties = properties;
        this.stateStore = stateStore;
        this.spotifyAccountsClient = spotifyAccountsClient;
        this.spotifyApiClient = spotifyApiClient;
        this.userAuthRepository = userAuthRepository;
    }

    public AuthorizationRequest startAuthorization(Optional<String> redirectUri) {
        Assert.hasText(properties.getClientId(), "Spotify client ID must be configured");
        Assert.hasText(properties.getClientSecret(), "Spotify client secret must be configured");

        String targetRedirect = redirectUri.filter(uri -> !uri.isBlank())
            .orElse("/");

        OAuthState state = stateStore.create(targetRedirect);
        String scopeParam = String.join(" ", REQUIRED_SCOPES);
        String encodedScope = UriUtils.encode(scopeParam, java.nio.charset.StandardCharsets.UTF_8);
        String encodedRedirect = UriUtils.encode(properties.getRedirectUri(), java.nio.charset.StandardCharsets.UTF_8);
        URI authorizationUri = UriComponentsBuilder.fromUriString(AUTHORIZATION_ENDPOINT)
            .queryParam("client_id", properties.getClientId())
            .queryParam("response_type", "code")
            .queryParam("redirect_uri", encodedRedirect)
            .queryParam("scope", encodedScope)
            .queryParam("state", state.value())
            .build(true)
            .toUri();

        return new AuthorizationRequest(authorizationUri, state.value());
    }

    public AuthResult completeAuthorization(String code, String stateParam) {
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("Missing authorization code");
        }
        OAuthState state = stateStore.consume(stateParam)
            .orElseThrow(() -> new IllegalStateException("Invalid or expired OAuth state"));

        TokenResponse tokenResponse = exchangeAuthorizationCode(code);
        SpotifyUserProfile profile = spotifyApiClient.getCurrentUserProfile(tokenResponse.accessToken());

        String scopes = normalizeScopes(tokenResponse.scope());
        UserAuth persisted = userAuthRepository.findBySpotifyId(profile.id())
            .map(existing -> userAuthRepository.updateTokens(existing.userId(), tokenResponse.accessToken(), tokenResponse.refreshToken(), scopes))
            .orElseGet(() -> userAuthRepository.insert(UUID.randomUUID(), profile.id(), tokenResponse.accessToken(), tokenResponse.refreshToken(), scopes));

        return new AuthResult(persisted.userId(), profile, state.redirectUri());
    }

    public UserAuth refreshAccessToken(UserAuth userAuth) {
        try {
            TokenResponse refreshed = refreshToken(userAuth.refreshToken());
            String scopes = normalizeScopes(refreshed.scope());
            return userAuthRepository.updateTokens(
                userAuth.userId(),
                refreshed.accessToken(),
                refreshed.refreshToken(),
                scopes
            );
        } catch (WebClientResponseException ex) {
            log.warn("Unable to refresh Spotify token for user {}: {}", userAuth.userId(), ex.getMessage());
            throw ex;
        }
    }

    private TokenResponse exchangeAuthorizationCode(String code) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "authorization_code");
        form.add("code", code);
        form.add("redirect_uri", properties.getRedirectUri());

        return requestToken(form);
    }

    private TokenResponse refreshToken(String refreshToken) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "refresh_token");
        form.add("refresh_token", refreshToken);
        return requestToken(form);
    }

    private TokenResponse requestToken(MultiValueMap<String, String> form) {
        try {
            return spotifyAccountsClient.post()
                .uri("/api/token")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .headers(headers -> headers.setBasicAuth(properties.getClientId(), properties.getClientSecret()))
                .body(BodyInserters.fromFormData(form))
                .retrieve()
                .bodyToMono(TokenResponse.class)
                .block();
        } catch (WebClientResponseException ex) {
            log.error("Spotify token request failed: {}", ex.getResponseBodyAsString());
            throw ex;
        }
    }

    private String normalizeScopes(String rawScopes) {
        if (rawScopes == null || rawScopes.isBlank()) {
            return String.join(" ", REQUIRED_SCOPES);
        }
        return rawScopes;
    }

    public record AuthorizationRequest(URI authorizationUri, String state) {}

    public record AuthResult(UUID userId, SpotifyUserProfile profile, String redirectUri) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TokenResponse(
        @JsonProperty("access_token") String accessToken,
        @JsonProperty("token_type") String tokenType,
        @JsonProperty("expires_in") Integer expiresIn,
        @JsonProperty("refresh_token") String refreshToken,
        @JsonProperty("scope") String scope
    ) {}
}

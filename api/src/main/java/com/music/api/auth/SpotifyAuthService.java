package com.music.api.auth;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.Arrays;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;

import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.security.oauth2.core.endpoint.PkceParameterNames;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.music.api.auth.OAuthStateStore.OAuthState;
import com.music.api.spotify.SpotifyApiClient;
import com.music.api.spotify.SpotifyUserProfile;

@Service
public class SpotifyAuthService {

    private static final Logger log = LoggerFactory.getLogger(SpotifyAuthService.class);
    private static final Set<String> REQUIRED_SCOPES = Set.of(
        "user-top-read",
        "user-read-recently-played",
        "playlist-modify-public",
        "playlist-modify-private",
        "user-read-private",
        "user-read-playback-state",
        "user-modify-playback-state",
        "streaming"
    );

    private final OAuthStateStore stateStore;
    private final WebClient spotifyAccountsClient;
    private final SpotifyApiClient spotifyApiClient;
    private final UserAuthRepository userAuthRepository;
    private final ClientRegistration clientRegistration;

    public SpotifyAuthService(
        OAuthStateStore stateStore,
        @Qualifier("spotifyAccountsClient") WebClient spotifyAccountsClient,
        SpotifyApiClient spotifyApiClient,
        UserAuthRepository userAuthRepository,
        ClientRegistrationRepository clientRegistrationRepository
    ) {
        this.stateStore = stateStore;
        this.spotifyAccountsClient = spotifyAccountsClient;
        this.spotifyApiClient = spotifyApiClient;
        this.userAuthRepository = userAuthRepository;
    ClientRegistration registration = clientRegistrationRepository.findByRegistrationId("spotify");
        Assert.notNull(registration, "Spotify client registration must be configured");
        Assert.isTrue(AuthorizationGrantType.AUTHORIZATION_CODE.equals(registration.getAuthorizationGrantType()),
            "Spotify registration must use authorization_code grant type");
        this.clientRegistration = registration;
    }

    public AuthorizationRequest startAuthorization(Optional<String> redirectUri) {
        Assert.hasText(clientRegistration.getClientId(), "Spotify client ID must be configured");
        Assert.hasText(resolveRedirectUri(), "Spotify redirect URI must be configured");

        String targetRedirect = redirectUri.filter(uri -> !uri.isBlank())
            .orElse("/");

        OAuthState state = stateStore.create(targetRedirect, stateValue -> buildAuthorizationRequest(stateValue));
        return new AuthorizationRequest(URI.create(state.authorizationRequest().getAuthorizationRequestUri()), state.value());
    }

    public AuthResult completeAuthorization(String code, String stateParam) {
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("Missing authorization code");
        }
        OAuthState state = stateStore.consume(stateParam)
            .orElseThrow(() -> new IllegalStateException("Invalid or expired OAuth state"));

        OAuth2AuthorizationAccess tokenResponse = exchangeAuthorizationCode(code, state.authorizationRequest());
        SpotifyUserProfile profile = spotifyApiClient.getCurrentUserProfile(tokenResponse.accessToken());

        String scopes = normalizeScopes(tokenResponse.scopes());
        UserAuth persisted = userAuthRepository.findBySpotifyId(profile.id())
            .map(existing -> userAuthRepository.updateTokens(existing.userId(), tokenResponse.accessToken(), tokenResponse.refreshToken(), scopes))
            .orElseGet(() -> userAuthRepository.insert(UUID.randomUUID(), profile.id(), tokenResponse.accessToken(), tokenResponse.refreshToken(), scopes));

        return new AuthResult(persisted.userId(), profile, state.redirectUri());
    }

    public UserAuth refreshAccessToken(UserAuth userAuth) {
        try {
            OAuth2AuthorizationAccess refreshed = refreshToken(userAuth);
            String scopes = normalizeScopes(refreshed.scopes());
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

    private OAuth2AuthorizationAccess exchangeAuthorizationCode(String code, OAuth2AuthorizationRequest authorizationRequest) {
        String redirectUri = resolveRedirectUri();
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "authorization_code");
        form.add("code", code);
        form.add("redirect_uri", redirectUri);
        Object verifier = authorizationRequest.getAttribute(PkceParameterNames.CODE_VERIFIER);
        if (verifier instanceof String codeVerifier && !codeVerifier.isBlank()) {
            form.add("code_verifier", codeVerifier);
        }
        TokenResponse response = requestToken(form);
        Set<String> scopes = ScopeParser.parse(response.scope());
        return new OAuth2AuthorizationAccess(
            response.accessToken(),
            response.refreshToken(),
            scopes
        );
    }

    private OAuth2AuthorizationAccess refreshToken(UserAuth userAuth) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "refresh_token");
        form.add("refresh_token", userAuth.refreshToken());
        TokenResponse response = requestToken(form);
        Set<String> scopes = ScopeParser.parse(response.scope());
        String refreshToken = response.refreshToken() != null ? response.refreshToken() : userAuth.refreshToken();
        return new OAuth2AuthorizationAccess(response.accessToken(), refreshToken, scopes);
    }

    private String normalizeScopes(Set<String> scopes) {
        if (scopes == null || scopes.isEmpty()) {
            return String.join(" ", REQUIRED_SCOPES);
        }
        return String.join(" ", scopes);
    }

    private OAuth2AuthorizationRequest buildAuthorizationRequest(String stateValue) {
        String redirectUri = resolveRedirectUri();
        PkcePair pkce = PkcePair.generate();

        return OAuth2AuthorizationRequest.authorizationCode()
            .authorizationUri(clientRegistration.getProviderDetails().getAuthorizationUri())
            .clientId(clientRegistration.getClientId())
            .redirectUri(redirectUri)
            .scopes(resolveScopes())
            .state(stateValue)
            .additionalParameters(params -> {
                params.put(PkceParameterNames.CODE_CHALLENGE, pkce.codeChallenge());
                params.put(PkceParameterNames.CODE_CHALLENGE_METHOD, pkce.codeChallengeMethod());
            })
            .attributes(attrs -> attrs.put(PkceParameterNames.CODE_VERIFIER, pkce.codeVerifier()))
            .build();
    }

    public record AuthorizationRequest(URI authorizationUri, String state) {}

    public record AuthResult(UUID userId, SpotifyUserProfile profile, String redirectUri) {}

    private record OAuth2AuthorizationAccess(String accessToken, String refreshToken, Set<String> scopes) {}

    private record PkcePair(String codeVerifier, String codeChallenge, String codeChallengeMethod) {
        static PkcePair generate() {
            String codeVerifier = randomUrlSafeString();
            String codeChallenge = createCodeChallenge(codeVerifier);
            return new PkcePair(codeVerifier, codeChallenge, "S256");
        }

        private static String randomUrlSafeString() {
            byte[] bytes = new byte[32];
            new java.security.SecureRandom().nextBytes(bytes);
            return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        }

        private static String createCodeChallenge(String verifier) {
            try {
                MessageDigest digest = MessageDigest.getInstance("SHA-256");
                byte[] hash = digest.digest(verifier.getBytes(StandardCharsets.US_ASCII));
                return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
            } catch (NoSuchAlgorithmException ex) {
                throw new IllegalStateException("SHA-256 algorithm not available", ex);
            }
        }
    }

    private String resolveRedirectUri() {
        return clientRegistration.getRedirectUri();
    }

    private Set<String> resolveScopes() {
        java.util.LinkedHashSet<String> merged = new java.util.LinkedHashSet<>(REQUIRED_SCOPES);
        Set<String> configured = clientRegistration.getScopes();
        if (configured != null) {
            merged.addAll(configured);
        }
        return Set.copyOf(merged);
    }

    private TokenResponse requestToken(MultiValueMap<String, String> form) {
        try {
            TokenResponse response = spotifyAccountsClient.post()
                .uri("/api/token")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .headers(headers -> headers.setBasicAuth(clientRegistration.getClientId(), clientRegistration.getClientSecret()))
                .body(BodyInserters.fromFormData(form))
                .retrieve()
                .bodyToMono(TokenResponse.class)
                .block();
            if (response == null) {
                throw new IllegalStateException("Spotify token endpoint returned empty response");
            }
            return response;
        } catch (WebClientResponseException ex) {
            log.error("Spotify token request failed: {}", ex.getResponseBodyAsString());
            throw ex;
        }
    }

    private static final class ScopeParser {
        private static Set<String> parse(String scopesValue) {
            if (scopesValue == null || scopesValue.isBlank()) {
                return REQUIRED_SCOPES;
            }
            LinkedHashSet<String> ordered = Arrays.stream(scopesValue.split("\\s+"))
                .map(String::trim)
                .filter(token -> !token.isBlank())
                .collect(Collectors.toCollection(LinkedHashSet::new));
            if (ordered.isEmpty()) {
                return REQUIRED_SCOPES;
            }
            return Set.copyOf(ordered);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record TokenResponse(
        @JsonProperty("access_token") String accessToken,
        @JsonProperty("refresh_token") String refreshToken,
        @JsonProperty("scope") String scope,
        @JsonProperty("token_type") String tokenType,
        @JsonProperty("expires_in") Integer expiresIn
    ) {}
}

package com.music.api.web;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.server.ResponseStatusException;

import com.music.api.auth.SpotifyAuthService;
import com.music.api.auth.UserAuth;
import com.music.api.auth.UserAuthRepository;
import com.music.api.spotify.SpotifyApiClient;
import com.music.api.spotify.SpotifyUserProfile;
import com.music.api.web.dto.PlaybackTokenResponse;

@RestController
public class PlaybackController {

    private static final String SCOPE_STREAMING = "streaming";
    private static final String SCOPE_PLAYBACK_STATE = "user-read-playback-state";
    private static final String SCOPE_PLAYBACK_MODIFY = "user-modify-playback-state";
    private static final String SCOPE_USER_PRIVATE = "user-read-private";

    private final UserAuthRepository userAuthRepository;
    private final SpotifyAuthService spotifyAuthService;
    private final SpotifyApiClient spotifyApiClient;

    public PlaybackController(
        UserAuthRepository userAuthRepository,
        SpotifyAuthService spotifyAuthService,
        SpotifyApiClient spotifyApiClient
    ) {
        this.userAuthRepository = userAuthRepository;
        this.spotifyAuthService = spotifyAuthService;
        this.spotifyApiClient = spotifyApiClient;
    }

    @GetMapping("/playback/token")
    public ResponseEntity<PlaybackTokenResponse> playbackToken(@RequestParam("userId") UUID userId) {
        UserAuth userAuth = userAuthRepository.findByUserId(userId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User authorization not found"));

        // Try to use existing token first, only refresh if it fails
        UserAuth refreshed;
        SpotifyUserProfile profile;
        try {
            // Validate token and get profile in one call
            profile = spotifyApiClient.getCurrentUserProfile(userAuth.accessToken());
            refreshed = userAuth; // Token is still valid
        } catch (WebClientResponseException ex) {
            if (ex.getStatusCode().value() == HttpStatus.UNAUTHORIZED.value()) {
                // Token expired, refresh it
                refreshed = spotifyAuthService.refreshAccessToken(userAuth);
            } else {
                // Other error, still try to refresh as fallback
                refreshed = spotifyAuthService.refreshAccessToken(userAuth);
            }
            // Get profile with refreshed token
            profile = spotifyApiClient.getCurrentUserProfile(refreshed.accessToken());
        }
        String scopes = refreshed.scopes() != null ? refreshed.scopes() : "";

        boolean hasStreaming = hasScope(scopes, SCOPE_STREAMING);
        boolean hasPlaybackState = hasScope(scopes, SCOPE_PLAYBACK_STATE);
        boolean hasPlaybackModify = hasScope(scopes, SCOPE_PLAYBACK_MODIFY);
        boolean hasUserPrivate = hasScope(scopes, SCOPE_USER_PRIVATE);

        List<String> missingScopes = new ArrayList<>();
        if (!hasStreaming) {
            missingScopes.add(SCOPE_STREAMING);
        }
        if (!hasPlaybackState) {
            missingScopes.add(SCOPE_PLAYBACK_STATE);
        }
        if (!hasPlaybackModify) {
            missingScopes.add(SCOPE_PLAYBACK_MODIFY);
        }
        if (!hasUserPrivate) {
            missingScopes.add(SCOPE_USER_PRIVATE);
        }
        Boolean premium = profile.product() != null ? profile.product().equalsIgnoreCase("premium") : null;
        boolean eligible = missingScopes.isEmpty() && Boolean.TRUE.equals(premium);

        String accessToken = eligible ? refreshed.accessToken() : null;
        PlaybackTokenResponse payload = new PlaybackTokenResponse(
            eligible,
            premium,
            profile.product(),
            missingScopes,
            accessToken
        );
        return ResponseEntity.ok().cacheControl(org.springframework.http.CacheControl.noStore()).body(payload);
    }

    private boolean hasScope(String scopes, String expected) {
        if (scopes == null || scopes.isBlank()) {
            return false;
        }
        for (String scope : scopes.split("\\s+")) {
            if (expected.equals(scope)) {
                return true;
            }
        }
        return false;
    }
}

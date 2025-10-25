package com.music.api.playlists;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.server.ResponseStatusException;

import com.music.api.auth.SpotifyAuthService;
import com.music.api.auth.UserAuth;
import com.music.api.auth.UserAuthRepository;
import com.music.api.spotify.SpotifyApiClient;
import com.music.api.spotify.SpotifyApiClient.CreatedPlaylist;
import com.music.api.spotify.TrackIdNormalizer;

@Service
public class PlaylistService {

    private static final int SPOTIFY_TRACK_CHUNK = 100;

    private final UserAuthRepository userAuthRepository;
    private final SpotifyAuthService spotifyAuthService;
    private final SpotifyApiClient spotifyApiClient;

    public PlaylistService(
        UserAuthRepository userAuthRepository,
        SpotifyAuthService spotifyAuthService,
        SpotifyApiClient spotifyApiClient
    ) {
        this.userAuthRepository = userAuthRepository;
        this.spotifyAuthService = spotifyAuthService;
        this.spotifyApiClient = spotifyApiClient;
    }

    public PlaylistResult createPlaylist(UUID userId, String name, List<String> rawTrackIds, boolean publicPlaylist) {
        if (name == null || name.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Playlist name is required");
        }
        UserAuth userAuth = userAuthRepository.findByUserId(userId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User authorization not found"));

        List<String> normalizedTrackIds = normalizeTrackIds(rawTrackIds);

        CreatedPlaylist created = createPlaylistWithRetry(userAuth, name.trim(), publicPlaylist);
        if (!normalizedTrackIds.isEmpty()) {
            addTracksWithRetry(userAuth, created.id(), normalizedTrackIds);
        }

        return new PlaylistResult(created.id(), created.externalUrls() != null ? created.externalUrls().spotify() : null, normalizedTrackIds.size());
    }

    private CreatedPlaylist createPlaylistWithRetry(UserAuth userAuth, String name, boolean publicPlaylist) {
        try {
            return spotifyApiClient.createPlaylist(userAuth.accessToken(), userAuth.spotifyId(), name, publicPlaylist);
        } catch (WebClientResponseException ex) {
            if (ex.getStatusCode().value() == 401) {
                UserAuth refreshed = spotifyAuthService.refreshAccessToken(userAuth);
                return spotifyApiClient.createPlaylist(refreshed.accessToken(), refreshed.spotifyId(), name, publicPlaylist);
            }
            throw translatePlaylistError(ex);
        }
    }

    private void addTracksWithRetry(UserAuth userAuth, String playlistId, List<String> trackIds) {
        List<String> uris = trackIds.stream()
            .map(TrackIdNormalizer::toSpotifyUri)
            .toList();

        UserAuth currentAuth = userAuth;
        for (int i = 0; i < uris.size(); i += SPOTIFY_TRACK_CHUNK) {
            List<String> batch = uris.subList(i, Math.min(uris.size(), i + SPOTIFY_TRACK_CHUNK));
            currentAuth = addTrackBatch(currentAuth, playlistId, batch);
        }
    }

    private UserAuth addTrackBatch(UserAuth userAuth, String playlistId, List<String> uris) {
        try {
            spotifyApiClient.addTracksToPlaylist(userAuth.accessToken(), playlistId, uris);
            return userAuth;
        } catch (WebClientResponseException ex) {
            if (ex.getStatusCode().value() == 401) {
                UserAuth refreshed = spotifyAuthService.refreshAccessToken(userAuth);
                spotifyApiClient.addTracksToPlaylist(refreshed.accessToken(), playlistId, uris);
                return refreshed;
            }
            throw translatePlaylistError(ex);
        }
    }

    private ResponseStatusException translatePlaylistError(WebClientResponseException ex) {
        if (ex.getStatusCode().value() == 403) {
            return new ResponseStatusException(HttpStatus.FORBIDDEN, "Spotify rejected playlist request, check scopes");
        }
        if (ex.getStatusCode().value() == 404) {
            return new ResponseStatusException(HttpStatus.NOT_FOUND, "Spotify playlist resource not found");
        }
        return new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Spotify playlist request failed");
    }

    private List<String> normalizeTrackIds(List<String> rawTrackIds) {
        if (rawTrackIds == null || rawTrackIds.isEmpty()) {
            return List.of();
        }
        List<String> normalized = new ArrayList<>();
        for (String raw : rawTrackIds) {
            if (Objects.requireNonNullElse(raw, "").isBlank()) {
                continue;
            }
            try {
                normalized.add(TrackIdNormalizer.normalize(raw));
            } catch (IllegalArgumentException ex) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage());
            }
        }
        return normalized;
    }

    public record PlaylistResult(
        String playlistId,
        String spotifyUrl,
        int trackCount
    ) {}
}

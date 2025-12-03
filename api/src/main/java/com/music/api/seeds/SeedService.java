package com.music.api.seeds;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.server.ResponseStatusException;

import com.music.api.auth.SpotifyAuthService;
import com.music.api.auth.UserAuth;
import com.music.api.auth.UserAuthRepository;
import com.music.api.spotify.SpotifyApiClient;
import com.music.api.spotify.SpotifyApiClient.SeedTrack;

@Service
public class SeedService {

    private static final int DEFAULT_LIMIT = 20;

    private final UserAuthRepository userAuthRepository;
    private final SpotifyApiClient spotifyApiClient;
    private final SpotifyAuthService spotifyAuthService;

    public SeedService(
        UserAuthRepository userAuthRepository,
        SpotifyApiClient spotifyApiClient,
        SpotifyAuthService spotifyAuthService
    ) {
        this.userAuthRepository = userAuthRepository;
        this.spotifyApiClient = spotifyApiClient;
        this.spotifyAuthService = spotifyAuthService;
    }

    public List<SeedTrackView> getCombinedSeedTracks(UUID userId, int requestedLimit, String timeRange) {
        int limit = normalizeLimit(requestedLimit);
        UserAuth userAuth = getUserAuth(userId);

        FetchResult topTracksResult = fetchWithRefresh(userAuth, token -> spotifyApiClient.getTopTracks(token, limit, normalizeTimeRange(timeRange)));
        UserAuth latestAuth = topTracksResult.userAuth();
        List<SeedTrack> combined = new ArrayList<>(topTracksResult.tracks());

        if (combined.size() < limit) {
            FetchResult recentResult = fetchWithRefresh(latestAuth, token -> spotifyApiClient.getRecentlyPlayed(token, limit));
            latestAuth = recentResult.userAuth();
            combined.addAll(recentResult.tracks());
        }

        List<SeedTrackView> deduped = deduplicateAndLimit(combined, limit);
        if (deduped.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Spotify did not return any candidate tracks");
        }
        return deduped;
    }

    public List<SeedTrackView> getTopSeedTracks(UUID userId, int requestedLimit, String timeRange) {
        int limit = normalizeLimit(requestedLimit);
        UserAuth userAuth = getUserAuth(userId);

        FetchResult topTracksResult = fetchWithRefresh(userAuth, token -> spotifyApiClient.getTopTracks(token, limit, normalizeTimeRange(timeRange)));
        List<SeedTrack> tracks = topTracksResult.tracks();
        List<SeedTrackView> deduped = deduplicateAndLimit(tracks, limit);
        if (deduped.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Spotify did not return any candidate tracks");
        }
        return deduped;
    }

    public List<SeedTrackView> getRecentSeedTracks(UUID userId, int requestedLimit) {
        int limit = normalizeLimit(requestedLimit);
        UserAuth userAuth = getUserAuth(userId);

        FetchResult recentResult = fetchWithRefresh(userAuth, token -> spotifyApiClient.getRecentlyPlayed(token, limit));
        List<SeedTrack> tracks = recentResult.tracks();

        List<SeedTrackView> deduped = deduplicateAndLimit(tracks, limit);
        if (deduped.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Spotify did not return any candidate tracks");
        }
        return deduped;
    }

    public List<SeedTrackView> searchTracks(UUID userId, String query, int requestedLimit) {
        int limit = normalizeLimit(requestedLimit);
        UserAuth userAuth = getUserAuth(userId);

        FetchResult searchResult = fetchWithRefresh(userAuth, token -> spotifyApiClient.searchTracks(token, query, limit));
        List<SeedTrack> tracks = searchResult.tracks();

        List<SeedTrackView> deduped = deduplicateAndLimit(tracks, limit);
        return deduped;
    }

    private int normalizeLimit(int requestedLimit) {
        return requestedLimit > 0 ? Math.min(requestedLimit, 50) : DEFAULT_LIMIT;
    }

    private UserAuth getUserAuth(UUID userId) {
        return userAuthRepository.findByUserId(userId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User authorization not found"));
    }

    private String normalizeTimeRange(String requestedRange) {
        if (requestedRange == null) {
            return "short_term";
        }
        return switch (requestedRange.toLowerCase()) {
            case "medium_term" -> "medium_term";
            case "long_term" -> "long_term";
            default -> "short_term";
        };
    }

    private List<SeedTrackView> deduplicateAndLimit(List<SeedTrack> tracks, int limit) {
        Map<String, SeedTrack> dedup = new LinkedHashMap<>();
        for (SeedTrack track : tracks) {
            if (track == null || track.id() == null) {
                continue;
            }
            dedup.putIfAbsent(track.id(), track);
        }
        return dedup.values().stream()
            .limit(limit)
            .map(SeedTrackView::fromSeedTrack)
            .collect(Collectors.toList());
    }

    private FetchResult fetchWithRefresh(UserAuth userAuth, Function<String, List<SeedTrack>> fetcher) {
        int maxRetries = 3;
        int attempt = 0;
        UserAuth currentAuth = userAuth;
        
        while (attempt < maxRetries) {
            try {
                List<SeedTrack> result = fetcher.apply(currentAuth.accessToken());
                return new FetchResult(currentAuth, result);
            } catch (WebClientResponseException ex) {
                if (ex.getStatusCode().value() == HttpStatus.UNAUTHORIZED.value()) {
                    attempt++;
                    if (attempt >= maxRetries) {
                        // Final attempt: refresh the token
                        UserAuth refreshed = spotifyAuthService.refreshAccessToken(currentAuth);
                        try {
                            List<SeedTrack> result = fetcher.apply(refreshed.accessToken());
                            return new FetchResult(refreshed, result);
                        } catch (WebClientResponseException finalEx) {
                            throw finalEx;
                        }
                    }
                    
                    // Re-read from database in case another process (e.g., playback) refreshed the token
                    UserAuth latestAuth = userAuthRepository.findByUserId(currentAuth.userId())
                        .orElse(currentAuth);
                    
                    // If token changed, use the new one for next retry
                    if (!latestAuth.accessToken().equals(currentAuth.accessToken())) {
                        currentAuth = latestAuth;
                        // Small delay to let any in-flight refresh complete
                        try {
                            Thread.sleep(50 * attempt); // 50ms, 100ms, 150ms delays
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            throw new RuntimeException("Interrupted during token refresh retry", ie);
                        }
                        continue; // Retry with new token
                    }
                    
                    // Token unchanged, refresh it
                    currentAuth = spotifyAuthService.refreshAccessToken(latestAuth);
                    // Small delay before retry
                    try {
                        Thread.sleep(50 * attempt);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException("Interrupted during token refresh retry", ie);
                    }
                    continue; // Retry with refreshed token
                }
                if (ex.getStatusCode().value() == HttpStatus.FORBIDDEN.value()) {
                    return new FetchResult(currentAuth, List.of());
                }
                throw ex;
            }
        }
        
        // Should never reach here, but just in case
        throw new RuntimeException("Failed to fetch after " + maxRetries + " attempts");
    }

    private record FetchResult(UserAuth userAuth, List<SeedTrack> tracks) {}
}

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

    public List<SeedTrackView> getSeedTracks(UUID userId, int requestedLimit) {
        int limit = requestedLimit > 0 ? Math.min(requestedLimit, 50) : DEFAULT_LIMIT;
        UserAuth userAuth = userAuthRepository.findByUserId(userId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User authorization not found"));

        FetchResult topTracksResult = fetchWithRefresh(userAuth, token -> spotifyApiClient.getTopTracks(token, limit));
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

    public List<SeedTrackView> getRecentSeedTracks(UUID userId, int requestedLimit) {
        int limit = requestedLimit > 0 ? Math.min(requestedLimit, 50) : DEFAULT_LIMIT;
        UserAuth userAuth = userAuthRepository.findByUserId(userId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User authorization not found"));

        FetchResult recentResult = fetchWithRefresh(userAuth, token -> spotifyApiClient.getRecentlyPlayed(token, limit));
        List<SeedTrack> tracks = recentResult.tracks();

        List<SeedTrackView> deduped = deduplicateAndLimit(tracks, limit);
        if (deduped.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Spotify did not return any candidate tracks");
        }
        return deduped;
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
        try {
            List<SeedTrack> result = fetcher.apply(userAuth.accessToken());
            return new FetchResult(userAuth, result);
        } catch (WebClientResponseException ex) {
            if (ex.getStatusCode().value() == HttpStatus.UNAUTHORIZED.value()) {
                UserAuth refreshed = spotifyAuthService.refreshAccessToken(userAuth);
                List<SeedTrack> result = fetcher.apply(refreshed.accessToken());
                return new FetchResult(refreshed, result);
            }
            if (ex.getStatusCode().value() == HttpStatus.FORBIDDEN.value()) {
                return new FetchResult(userAuth, List.of());
            }
            throw ex;
        }
    }

    private record FetchResult(UserAuth userAuth, List<SeedTrack> tracks) {}
}

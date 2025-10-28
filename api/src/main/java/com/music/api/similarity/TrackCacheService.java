package com.music.api.similarity;

import java.util.Optional;
import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import com.music.api.auth.SpotifyAuthService;
import com.music.api.auth.UserAuth;
import com.music.api.similarity.TrackCacheRepository.TrackCacheEntry;
import com.music.api.spotify.SpotifyApiClient;
import com.music.api.spotify.SpotifyApiClient.SeedTrack;

@Service
public class TrackCacheService {

    private static final Logger log = LoggerFactory.getLogger(TrackCacheService.class);

    private final TrackCacheRepository repository;
    private final SpotifyApiClient spotifyApiClient;
    private final SpotifyAuthService spotifyAuthService;

    public TrackCacheService(
        TrackCacheRepository repository,
        SpotifyApiClient spotifyApiClient,
        SpotifyAuthService spotifyAuthService
    ) {
        this.repository = repository;
        this.spotifyApiClient = spotifyApiClient;
        this.spotifyAuthService = spotifyAuthService;
    }

    public Optional<TrackCacheEntry> getTrack(UserAuth userAuth, String spotifyId) {
        if (spotifyId == null || spotifyId.isBlank()) {
            return Optional.empty();
        }
        Optional<TrackCacheEntry> cached = repository.findFresh(spotifyId);
        if (cached.isPresent() && hasCompleteMetadata(cached.get())) {
            return cached;
        }
        Optional<TrackCacheEntry> refreshed = fetchAndCache(userAuth, spotifyId);
        if (refreshed.isPresent()) {
            return refreshed;
        }
        return cached;
    }

    public void cacheSeedTrack(SeedTrack track) {
        if (track == null || track.id() == null) {
            return;
        }
        TrackCacheEntry entry = new TrackCacheEntry(
            track.id(),
            track.name(),
            track.artist(),
            track.album(),
            track.popularity(),
            track.imageUrl(),
            track.isrc(),
            Instant.now()
        );
        repository.upsert(entry);
    }

    private boolean hasCompleteMetadata(TrackCacheEntry entry) {
        return entry.imageUrl() != null && !entry.imageUrl().isBlank();
    }

    private Optional<TrackCacheEntry> fetchAndCache(UserAuth userAuth, String spotifyId) {
        try {
            SeedTrack track = spotifyApiClient.getTrack(userAuth.accessToken(), spotifyId);
            TrackCacheEntry entry = new TrackCacheEntry(
                track.id(),
                track.name(),
                track.artist(),
                track.album(),
                track.popularity(),
                track.imageUrl(),
                track.isrc(),
                Instant.now()
            );
            repository.upsert(entry);
            return Optional.of(entry);
        } catch (WebClientResponseException ex) {
            if (ex.getStatusCode().value() == 401) {
                UserAuth refreshed = spotifyAuthService.refreshAccessToken(userAuth);
                return fetchAndCache(refreshed, spotifyId);
            }
            log.debug("Spotify track fetch failed for {}: {}", spotifyId, ex.getStatusCode());
            return Optional.empty();
        } catch (Exception ex) {
            log.warn("Track fetch error for {}: {}", spotifyId, ex.getMessage());
            return Optional.empty();
        }
    }
}

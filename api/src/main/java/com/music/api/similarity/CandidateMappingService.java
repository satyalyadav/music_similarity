package com.music.api.similarity;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import com.music.api.auth.SpotifyAuthService;
import com.music.api.auth.UserAuth;
import com.music.api.similarity.IdMapRepository.IdMapEntry;
import com.music.api.similarity.LastFmClient.LastFmTrack;
import com.music.api.similarity.TrackCacheRepository.TrackCacheEntry;
import com.music.api.spotify.SpotifyApiClient;
import com.music.api.spotify.SpotifyApiClient.SeedTrack;

@Service
public class CandidateMappingService {

    private static final Logger log = LoggerFactory.getLogger(CandidateMappingService.class);

    private final IdMapRepository idMapRepository;
    private final SpotifyApiClient spotifyApiClient;
    private final SpotifyAuthService spotifyAuthService;
    private final TrackCacheService trackCacheService;

    public CandidateMappingService(
        IdMapRepository idMapRepository,
        SpotifyApiClient spotifyApiClient,
        SpotifyAuthService spotifyAuthService,
        TrackCacheService trackCacheService
    ) {
        this.idMapRepository = idMapRepository;
        this.spotifyApiClient = spotifyApiClient;
        this.spotifyAuthService = spotifyAuthService;
        this.trackCacheService = trackCacheService;
    }

    public List<MappedTrack> mapCandidates(UserAuth userAuth, List<LastFmTrack> candidates, int desiredCount) {
        int positiveDesired = Math.max(desiredCount, 0);
        int processingBudget = positiveDesired > 0
            ? Math.min(candidates.size(), Math.max(positiveDesired * 3, positiveDesired + 15))
            : candidates.size();
        int mappedThreshold = positiveDesired > 0 ? Math.max(positiveDesired * 2, positiveDesired + 5) : Integer.MAX_VALUE;

        List<MappedTrack> mapped = new ArrayList<>(Math.min(processingBudget, candidates.size()));
        UserAuth currentAuth = userAuth;
        int processed = 0;
        int mappedWithSpotify = 0;
        Set<String> dedupeKeys = new LinkedHashSet<>();

        for (LastFmTrack candidate : candidates) {
            if (processed >= processingBudget) {
                break;
            }
            processed++;

            String normalizedKey = SimilarityKeys.normalize(candidate.artist(), candidate.name());

            Optional<IdMapEntry> cached = idMapRepository.findFresh(normalizedKey);
            MappedTrack mappedTrack;
            if (cached.isPresent()) {
                IdMapEntry entry = cached.get();
                double confidence = entry.confidence() != null ? entry.confidence() : 1.0;
                Optional<TrackCacheEntry> cachedTrack = trackCacheService.getTrack(currentAuth, entry.spotifyId());
                String dedupeKey = buildDedupeKey(
                    cachedTrack.map(TrackCacheEntry::isrc).orElse(null),
                    cachedTrack.map(TrackCacheEntry::artist).orElse(candidate.artist()),
                    cachedTrack.map(TrackCacheEntry::name).orElse(candidate.name())
                );
                if (!dedupeKeys.add(dedupeKey)) {
                    continue;
                }
                mappedTrack = new MappedTrack(
                    candidate,
                    entry.spotifyId(),
                    confidence,
                    true,
                    cachedTrack.map(TrackCacheEntry::imageUrl).orElse(null),
                    cachedTrack.map(TrackCacheEntry::isrc).orElse(null)
                );
            } else {
                SearchResult searchResult = performSearch(currentAuth, candidate);
                if (searchResult != null) {
                    currentAuth = searchResult.userAuth();
                    if (searchResult.track() != null) {
                        double confidence = candidate.matchScore() > 0 ? candidate.matchScore() : 1.0;
                        SeedTrack track = searchResult.track();
                        idMapRepository.upsert(normalizedKey, track.id(), confidence);
                        buildIsrcKey(track.isrc()).ifPresent(isrcKey -> idMapRepository.upsert(isrcKey, track.id(), confidence));
                        trackCacheService.cacheSeedTrack(track);

                        String dedupeKey = buildDedupeKey(track.isrc(), track.artist(), track.name());
                        if (!dedupeKeys.add(dedupeKey)) {
                            continue;
                        }
                        mappedTrack = new MappedTrack(
                            candidate,
                            track.id(),
                            confidence,
                            false,
                            track.imageUrl(),
                            track.isrc()
                        );
                    } else {
                        String dedupeKey = buildDedupeKey(null, candidate.artist(), candidate.name());
                        if (!dedupeKeys.add(dedupeKey)) {
                            continue;
                        }
                        mappedTrack = new MappedTrack(candidate, null, 0.0, false, null, null);
                    }
                } else {
                    String dedupeKey = buildDedupeKey(null, candidate.artist(), candidate.name());
                    if (!dedupeKeys.add(dedupeKey)) {
                        continue;
                    }
                    mappedTrack = new MappedTrack(candidate, null, 0.0, false, null, null);
                }
            }

            mapped.add(mappedTrack);
            if (mappedTrack.spotifyId() != null) {
                mappedWithSpotify++;
                if (mappedWithSpotify >= mappedThreshold) {
                    break;
                }
            }
        }

        return mapped;
    }

    private SearchResult performSearch(UserAuth userAuth, LastFmTrack candidate) {
        try {
            SeedTrack track = spotifyApiClient.searchTrack(userAuth.accessToken(), candidate.name(), candidate.artist());
            return new SearchResult(track, userAuth);
        } catch (WebClientResponseException ex) {
            if (ex.getStatusCode().value() == 401) {
                UserAuth refreshed = spotifyAuthService.refreshAccessToken(userAuth);
                SeedTrack track = spotifyApiClient.searchTrack(refreshed.accessToken(), candidate.name(), candidate.artist());
                return new SearchResult(track, refreshed);
            }
            log.debug("Spotify search failed for {} - {}: {}", candidate.artist(), candidate.name(), ex.getStatusCode());
            return null;
        } catch (Exception ex) {
            log.debug("Spotify search error for {} - {}: {}", candidate.artist(), candidate.name(), ex.getMessage());
            return null;
        }
    }

    public record MappedTrack(
        LastFmTrack source,
        String spotifyId,
        double confidence,
        boolean cached,
        String spotifyImageUrl,
        String isrc
    ) {}

    private record SearchResult(SeedTrack track, UserAuth userAuth) {}

    private String buildDedupeKey(String isrc, String artist, String track) {
        return buildIsrcKey(isrc).orElse("name:" + SimilarityKeys.normalize(artist, track));
    }

    private Optional<String> buildIsrcKey(String isrc) {
        if (isrc == null) {
            return Optional.empty();
        }
        String trimmed = isrc.trim();
        if (trimmed.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of("isrc:" + trimmed.toUpperCase(Locale.ROOT));
    }
}

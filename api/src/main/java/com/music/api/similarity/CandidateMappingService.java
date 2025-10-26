package com.music.api.similarity;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import com.music.api.auth.SpotifyAuthService;
import com.music.api.auth.UserAuth;
import com.music.api.similarity.IdMapRepository.IdMapEntry;
import com.music.api.similarity.LastFmClient.LastFmTrack;
import com.music.api.spotify.SpotifyApiClient;
import com.music.api.spotify.SpotifyApiClient.SeedTrack;

@Service
public class CandidateMappingService {

    private static final Logger log = LoggerFactory.getLogger(CandidateMappingService.class);

    private final IdMapRepository idMapRepository;
    private final SpotifyApiClient spotifyApiClient;
    private final SpotifyAuthService spotifyAuthService;

    public CandidateMappingService(
        IdMapRepository idMapRepository,
        SpotifyApiClient spotifyApiClient,
        SpotifyAuthService spotifyAuthService
    ) {
        this.idMapRepository = idMapRepository;
        this.spotifyApiClient = spotifyApiClient;
        this.spotifyAuthService = spotifyAuthService;
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

        for (LastFmTrack candidate : candidates) {
            if (processed >= processingBudget) {
                break;
            }
            processed++;

            String key = SimilarityKeys.normalize(candidate.artist(), candidate.name());

            Optional<IdMapEntry> cached = idMapRepository.findFresh(key);
            MappedTrack mappedTrack;
            if (cached.isPresent()) {
                IdMapEntry entry = cached.get();
                double confidence = entry.confidence() != null ? entry.confidence() : 1.0;
                mappedTrack = new MappedTrack(candidate, entry.spotifyId(), confidence, true, null);
            } else {
                SearchResult searchResult = performSearch(currentAuth, candidate);
                if (searchResult != null) {
                    currentAuth = searchResult.userAuth();
                    if (searchResult.track() != null) {
                        double confidence = candidate.matchScore() > 0 ? candidate.matchScore() : 1.0;
                        idMapRepository.upsert(key, searchResult.track().id(), confidence);
                        mappedTrack = new MappedTrack(
                            candidate,
                            searchResult.track().id(),
                            confidence,
                            false,
                            searchResult.track().imageUrl()
                        );
                    } else {
                        mappedTrack = new MappedTrack(candidate, null, 0.0, false, null);
                    }
                } else {
                    mappedTrack = new MappedTrack(candidate, null, 0.0, false, null);
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
        String spotifyImageUrl
    ) {}

    private record SearchResult(SeedTrack track, UserAuth userAuth) {}
}

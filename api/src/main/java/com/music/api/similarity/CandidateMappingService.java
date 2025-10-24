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

    public List<MappedTrack> mapCandidates(UserAuth userAuth, List<LastFmTrack> candidates) {
        List<MappedTrack> mapped = new ArrayList<>(candidates.size());
        UserAuth currentAuth = userAuth;

        for (LastFmTrack candidate : candidates) {
            String key = SimilarityKeys.normalize(candidate.artist(), candidate.name());

            Optional<IdMapEntry> cached = idMapRepository.findFresh(key);
            if (cached.isPresent()) {
                IdMapEntry entry = cached.get();
                mapped.add(new MappedTrack(candidate, entry.spotifyId(), entry.confidence(), true));
                continue;
            }

            SearchResult searchResult = performSearch(currentAuth, candidate);
            if (searchResult != null) {
                currentAuth = searchResult.userAuth();
                if (searchResult.track() != null) {
                    double confidence = candidate.matchScore() > 0 ? candidate.matchScore() : 1.0;
                    idMapRepository.upsert(key, searchResult.track().id(), confidence);
                    mapped.add(new MappedTrack(candidate, searchResult.track().id(), confidence, false));
                } else {
                    mapped.add(new MappedTrack(candidate, null, 0.0, false));
                }
            } else {
                mapped.add(new MappedTrack(candidate, null, 0.0, false));
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
        boolean cached
    ) {}

    private record SearchResult(SeedTrack track, UserAuth userAuth) {}
}

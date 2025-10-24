package com.music.api.web;

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
import com.music.api.seeds.SeedTrackView;
import com.music.api.similarity.CandidateMappingService;
import com.music.api.similarity.CandidateMappingService.MappedTrack;
import com.music.api.similarity.SimilarityService;
import com.music.api.similarity.SimilarityService.SimilarityResult;
import com.music.api.spotify.SpotifyApiClient;
import com.music.api.spotify.SpotifyApiClient.SeedTrack;

@RestController
public class SimilarityController {

    private final UserAuthRepository userAuthRepository;
    private final SpotifyAuthService spotifyAuthService;
    private final SpotifyApiClient spotifyApiClient;
    private final SimilarityService similarityService;
    private final CandidateMappingService candidateMappingService;

    public SimilarityController(
        UserAuthRepository userAuthRepository,
        SpotifyAuthService spotifyAuthService,
        SpotifyApiClient spotifyApiClient,
        SimilarityService similarityService,
        CandidateMappingService candidateMappingService
    ) {
        this.userAuthRepository = userAuthRepository;
        this.spotifyAuthService = spotifyAuthService;
        this.spotifyApiClient = spotifyApiClient;
        this.similarityService = similarityService;
        this.candidateMappingService = candidateMappingService;
    }

    @GetMapping("/similarity/candidates")
    public ResponseEntity<SimilarityResponse> candidates(
        @RequestParam("userId") UUID userId,
        @RequestParam("trackId") String trackId
    ) {
        UserAuth userAuth = userAuthRepository.findByUserId(userId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User authorization not found"));

        String normalizedTrackId = normalizeTrackId(trackId);

        SeedTrack seedTrack = fetchTrackWithRefresh(userAuth, normalizedTrackId);

        SimilarityResult result = similarityService.getSimilarTracks(seedTrack.artist(), seedTrack.name());
        List<MappedTrack> mapped = candidateMappingService.mapCandidates(userAuth, result.tracks());
        List<CandidateTrackView> items = mapped.stream()
            .map(CandidateTrackView::fromMappedTrack)
            .toList();

        SimilarityResponse response = new SimilarityResponse(
            SeedTrackView.fromSeedTrack(seedTrack),
            result.strategy(),
            items
        );
        return ResponseEntity.ok(response);
    }

    private SeedTrack fetchTrackWithRefresh(UserAuth userAuth, String trackId) {
        try {
            return spotifyApiClient.getTrack(userAuth.accessToken(), trackId);
        } catch (WebClientResponseException ex) {
            if (ex.getStatusCode().value() == 401) {
                UserAuth refreshed = spotifyAuthService.refreshAccessToken(userAuth);
                return spotifyApiClient.getTrack(refreshed.accessToken(), trackId);
            }
            if (ex.getStatusCode().value() == 404) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Seed track not found on Spotify");
            }
            throw ex;
        }
    }

    private String normalizeTrackId(String raw) {
        if (raw == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "trackId is required");
        }
        if (raw.startsWith("spotify:track:")) {
            return raw.substring("spotify:track:".length());
        }
        if (raw.startsWith("https://open.spotify.com/track/")) {
            String trimmed = raw.substring("https://open.spotify.com/track/".length());
            int queryIndex = trimmed.indexOf('?');
            return queryIndex >= 0 ? trimmed.substring(0, queryIndex) : trimmed;
        }
        return raw;
    }

    public record SimilarityResponse(
        SeedTrackView seed,
        String strategy,
        List<CandidateTrackView> tracks
    ) {}

    public record CandidateTrackView(
        String name,
        String artist,
        double matchScore,
        String spotifyId,
        double confidence,
        boolean cached,
        String lastFmUrl,
        String imageUrl
    ) {
        static CandidateTrackView fromMappedTrack(MappedTrack mapped) {
            return new CandidateTrackView(
                mapped.source().name(),
                mapped.source().artist(),
                mapped.source().matchScore(),
                mapped.spotifyId(),
                mapped.confidence(),
                mapped.cached(),
                mapped.source().url(),
                mapped.source().imageUrl()
            );
        }
    }
}

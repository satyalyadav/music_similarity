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
import com.music.api.similarity.RankingService;
import com.music.api.similarity.RankingService.RankedTrack;
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
    private final RankingService rankingService;

    public SimilarityController(
        UserAuthRepository userAuthRepository,
        SpotifyAuthService spotifyAuthService,
        SpotifyApiClient spotifyApiClient,
        SimilarityService similarityService,
        CandidateMappingService candidateMappingService,
        RankingService rankingService
    ) {
        this.userAuthRepository = userAuthRepository;
        this.spotifyAuthService = spotifyAuthService;
        this.spotifyApiClient = spotifyApiClient;
        this.similarityService = similarityService;
        this.candidateMappingService = candidateMappingService;
        this.rankingService = rankingService;
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
        List<RankedTrack> ranked = rankingService.rank(userAuth, seedTrack.artist(), mapped);
        List<CandidateTrackView> items = ranked.stream()
            .map(CandidateTrackView::fromRankedTrack)
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
        String spotifyId,
        double score,
        double lastFmMatch,
        double tagOverlap,
        double confidence,
        Integer popularity,
        boolean cached,
        String lastFmUrl,
        String imageUrl
    ) {
        static CandidateTrackView fromRankedTrack(RankedTrack ranked) {
            return new CandidateTrackView(
                ranked.name(),
                ranked.artist(),
                ranked.spotifyId(),
                ranked.score(),
                ranked.rawMatch(),
                ranked.tagOverlap(),
                ranked.confidence(),
                ranked.popularity(),
                ranked.cached(),
                ranked.lastFmUrl(),
                ranked.imageUrl()
            );
        }
    }
}

package com.music.api.recommendation;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
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
import com.music.api.spotify.TrackIdNormalizer;

@Service
public class RecommendationService {

    private static final int DEFAULT_LIMIT = 20;
    private static final int MAX_LIMIT = 50;

    private final UserAuthRepository userAuthRepository;
    private final SpotifyAuthService spotifyAuthService;
    private final SpotifyApiClient spotifyApiClient;
    private final SimilarityService similarityService;
    private final CandidateMappingService candidateMappingService;
    private final RankingService rankingService;

    public RecommendationService(
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

    public RecommendationResult getRecommendations(UUID userId, String seedTrackId, Integer requestedLimit) {
        int limit = determineLimit(requestedLimit);
        UserAuth userAuth = userAuthRepository.findByUserId(userId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User authorization not found"));

        String normalizedTrackId = normalize(seedTrackId);
        SeedTrack seedTrack = fetchTrackWithRefresh(userAuth, normalizedTrackId);

        SimilarityResult result = similarityService.getSimilarTracks(seedTrack.artist(), seedTrack.name());
        List<MappedTrack> mapped = candidateMappingService.mapCandidates(userAuth, result.tracks());
        List<RankedTrack> ranked = rankingService.rank(userAuth, seedTrack.artist(), mapped).stream()
            .filter(track -> track.spotifyId() != null)
            .limit(limit)
            .collect(Collectors.toList());

        return new RecommendationResult(SeedTrackView.fromSeedTrack(seedTrack), result.strategy(), ranked);
    }

    private int determineLimit(Integer requestedLimit) {
        if (requestedLimit == null || requestedLimit <= 0) {
            return DEFAULT_LIMIT;
        }
        return Math.min(requestedLimit, MAX_LIMIT);
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

    private String normalize(String rawTrackId) {
        try {
            return TrackIdNormalizer.normalize(rawTrackId);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage());
        }
    }

    public record RecommendationResult(
        SeedTrackView seed,
        String strategy,
        List<RankedTrack> tracks
    ) {}
}

package com.music.api.web;

import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.music.api.recommendation.RecommendationService;
import com.music.api.recommendation.RecommendationService.RecommendationResult;
import com.music.api.seeds.SeedTrackView;
import com.music.api.similarity.RankingService.RankedTrack;

@RestController
public class SimilarityController {

    private final RecommendationService recommendationService;

    public SimilarityController(RecommendationService recommendationService) {
        this.recommendationService = recommendationService;
    }

    @GetMapping("/similarity/candidates")
    public ResponseEntity<SimilarityResponse> candidates(
        @RequestParam("userId") UUID userId,
        @RequestParam("trackId") String trackId,
        @RequestParam(name = "limit", required = false) Integer limit
    ) {
        RecommendationResult result = recommendationService.getRecommendations(userId, trackId, limit);
        List<CandidateTrackView> items = result.tracks().stream()
            .map(CandidateTrackView::fromRankedTrack)
            .toList();

        SimilarityResponse response = new SimilarityResponse(
            result.seed(),
            result.strategy(),
            items
        );
        return ResponseEntity.ok(response);
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

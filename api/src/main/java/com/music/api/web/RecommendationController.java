package com.music.api.web;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.music.api.feedback.FeedbackRecord;
import com.music.api.feedback.FeedbackService;
import com.music.api.feedback.FeedbackService.FeedbackVote;
import com.music.api.playlists.PlaylistService;
import com.music.api.playlists.PlaylistService.PlaylistResult;
import com.music.api.recommendation.RecommendationService;
import com.music.api.recommendation.RecommendationService.RecommendationResult;
import com.music.api.web.dto.CreatePlaylistRequest;
import com.music.api.web.dto.CreatePlaylistResponse;
import com.music.api.web.dto.FeedbackRequest;
import com.music.api.web.dto.FeedbackResponse;
import com.music.api.web.dto.RecommendationResponse;
import com.music.api.web.dto.RecommendationTrackView;

@RestController
public class RecommendationController {

    private final RecommendationService recommendationService;
    private final PlaylistService playlistService;
    private final FeedbackService feedbackService;

    public RecommendationController(
        RecommendationService recommendationService,
        PlaylistService playlistService,
        FeedbackService feedbackService
    ) {
        this.recommendationService = recommendationService;
        this.playlistService = playlistService;
        this.feedbackService = feedbackService;
    }

    @GetMapping("/recommend")
    public ResponseEntity<RecommendationResponse> recommend(
        @RequestParam("userId") UUID userId,
        @RequestParam("seed") String seed,
        @RequestParam(name = "limit", defaultValue = "20") int limit
    ) {
        RecommendationResult result = recommendationService.getRecommendations(userId, seed, limit);
        List<RecommendationTrackView> items = result.tracks().stream()
            .map(RecommendationTrackView::fromRankedTrack)
            .toList();
        return ResponseEntity.ok(new RecommendationResponse(result.seed(), result.strategy(), items));
    }

    @PostMapping("/playlist")
    public ResponseEntity<CreatePlaylistResponse> createPlaylist(@RequestBody CreatePlaylistRequest request) {
        if (request == null || request.userId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "userId is required");
        }
        PlaylistResult result = playlistService.createPlaylist(
            request.userId(),
            request.name(),
            request.trackIds(),
            Boolean.TRUE.equals(request.publicPlaylist())
        );
        CreatePlaylistResponse response = new CreatePlaylistResponse(result.playlistId(), result.spotifyUrl(), result.trackCount());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/feedback")
    public ResponseEntity<FeedbackResponse> feedback(@RequestBody FeedbackRequest request) {
        if (request == null || request.userId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "userId is required");
        }
        FeedbackVote vote = FeedbackVote.fromLabel(request.vote());
        FeedbackRecord saved = feedbackService.saveFeedback(request.userId(), request.trackId(), vote);
        FeedbackResponse response = new FeedbackResponse(saved.userId(), saved.spotifyId(), vote == FeedbackVote.UP ? "up" : "down");
        return ResponseEntity.ok(response);
    }
}

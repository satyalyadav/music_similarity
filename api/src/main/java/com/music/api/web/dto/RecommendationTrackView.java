package com.music.api.web.dto;

import com.music.api.similarity.RankingService.RankedTrack;

public record RecommendationTrackView(
    String name,
    String artist,
    String spotifyId,
    String spotifyUrl,
    double score,
    String imageUrl
) {
    public static RecommendationTrackView fromRankedTrack(RankedTrack rankedTrack) {
        return new RecommendationTrackView(
            rankedTrack.name(),
            rankedTrack.artist(),
            rankedTrack.spotifyId(),
            rankedTrack.spotifyId() != null ? "https://open.spotify.com/track/" + rankedTrack.spotifyId() : null,
            rankedTrack.score(),
            rankedTrack.imageUrl()
        );
    }
}

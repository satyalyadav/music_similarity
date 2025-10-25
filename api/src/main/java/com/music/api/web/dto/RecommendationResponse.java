package com.music.api.web.dto;

import java.util.List;

import com.music.api.seeds.SeedTrackView;

public record RecommendationResponse(
    SeedTrackView seed,
    String strategy,
    List<RecommendationTrackView> items
) {}

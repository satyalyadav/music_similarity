package com.music.api.web.dto;

import java.util.UUID;

public record FeedbackResponse(
    UUID userId,
    String trackId,
    String vote
) {}

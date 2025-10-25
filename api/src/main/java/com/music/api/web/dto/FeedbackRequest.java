package com.music.api.web.dto;

import java.util.UUID;

public record FeedbackRequest(
    UUID userId,
    String trackId,
    String vote
) {}

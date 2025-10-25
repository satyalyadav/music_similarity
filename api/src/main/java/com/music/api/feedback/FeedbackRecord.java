package com.music.api.feedback;

import java.time.Instant;
import java.util.UUID;

public record FeedbackRecord(
    UUID userId,
    String spotifyId,
    short label,
    Instant createdAt
) {}

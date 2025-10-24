package com.music.api.auth;

import java.time.Instant;
import java.util.UUID;

public record UserAuth(
    UUID userId,
    String spotifyId,
    String accessToken,
    String refreshToken,
    String scopes,
    Instant createdAt,
    Instant updatedAt
) {
    public UserAuth withTokens(String accessToken, String refreshToken, String scopes, Instant updatedAt) {
        return new UserAuth(userId, spotifyId, accessToken, refreshToken, scopes, createdAt, updatedAt);
    }
}

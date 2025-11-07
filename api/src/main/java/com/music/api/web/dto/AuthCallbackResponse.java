package com.music.api.web.dto;

import java.util.UUID;

public record AuthCallbackResponse(
    UUID userId,
    String spotifyId,
    String displayName,
    String product,
    String imageUrl,
    String redirectUri
) {}

package com.music.api.web.dto;

import java.util.List;

public record PlaybackTokenResponse(
    boolean eligible,
    Boolean premium,
    String product,
    List<String> missingScopes,
    String accessToken
) {}

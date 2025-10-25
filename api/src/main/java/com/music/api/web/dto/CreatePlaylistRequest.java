package com.music.api.web.dto;

import java.util.List;
import java.util.UUID;

public record CreatePlaylistRequest(
    UUID userId,
    String name,
    List<String> trackIds,
    Boolean publicPlaylist
) {}

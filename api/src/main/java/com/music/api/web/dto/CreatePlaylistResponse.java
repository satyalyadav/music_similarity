package com.music.api.web.dto;

public record CreatePlaylistResponse(
    String playlistId,
    String spotifyUrl,
    int tracksAdded
) {}

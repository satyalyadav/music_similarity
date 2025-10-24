package com.music.api.spotify;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record SpotifyUserProfile(
    String id,
    @JsonProperty("display_name") String displayName
) {}

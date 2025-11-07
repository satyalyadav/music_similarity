package com.music.api.spotify;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record SpotifyUserProfile(
    String id,
    @JsonProperty("display_name") String displayName,
    String product,
    List<SpotifyImage> images
) {
    public String imageUrl() {
        if (images == null || images.isEmpty()) {
            return null;
        }
        // Return the largest image (first in the list, as Spotify returns them sorted)
        return images.get(0).url();
    }
}

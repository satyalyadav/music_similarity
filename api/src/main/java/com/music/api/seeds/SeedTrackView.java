package com.music.api.seeds;

import com.music.api.spotify.SpotifyApiClient.SeedTrack;

public record SeedTrackView(
    String id,
    String name,
    String artist,
    String album,
    String imageUrl,
    String spotifyUrl
) {
    public static SeedTrackView fromSeedTrack(SeedTrack track) {
        return new SeedTrackView(
            track.id(),
            track.name(),
            track.artist(),
            track.album(),
            track.imageUrl(),
            track.spotifyUrl()
        );
    }
}

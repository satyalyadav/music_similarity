package com.music.api.spotify;

import org.springframework.util.StringUtils;

/**
 * Utility helpers for normalizing Spotify track identifiers provided in various formats.
 */
public final class TrackIdNormalizer {

    private static final String TRACK_URI_PREFIX = "spotify:track:";
    private static final String TRACK_URL_PREFIX = "https://open.spotify.com/track/";

    private TrackIdNormalizer() {
    }

    /**
     * Normalize a Spotify track ID, accepting bare IDs, spotify:track URIs, or open.spotify.com URLs.
     * @param raw user provided identifier
     * @return bare Spotify track ID
     */
    public static String normalize(String raw) {
        if (!StringUtils.hasText(raw)) {
            throw new IllegalArgumentException("Track identifier is required");
        }
        String trimmed = raw.trim();
        if (trimmed.startsWith(TRACK_URI_PREFIX)) {
            return trimmed.substring(TRACK_URI_PREFIX.length());
        }
        if (trimmed.startsWith(TRACK_URL_PREFIX)) {
            String remainder = trimmed.substring(TRACK_URL_PREFIX.length());
            int queryIndex = remainder.indexOf('?');
            return queryIndex >= 0 ? remainder.substring(0, queryIndex) : remainder;
        }
        return trimmed;
    }

    /**
     * Convert a bare Spotify track ID to the spotify:track URI format expected by playlist APIs.
     */
    public static String toSpotifyUri(String trackId) {
        if (!StringUtils.hasText(trackId)) {
            throw new IllegalArgumentException("Track identifier is required");
        }
        String normalized = normalize(trackId);
        return TRACK_URI_PREFIX + normalized;
    }
}

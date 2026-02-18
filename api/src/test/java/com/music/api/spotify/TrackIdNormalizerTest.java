package com.music.api.spotify;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class TrackIdNormalizerTest {

    @Test
    void normalizesTrackUrlWithQueryFragmentAndTrailingSlash() {
        String raw = "https://open.spotify.com/track/1A2B3C4D5E6F7G8H9I0J1K/?si=abc123#section";

        assertThat(TrackIdNormalizer.normalize(raw)).isEqualTo("1A2B3C4D5E6F7G8H9I0J1K");
    }

    @Test
    void normalizesTrackUrlWithFragment() {
        String raw = "https://open.spotify.com/track/1A2B3C4D5E6F7G8H9I0J1K#fragment";

        assertThat(TrackIdNormalizer.normalize(raw)).isEqualTo("1A2B3C4D5E6F7G8H9I0J1K");
    }

    @Test
    void rejectsBlankTrackIdentifier() {
        assertThatThrownBy(() -> TrackIdNormalizer.normalize("  "))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Track identifier is required");
    }
}

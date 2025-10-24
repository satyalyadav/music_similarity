package com.music.api.similarity;

import java.util.Locale;
import java.util.Objects;

final class SimilarityKeys {

    private SimilarityKeys() {
    }

    static String normalize(String artist, String track) {
        return (Objects.toString(artist, "").trim().toLowerCase(Locale.ROOT) + "|" +
            Objects.toString(track, "").trim().toLowerCase(Locale.ROOT));
    }
}

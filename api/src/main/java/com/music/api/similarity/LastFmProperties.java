package com.music.api.similarity;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "lastfm")
public class LastFmProperties {

    /**
     * API key issued by Last.fm.
     */
    private String apiKey;

    /**
     * Default country code for geo.getTopTracks fallback.
     */
    private String defaultCountry = "US";

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getDefaultCountry() {
        return defaultCountry;
    }

    public void setDefaultCountry(String defaultCountry) {
        this.defaultCountry = defaultCountry;
    }
}

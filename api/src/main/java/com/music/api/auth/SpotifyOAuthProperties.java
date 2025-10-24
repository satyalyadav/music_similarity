package com.music.api.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "spotify")
public class SpotifyOAuthProperties {

    /**
     * Spotify application client ID.
     */
    private String clientId;

    /**
     * Spotify application client secret.
     */
    private String clientSecret;

    /**
     * Fully qualified redirect URI registered with Spotify.
     */
    private String redirectUri;

    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public String getClientSecret() {
        return clientSecret;
    }

    public void setClientSecret(String clientSecret) {
        this.clientSecret = clientSecret;
    }

    public String getRedirectUri() {
        return redirectUri;
    }

    public void setRedirectUri(String redirectUri) {
        this.redirectUri = redirectUri;
    }
}

package com.music.api.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class WebClientConfig {

    @Bean
    public WebClient spotifyAccountsClient(
        WebClient.Builder builder,
        @Value("${spotify.accounts.base-url:https://accounts.spotify.com}") String baseUrl
    ) {
        return builder
            .baseUrl(baseUrl)
            .exchangeStrategies(defaultStrategies())
            .build();
    }

    @Bean
    public WebClient spotifyApiWebClient(
        WebClient.Builder builder,
        @Value("${spotify.api.base-url:https://api.spotify.com/v1}") String baseUrl
    ) {
        return builder
            .baseUrl(baseUrl)
            .exchangeStrategies(defaultStrategies())
            .build();
    }

    @Bean
    public WebClient lastFmWebClient(
        WebClient.Builder builder,
        @Value("${lastfm.base-url:https://ws.audioscrobbler.com/2.0}") String baseUrl
    ) {
        return builder
            .baseUrl(baseUrl)
            .exchangeStrategies(defaultStrategies())
            .build();
    }

    @Bean
    public WebClient musicBrainzWebClient(
        WebClient.Builder builder,
        @Value("${musicbrainz.base-url:https://musicbrainz.org/ws/2}") String baseUrl
    ) {
        return builder
            .baseUrl(baseUrl)
            .defaultHeader("User-Agent", "music-similarity-app/1.0 (support@example.com)")
            .exchangeStrategies(defaultStrategies())
            .build();
    }

    @Bean
    public WebClient.Builder webClientBuilder() {
        return WebClient.builder();
    }

    private ExchangeStrategies defaultStrategies() {
        return ExchangeStrategies.builder()
            .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(2 * 1024 * 1024))
            .build();
    }
}

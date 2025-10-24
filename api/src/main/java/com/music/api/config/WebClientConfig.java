package com.music.api.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class WebClientConfig {

    @Bean
    public WebClient spotifyAccountsClient(WebClient.Builder builder) {
        return builder
            .baseUrl("https://accounts.spotify.com")
            .exchangeStrategies(defaultStrategies())
            .build();
    }

    @Bean
    public WebClient spotifyApiWebClient(WebClient.Builder builder) {
        return builder
            .baseUrl("https://api.spotify.com/v1")
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

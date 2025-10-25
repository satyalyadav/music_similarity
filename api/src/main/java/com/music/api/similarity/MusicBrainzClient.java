package com.music.api.similarity;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@Component
public class MusicBrainzClient {

    private static final Logger log = LoggerFactory.getLogger(MusicBrainzClient.class);

    private final WebClient musicBrainzWebClient;

    public MusicBrainzClient(@Qualifier("musicBrainzWebClient") WebClient musicBrainzWebClient) {
        this.musicBrainzWebClient = musicBrainzWebClient;
    }

    public List<String> fetchArtistTags(String artistName) {
        if (artistName == null || artistName.isBlank()) {
            return List.of();
        }

        try {
            ArtistSearchResponse response = musicBrainzWebClient.get()
                .uri(uriBuilder -> uriBuilder
                    .path("/artist")
                    .queryParam("query", "artist:\"" + artistName + "\"")
                    .queryParam("fmt", "json")
                    .queryParam("limit", 1)
                    .queryParam("inc", "tags")
                    .build())
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .bodyToMono(ArtistSearchResponse.class)
                .block();

            if (response == null || response.artists() == null || response.artists().isEmpty()) {
                return List.of();
            }

            Artist first = response.artists().get(0);
            if (first.tags() == null) {
                return List.of();
            }

            return first.tags().stream()
                .sorted((a, b) -> Integer.compare(Optional.ofNullable(b.count()).orElse(0), Optional.ofNullable(a.count()).orElse(0)))
                .map(Tag::name)
                .filter(tag -> tag != null && !tag.isBlank())
                .limit(20)
                .collect(Collectors.toList());
        } catch (WebClientResponseException ex) {
            log.warn("MusicBrainz tag fetch failed for {}: {} {}", artistName, ex.getStatusCode(), ex.getResponseBodyAsString());
            return Collections.emptyList();
        } catch (Exception ex) {
            log.warn("MusicBrainz tag fetch error for {}: {}", artistName, ex.getMessage());
            return Collections.emptyList();
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ArtistSearchResponse(List<Artist> artists) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record Artist(
        String id,
        String name,
        List<Tag> tags
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record Tag(
        String name,
        Integer count
    ) {}
}

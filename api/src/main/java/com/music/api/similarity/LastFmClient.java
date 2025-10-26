package com.music.api.similarity;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.util.UriBuilder;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.retry.Retry;

@Component
public class LastFmClient {

    private static final Logger log = LoggerFactory.getLogger(LastFmClient.class);

    private final WebClient lastFmWebClient;
    private final LastFmProperties properties;
    private final RateLimiter lastFmRateLimiter;
    private final Retry metadataRetry;
    private final CircuitBreaker lastFmCircuitBreaker;

    public LastFmClient(
        @Qualifier("lastFmWebClient") WebClient lastFmWebClient,
        LastFmProperties properties,
        @Qualifier("lastFmRateLimiter") RateLimiter lastFmRateLimiter,
        @Qualifier("metadataRetry") Retry metadataRetry,
        @Qualifier("lastFmCircuitBreaker") CircuitBreaker lastFmCircuitBreaker
    ) {
        this.lastFmWebClient = lastFmWebClient;
        this.properties = properties;
        this.lastFmRateLimiter = lastFmRateLimiter;
        this.metadataRetry = metadataRetry;
        this.lastFmCircuitBreaker = lastFmCircuitBreaker;
    }

    public List<LastFmTrack> getSimilarTracks(String artist, String track, int limit) {
        return execute("track.getSimilar", builder -> builder
            .queryParam("artist", artist)
            .queryParam("track", track)
            .queryParam("limit", limit)
        , SimilarTracksResponse.class)
        .map(response -> Optional.ofNullable(response.similarTracks())
            .map(SimilarTracks::tracks)
            .orElse(List.of()))
        .map(this::mapTrackResults)
        .orElse(List.of());
    }

    public List<LastFmArtist> getSimilarArtists(String artist, int limit) {
        return execute("artist.getSimilar", builder -> builder
            .queryParam("artist", artist)
            .queryParam("limit", limit)
        , SimilarArtistsResponse.class)
        .map(response -> Optional.ofNullable(response.similarArtists())
            .map(SimilarArtists::artists)
            .orElse(List.of()))
        .map(this::mapArtistResults)
        .orElse(List.of());
    }

    public List<LastFmTrack> getArtistTopTracks(String artist, int limit) {
        return execute("artist.getTopTracks", builder -> builder
            .queryParam("artist", artist)
            .queryParam("limit", limit)
        , TopTracksResponse.class)
        .map(response -> Optional.ofNullable(response.topTracks())
            .map(TopTracks::tracks)
            .orElse(List.of()))
        .map(this::mapTrackResults)
        .orElse(List.of());
    }

    public List<LastFmTrack> getGeoTopTracks(String country, int limit) {
        return execute("geo.getTopTracks", builder -> builder
            .queryParam("country", country)
            .queryParam("limit", limit)
        , TopTracksResponse.class)
        .map(response -> Optional.ofNullable(response.topTracks())
            .map(TopTracks::tracks)
            .orElse(List.of()))
        .map(this::mapTrackResults)
        .orElse(List.of());
    }

    private <T> Optional<T> execute(String method, Function<UriBuilder, UriBuilder> customizer, Class<T> clazz) {
        if (properties.getApiKey() == null || properties.getApiKey().isBlank()) {
            log.warn("Last.fm API key is not configured; skipping {} call", method);
            return Optional.empty();
        }

        Supplier<T> supplier = () -> lastFmWebClient.get()
            .uri(builder -> {
                UriBuilder base = builder
                    .queryParam("method", method)
                    .queryParam("api_key", properties.getApiKey())
                    .queryParam("format", "json");
                return customizer.apply(base).build();
            })
            .accept(MediaType.APPLICATION_JSON)
            .retrieve()
            .bodyToMono(clazz)
            .block();
        try {
            Supplier<T> rateLimited = RateLimiter.decorateSupplier(lastFmRateLimiter, supplier);
            Supplier<T> circuitProtected = CircuitBreaker.decorateSupplier(lastFmCircuitBreaker, rateLimited);
            Supplier<T> retried = Retry.decorateSupplier(metadataRetry, circuitProtected);
            return Optional.ofNullable(retried.get());
        } catch (CallNotPermittedException ex) {
            log.warn("Last.fm circuit breaker open, skipping {} request", method);
            return Optional.empty();
        } catch (WebClientResponseException ex) {
            log.warn("Last.fm {} request failed: {} {}", method, ex.getStatusCode(), ex.getResponseBodyAsString());
            return Optional.empty();
        } catch (Exception ex) {
            log.warn("Last.fm {} request failed: {}", method, ex.getMessage());
            return Optional.empty();
        }
    }

    private List<LastFmTrack> mapTrackResults(List<TrackPayload> payloads) {
        return payloads.stream()
            .filter(Objects::nonNull)
            .map(track -> new LastFmTrack(
                track.name(),
                extractArtistName(track.artist()),
                parseMatch(track.match()),
                track.url(),
                chooseImage(track.image())
            ))
            .filter(track -> track.name() != null && !track.name().isBlank() && track.artist() != null && !track.artist().isBlank())
            .collect(Collectors.toList());
    }

    private List<LastFmArtist> mapArtistResults(List<ArtistPayload> payloads) {
        return payloads.stream()
            .filter(Objects::nonNull)
            .map(artist -> new LastFmArtist(
                artist.name(),
                parseMatch(artist.match()),
                artist.url()
            ))
            .filter(artist -> artist.name() != null && !artist.name().isBlank())
            .collect(Collectors.toList());
    }

    private double parseMatch(String match) {
        if (match == null) {
            return 0.0;
        }
        try {
            return Double.parseDouble(match);
        } catch (NumberFormatException ex) {
            return 0.0;
        }
    }

    private String chooseImage(List<ImagePayload> images) {
        if (images == null || images.isEmpty()) {
            return null;
        }
        return images.stream()
            .sorted(Comparator.comparing(ImagePayload::size, Comparator.nullsLast(Comparator.naturalOrder())))
            .map(ImagePayload::url)
            .filter(url -> url != null && !url.isBlank())
            .findFirst()
            .orElse(null);
    }

    private String extractArtistName(JsonNode artistNode) {
        if (artistNode == null || artistNode.isNull()) {
            return null;
        }
        if (artistNode.isObject()) {
            JsonNode nameNode = artistNode.get("name");
            if (nameNode != null && !nameNode.isNull()) {
                return nameNode.asText();
            }
        }
        if (artistNode.isTextual()) {
            return artistNode.asText();
        }
        return null;
    }

    public record LastFmTrack(
        String name,
        String artist,
        double matchScore,
        String url,
        String imageUrl
    ) {}

    public record LastFmArtist(
        String name,
        double matchScore,
        String url
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record SimilarTracksResponse(@JsonProperty("similartracks") SimilarTracks similarTracks) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record SimilarTracks(@JsonProperty("track") List<TrackPayload> tracks) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record SimilarArtistsResponse(@JsonProperty("similarartists") SimilarArtists similarArtists) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record SimilarArtists(@JsonProperty("artist") List<ArtistPayload> artists) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record TopTracksResponse(@JsonProperty("toptracks") TopTracks topTracks) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record TopTracks(@JsonProperty("track") List<TrackPayload> tracks) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record TrackPayload(
        String name,
        String url,
        String match,
        @JsonProperty("artist") JsonNode artist,
        @JsonProperty("image") List<ImagePayload> image
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ArtistPayload(
        String name,
        String url,
        String match
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ImagePayload(
        @JsonProperty("#text") String url,
        String size
    ) {}
}

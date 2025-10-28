package com.music.api.spotify;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import io.github.resilience4j.reactor.retry.RetryOperator;
import reactor.core.publisher.Mono;

@Component
public class SpotifyApiClient {

    private static final Logger log = LoggerFactory.getLogger(SpotifyApiClient.class);

    private final WebClient spotifyWebClient;
    private final io.github.resilience4j.retry.Retry outboundHttpRetry;

    public SpotifyApiClient(
        @Qualifier("spotifyApiWebClient") WebClient spotifyWebClient,
        @Qualifier("outboundHttpRetry") io.github.resilience4j.retry.Retry outboundHttpRetry
    ) {
        this.spotifyWebClient = spotifyWebClient;
        this.outboundHttpRetry = outboundHttpRetry;
    }

    public SpotifyUserProfile getCurrentUserProfile(String accessToken) {
        return execute(spotifyWebClient.get()
            .uri("/me")
            .accept(MediaType.APPLICATION_JSON)
            .headers(headers -> headers.setBearerAuth(accessToken))
            .retrieve()
            .bodyToMono(SpotifyUserProfile.class));
    }

    public List<SeedTrack> getTopTracks(String accessToken, int limit) {
        try {
            TopTracksResponse response = execute(spotifyWebClient.get()
                .uri(uriBuilder -> uriBuilder
                    .path("/me/top/tracks")
                    .queryParam("limit", Math.min(limit, 50))
                    .build())
                .accept(MediaType.APPLICATION_JSON)
                .headers(headers -> headers.setBearerAuth(accessToken))
                .retrieve()
                .bodyToMono(TopTracksResponse.class));

            return extractTracks(response);
        } catch (WebClientResponseException ex) {
            log.debug("Spotify top tracks request failed: {}", ex.getStatusCode());
            throw ex;
        }
    }

    public SeedTrack getTrack(String accessToken, String trackId) {
        try {
            SpotifyTrack track = execute(spotifyWebClient.get()
                .uri(uriBuilder -> uriBuilder
                    .path("/tracks/{id}")
                    .build(trackId))
                .accept(MediaType.APPLICATION_JSON)
                .headers(headers -> headers.setBearerAuth(accessToken))
                .retrieve()
                .bodyToMono(SpotifyTrack.class));
            return mapTrack(track);
        } catch (WebClientResponseException ex) {
            log.debug("Spotify track lookup failed for {}: {}", trackId, ex.getStatusCode());
            throw ex;
        }
    }

    public CreatedPlaylist createPlaylist(String accessToken, String spotifyUserId, String name, boolean isPublic) {
        try {
            return execute(spotifyWebClient.post()
                .uri(uriBuilder -> uriBuilder
                    .path("/users/{userId}/playlists")
                    .build(spotifyUserId))
                .contentType(MediaType.APPLICATION_JSON)
                .headers(headers -> headers.setBearerAuth(accessToken))
                .bodyValue(new CreatePlaylistPayload(name, null, isPublic))
                .retrieve()
                .bodyToMono(CreatedPlaylist.class));
        } catch (WebClientResponseException ex) {
            log.debug("Spotify create playlist failed: {}", ex.getStatusCode());
            throw ex;
        }
    }

    public void addTracksToPlaylist(String accessToken, String playlistId, List<String> uris) {
        try {
            executeVoid(spotifyWebClient.post()
                .uri(uriBuilder -> uriBuilder
                    .path("/playlists/{playlistId}/tracks")
                    .build(playlistId))
                .contentType(MediaType.APPLICATION_JSON)
                .headers(headers -> headers.setBearerAuth(accessToken))
                .bodyValue(new AddTracksPayload(uris))
                .retrieve()
                .bodyToMono(SnapshotResponse.class));
        } catch (WebClientResponseException ex) {
            log.debug("Spotify add tracks failed for playlist {}: {}", playlistId, ex.getStatusCode());
            throw ex;
        }
    }

    public SeedTrack searchTrack(String accessToken, String trackName, String artistName) {
        String query = String.format("track:\"%s\" artist:\"%s\"", trackName, artistName);
        try {
            SearchResponse response = execute(spotifyWebClient.get()
                .uri(uriBuilder -> uriBuilder
                    .path("/search")
                    .queryParam("q", query)
                    .queryParam("type", "track")
                    .queryParam("limit", 1)
                    .build())
                .accept(MediaType.APPLICATION_JSON)
                .headers(headers -> headers.setBearerAuth(accessToken))
                .retrieve()
                .bodyToMono(SearchResponse.class));

            if (response == null || response.tracks() == null || response.tracks().items() == null || response.tracks().items().isEmpty()) {
                return null;
            }
            return mapTrack(response.tracks().items().get(0));
        } catch (WebClientResponseException ex) {
            log.debug("Spotify search failed for {} - {}: {}", trackName, artistName, ex.getStatusCode());
            throw ex;
        }
    }

    public List<SeedTrack> getRecentlyPlayed(String accessToken, int limit) {
        try {
            RecentlyPlayedResponse response = execute(spotifyWebClient.get()
                .uri(uriBuilder -> uriBuilder
                    .path("/me/player/recently-played")
                    .queryParam("limit", Math.min(limit, 50))
                    .build())
                .accept(MediaType.APPLICATION_JSON)
                .headers(headers -> headers.setBearerAuth(accessToken))
                .retrieve()
                .bodyToMono(RecentlyPlayedResponse.class));

            if (response == null) {
                return List.of();
            }

            return response.items().stream()
                .map(RecentlyPlayedItem::track)
                .map(this::mapTrack)
                .filter(track -> track != null)
                .collect(Collectors.toCollection(ArrayList::new));
        } catch (WebClientResponseException ex) {
            log.debug("Spotify recently played request failed: {}", ex.getStatusCode());
            throw ex;
        }
    }

    private List<SeedTrack> extractTracks(TopTracksResponse response) {
        if (response == null || response.items() == null) {
            return List.of();
        }
        return response.items().stream()
            .map(this::mapTrack)
            .filter(track -> track != null)
            .collect(Collectors.toCollection(ArrayList::new));
    }

    private SeedTrack mapTrack(SpotifyTrack track) {
        if (track == null) {
            return null;
        }
        String artist = track.artists() == null || track.artists().isEmpty()
            ? "Unknown Artist"
            : track.artists().get(0).name();
        String albumName = track.album() != null ? track.album().name() : null;
        String imageUrl = extractLargestImage(track.album() != null ? track.album().images() : null);
    String spotifyUrl = track.externalUrls() != null ? track.externalUrls().spotify() : null;
    String isrc = track.externalIds() != null ? track.externalIds().isrc() : null;
    return new SeedTrack(track.id(), track.name(), artist, albumName, imageUrl, spotifyUrl, track.popularity(), isrc);
    }

    private String extractLargestImage(List<SpotifyImage> images) {
        if (images == null || images.isEmpty()) {
            return null;
        }
        return images.stream()
            .sorted(Comparator.comparing(SpotifyImage::height, Comparator.nullsLast(Comparator.reverseOrder())))
            .map(SpotifyImage::url)
            .filter(url -> url != null && !url.isBlank())
            .findFirst()
            .orElse(null);
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TopTracksResponse(List<SpotifyTrack> items) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record RecentlyPlayedResponse(List<RecentlyPlayedItem> items) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record RecentlyPlayedItem(SpotifyTrack track) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SpotifyTrack(
        String id,
        String name,
        List<SpotifyArtist> artists,
        SpotifyAlbum album,
        @JsonProperty("external_urls") SpotifyExternalUrls externalUrls,
        @JsonProperty("external_ids") SpotifyExternalIds externalIds,
        Integer popularity
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SpotifyArtist(String name) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SpotifyAlbum(
        String name,
        List<SpotifyImage> images
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SpotifyImage(String url, Integer height, Integer width) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SpotifyExternalUrls(String spotify) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SpotifyExternalIds(String isrc) {}

    public record SeedTrack(
        String id,
        String name,
        String artist,
        String album,
        String imageUrl,
        String spotifyUrl,
        Integer popularity,
        String isrc
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SearchResponse(Tracks tracks) {}

    public record CreatePlaylistPayload(
        String name,
        String description,
        @JsonProperty("public") boolean isPublic
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CreatedPlaylist(
        String id,
        String name,
        @JsonProperty("external_urls") SpotifyExternalUrls externalUrls
    ) {}

    private record AddTracksPayload(List<String> uris) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SnapshotResponse(@JsonProperty("snapshot_id") String snapshotId) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Tracks(List<SpotifyTrack> items) {}

    private <T> T execute(Mono<T> mono) {
        return mono.transformDeferred(RetryOperator.of(outboundHttpRetry)).block();
    }

    private void executeVoid(Mono<?> mono) {
        mono.transformDeferred(RetryOperator.of(outboundHttpRetry)).then().block();
    }
}

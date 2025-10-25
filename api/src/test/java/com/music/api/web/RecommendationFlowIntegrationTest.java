package com.music.api.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import com.music.api.web.dto.CreatePlaylistRequest;
import com.music.api.web.dto.CreatePlaylistResponse;
import com.music.api.web.dto.FeedbackRequest;
import com.music.api.web.dto.FeedbackResponse;
import com.music.api.web.dto.RecommendationResponse;
import com.music.api.web.dto.RecommendationTrackView;

import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class RecommendationFlowIntegrationTest {

    private static final MockWebServer spotifyApiServer = startServer();
    private static final MockWebServer spotifyAccountsServer = startServer();
    private static final MockWebServer lastFmServer = startServer();
    private static final MockWebServer musicBrainzServer = startServer();

    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    private static MockWebServer startServer() {
        MockWebServer server = new MockWebServer();
        try {
            server.start();
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to start MockWebServer", ex);
        }
        return server;
    }

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);

        registry.add("spotify.client-id", () -> "client-id");
        registry.add("spotify.client-secret", () -> "client-secret");
        registry.add("spotify.redirect-uri", () -> "http://localhost/callback");
        registry.add("lastfm.api-key", () -> "test-lastfm-key");

        registry.add("spotify.api.base-url", () -> spotifyApiServer.url("/").toString());
        registry.add("spotify.accounts.base-url", () -> spotifyAccountsServer.url("/").toString());
        registry.add("lastfm.base-url", () -> lastFmServer.url("/").toString());
        registry.add("musicbrainz.base-url", () -> musicBrainzServer.url("/").toString());
    }

    @AfterAll
    static void shutdownServers() throws IOException {
        spotifyApiServer.shutdown();
        spotifyAccountsServer.shutdown();
        lastFmServer.shutdown();
        musicBrainzServer.shutdown();
    }

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    private static final UUID USER_ID = UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee");

    @BeforeEach
    void resetState() {
        jdbcTemplate.execute("TRUNCATE feedback, id_map, track_cache, artist_tags, user_auth RESTART IDENTITY CASCADE");
        insertUserAuth();
        insertArtistTags("Seed Artist");
        insertArtistTags("Rec Artist");
        insertTrackCache("rec-track-id");
    }

    @Test
    void recommendEndpointReturnsRankedTracks() {
        enqueueSpotifyTrackLookup();
        enqueueLastFmSimilar();
        enqueueSpotifySearch();

        ResponseEntity<RecommendationResponse> response = restTemplate.exchange(
            "/recommend?userId=" + USER_ID + "&seed=spotify:track:seed-track-id&limit=5",
            HttpMethod.GET,
            null,
            new ParameterizedTypeReference<>() {}
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        RecommendationResponse body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.items()).hasSize(1);

        RecommendationTrackView track = body.items().get(0);
        assertThat(track.spotifyId()).isEqualTo("rec-track-id");
        assertThat(track.score()).isGreaterThan(0);

        assertThat(body.seed().id()).isEqualTo("seed-track-id");
    }

    @Test
    void playlistEndpointCreatesPlaylistWithTracks() {
        enqueueSpotifyPlaylistCreate();
        enqueueSpotifyAddTracks();

        CreatePlaylistRequest request = new CreatePlaylistRequest(
            USER_ID,
            "Test Playlist",
            List.of("spotify:track:rec-track-id"),
            Boolean.TRUE
        );

        ResponseEntity<CreatePlaylistResponse> response = restTemplate.postForEntity(
            "/playlist",
            request,
            CreatePlaylistResponse.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        CreatePlaylistResponse body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.playlistId()).isEqualTo("playlist-123");
        assertThat(body.tracksAdded()).isEqualTo(1);
    }

    @Test
    void feedbackEndpointStoresVote() {
        FeedbackRequest request = new FeedbackRequest(
            USER_ID,
            "spotify:track:feedback-track",
            "up"
        );

        ResponseEntity<FeedbackResponse> response = restTemplate.postForEntity(
            "/feedback",
            request,
            FeedbackResponse.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        FeedbackResponse body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.trackId()).isEqualTo("feedback-track");

        Integer count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM feedback WHERE user_id = ? AND spotify_id = ?",
            Integer.class,
            USER_ID,
            "feedback-track"
        );
        assertThat(count).isEqualTo(1);
    }

    private void insertUserAuth() {
        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("userId", USER_ID)
            .addValue("spotifyId", "spotify-user-123")
            .addValue("accessToken", "access-token")
            .addValue("refreshToken", "refresh-token")
            .addValue("scopes", String.join(" ",
                "user-top-read",
                "user-read-recently-played",
                "playlist-modify-public",
                "playlist-modify-private"));

        namedParameterJdbcTemplate.update("""
            INSERT INTO user_auth (user_id, spotify_id, access_token, refresh_token, scopes, created_at, updated_at)
            VALUES (:userId, :spotifyId, :accessToken, :refreshToken, :scopes, NOW(), NOW())
            """, params);
    }

    private void insertTrackCache(String spotifyId) {
        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("spotifyId", spotifyId)
            .addValue("name", "Rec Track")
            .addValue("artist", "Rec Artist")
            .addValue("album", "Rec Album")
            .addValue("popularity", 75)
            .addValue("imageUrl", "https://images/rec");

        namedParameterJdbcTemplate.update("""
            INSERT INTO track_cache (spotify_id, name, artist, album, popularity, image_url, cached_at)
            VALUES (:spotifyId, :name, :artist, :album, :popularity, :imageUrl, NOW())
            """, params);
    }

    private void insertArtistTags(String artistName) {
        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("artistName", artistName)
            .addValue("tags", "[\"rock\",\"indie\"]");

        namedParameterJdbcTemplate.update("""
            INSERT INTO artist_tags (artist_name, tags_json, cached_at, source)
            VALUES (:artistName, CAST(:tags AS JSONB), NOW(), 'musicbrainz')
            ON CONFLICT (artist_name, source)
            DO UPDATE SET tags_json = CAST(:tags AS JSONB), cached_at = NOW()
            """, params);
    }

    private void enqueueSpotifyTrackLookup() {
        spotifyApiServer.enqueue(new MockResponse()
            .setResponseCode(200)
            .setHeader("Content-Type", "application/json")
            .setBody("""
                {
                  "id": "seed-track-id",
                  "name": "Seed Song",
                  "artists": [ { "name": "Seed Artist" } ],
                  "album": { "name": "Seed Album", "images": [ { "url": "https://images/seed", "height": 640 } ] },
                  "external_urls": { "spotify": "https://open.spotify.com/track/seed-track-id" },
                  "popularity": 60
                }
                """));
    }

    private void enqueueLastFmSimilar() {
        lastFmServer.enqueue(new MockResponse()
            .setResponseCode(200)
            .setHeader("Content-Type", "application/json")
            .setBody("""
                {
                  "similartracks": {
                    "track": [
                      {
                        "name": "Rec Track",
                        "match": "0.9",
                        "url": "https://last.fm/rec-track",
                        "artist": { "name": "Rec Artist" },
                        "image": [ { "#text": "https://image/rec", "size": "large" } ]
                      }
                    ]
                  }
                }
                """));
    }

    private void enqueueSpotifySearch() {
        spotifyApiServer.enqueue(new MockResponse()
            .setResponseCode(200)
            .setHeader("Content-Type", "application/json")
            .setBody("""
                {
                  "tracks": {
                    "items": [
                      {
                        "id": "rec-track-id",
                        "name": "Rec Track",
                        "artists": [ { "name": "Rec Artist" } ],
                        "album": { "name": "Rec Album", "images": [ { "url": "https://images/rec", "height": 640 } ] },
                        "external_urls": { "spotify": "https://open.spotify.com/track/rec-track-id" },
                        "popularity": 75
                      }
                    ]
                  }
                }
                """));
    }

    private void enqueueSpotifyPlaylistCreate() {
        spotifyApiServer.enqueue(new MockResponse()
            .setResponseCode(201)
            .setHeader("Content-Type", "application/json")
            .setBody("""
                {
                  "id": "playlist-123",
                  "name": "Test Playlist",
                  "external_urls": { "spotify": "https://open.spotify.com/playlist/playlist-123" }
                }
                """));
    }

    private void enqueueSpotifyAddTracks() {
        spotifyApiServer.enqueue(new MockResponse()
            .setResponseCode(201)
            .setHeader("Content-Type", "application/json")
            .setBody("""
                { "snapshot_id": "snapshot-1" }
                """));
    }
}

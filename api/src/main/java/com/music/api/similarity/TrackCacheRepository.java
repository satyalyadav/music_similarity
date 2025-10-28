package com.music.api.similarity;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class TrackCacheRepository {

    private static final Duration TTL = Duration.ofDays(7);

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public TrackCacheRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<TrackCacheEntry> findFresh(String spotifyId) {
        String sql = """
            SELECT spotify_id, name, artist, album, popularity, image_url, isrc, cached_at
            FROM track_cache
            WHERE spotify_id = :spotifyId
            """;
        return jdbcTemplate.query(sql, Map.of("spotifyId", spotifyId), (rs, rowNum) -> {
            Instant cachedAt = rs.getTimestamp("cached_at").toInstant();
            if (cachedAt.plus(TTL).isBefore(Instant.now())) {
                delete(spotifyId);
                return null;
            }
            return new TrackCacheEntry(
                rs.getString("spotify_id"),
                rs.getString("name"),
                rs.getString("artist"),
                rs.getString("album"),
                rs.getInt("popularity"),
                rs.getString("image_url"),
                rs.getString("isrc"),
                cachedAt
            );
        }).stream().filter(entry -> entry != null).findFirst();
    }

    public void upsert(TrackCacheEntry entry) {
        String sql = """
            INSERT INTO track_cache (spotify_id, name, artist, album, popularity, image_url, isrc, cached_at)
            VALUES (:spotifyId, :name, :artist, :album, :popularity, :imageUrl, :isrc, NOW())
            ON CONFLICT (spotify_id)
            DO UPDATE SET name = :name, artist = :artist, album = :album, popularity = :popularity, image_url = :imageUrl, isrc = :isrc, cached_at = NOW()
            """;
        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("spotifyId", entry.spotifyId())
            .addValue("name", entry.name())
            .addValue("artist", entry.artist())
            .addValue("album", entry.album())
            .addValue("popularity", entry.popularity())
            .addValue("imageUrl", entry.imageUrl())
            .addValue("isrc", entry.isrc());
        jdbcTemplate.update(sql, params);
    }

    private void delete(String spotifyId) {
        jdbcTemplate.update("DELETE FROM track_cache WHERE spotify_id = :spotifyId", Map.of("spotifyId", spotifyId));
    }

    public record TrackCacheEntry(
        String spotifyId,
        String name,
        String artist,
        String album,
        Integer popularity,
        String imageUrl,
        String isrc,
        Instant cachedAt
    ) {}
}

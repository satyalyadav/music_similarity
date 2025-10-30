package com.music.api.similarity;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class IdMapRepository {

    private static final Duration TTL = Duration.ofDays(7);
    private static final String SOURCE_LASTFM = "lastfm";

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public IdMapRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<IdMapEntry> findFresh(String key) {
        String sql = """
            SELECT spotify_id, confidence, cached_at
            FROM id_map
            WHERE source = :source
              AND source_key = :key
            """;
        return jdbcTemplate.query(sql, Map.of("source", SOURCE_LASTFM, "key", key), (rs, rowNum) -> {
            Instant cachedAt = rs.getTimestamp("cached_at").toInstant();
            if (cachedAt.plus(TTL).isBefore(Instant.now())) {
                delete(key);
                return null;
            }
            return new IdMapEntry(
                rs.getString("spotify_id"),
                rs.getBigDecimal("confidence") != null ? rs.getBigDecimal("confidence").doubleValue() : null,
                cachedAt
            );
        }).stream().filter(entry -> entry != null).findFirst();
    }

    public void upsert(String key, String spotifyId, double confidence) {
        String sql = """
            INSERT INTO id_map (source, source_key, spotify_id, confidence, cached_at)
            VALUES (:source, :key, :spotifyId, :confidence, NOW())
            ON CONFLICT (source, source_key)
            DO UPDATE SET spotify_id = :spotifyId, confidence = :confidence, cached_at = NOW()
            """;
        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("source", SOURCE_LASTFM)
            .addValue("key", key)
            .addValue("spotifyId", spotifyId)
            .addValue("confidence", confidence);
        jdbcTemplate.update(sql, params);
    }

    private void delete(String key) {
        jdbcTemplate.update(
            "DELETE FROM id_map WHERE source = :source AND source_key = :key",
            Map.of("source", SOURCE_LASTFM, "key", key)
        );
    }

    public record IdMapEntry(String spotifyId, Double confidence, Instant cachedAt) {}

    public int purgeExpired() {
        String sql = "DELETE FROM id_map WHERE cached_at < NOW() - INTERVAL '7 days'";
        return jdbcTemplate.update(sql, new MapSqlParameterSource());
    }
}

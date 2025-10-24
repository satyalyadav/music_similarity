package com.music.api.similarity;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class LastFmCacheRepository {

    private static final Duration TTL = Duration.ofHours(24);

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public LastFmCacheRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<String> findFreshResponse(String seedType, String seedKey) {
        String sql = """
            SELECT response_json::text, cached_at
            FROM lastfm_similarity_cache
            WHERE seed_type = :seedType
              AND seed_key = :seedKey
            """;
        return jdbcTemplate.query(sql, Map.of("seedType", seedType, "seedKey", seedKey), (rs, rowNum) -> {
            Instant cachedAt = rs.getTimestamp("cached_at").toInstant();
            if (cachedAt.plus(TTL).isBefore(Instant.now())) {
                delete(seedType, seedKey);
                return null;
            }
            return rs.getString(1);
        }).stream().filter(value -> value != null).findFirst();
    }

    public void upsert(String seedType, String seedKey, String json) {
        String sql = """
            INSERT INTO lastfm_similarity_cache (seed_type, seed_key, response_json, cached_at)
            VALUES (:seedType, :seedKey, CAST(:json AS JSONB), NOW())
            ON CONFLICT (seed_type, seed_key)
            DO UPDATE SET response_json = CAST(:json AS JSONB), cached_at = NOW()
            """;
        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("seedType", seedType)
            .addValue("seedKey", seedKey)
            .addValue("json", json);
        jdbcTemplate.update(sql, params);
    }

    private void delete(String seedType, String seedKey) {
        String sql = """
            DELETE FROM lastfm_similarity_cache
            WHERE seed_type = :seedType AND seed_key = :seedKey
            """;
        jdbcTemplate.update(sql, Map.of("seedType", seedType, "seedKey", seedKey));
    }
}

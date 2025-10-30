package com.music.api.similarity;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class ArtistTagsRepository {

    private static final Duration TTL = Duration.ofDays(30);

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public ArtistTagsRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<String> findFreshTags(String artistName) {
        String sql = """
            SELECT tags_json::text, cached_at
            FROM artist_tags
            WHERE artist_name = :artistName
              AND source = 'musicbrainz'
            """;
        return jdbcTemplate.query(sql, Map.of("artistName", artistName), (rs, rowNum) -> {
            Instant cachedAt = rs.getTimestamp("cached_at").toInstant();
            if (cachedAt.plus(TTL).isBefore(Instant.now())) {
                delete(artistName);
                return null;
            }
            return rs.getString(1);
        }).stream().filter(value -> value != null).findFirst();
    }

    public void upsert(String artistName, String tagsJson) {
        String sql = """
            INSERT INTO artist_tags (artist_name, tags_json, cached_at, source)
            VALUES (:artistName, CAST(:tags AS JSONB), NOW(), 'musicbrainz')
            ON CONFLICT (artist_name, source)
            DO UPDATE SET tags_json = CAST(:tags AS JSONB), cached_at = NOW()
            """;
        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("artistName", artistName)
            .addValue("tags", tagsJson);
        jdbcTemplate.update(sql, params);
    }

    private void delete(String artistName) {
        String sql = """
            DELETE FROM artist_tags WHERE artist_name = :artistName AND source = 'musicbrainz'
            """;
        jdbcTemplate.update(sql, Map.of("artistName", artistName));
    }

    public int purgeExpired() {
        String sql = "DELETE FROM artist_tags WHERE cached_at < NOW() - INTERVAL '30 days'";
        return jdbcTemplate.update(sql, new MapSqlParameterSource());
    }
}

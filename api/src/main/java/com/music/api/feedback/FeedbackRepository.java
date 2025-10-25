package com.music.api.feedback;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class FeedbackRepository {

    private static final RowMapper<FeedbackRecord> ROW_MAPPER = FeedbackRepository::mapRow;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public FeedbackRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public FeedbackRecord upsert(UUID userId, String spotifyId, short label) {
        String sql = """
            INSERT INTO feedback (user_id, spotify_id, label)
            VALUES (:userId, :spotifyId, :label)
            ON CONFLICT (user_id, spotify_id)
            DO UPDATE SET label = EXCLUDED.label, created_at = NOW()
            RETURNING user_id, spotify_id, label, created_at
            """;

        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("userId", userId)
            .addValue("spotifyId", spotifyId)
            .addValue("label", label);

        return jdbcTemplate.queryForObject(sql, params, ROW_MAPPER);
    }

    public Optional<FeedbackRecord> find(UUID userId, String spotifyId) {
        String sql = """
            SELECT user_id, spotify_id, label, created_at
            FROM feedback
            WHERE user_id = :userId AND spotify_id = :spotifyId
            """;

        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("userId", userId)
            .addValue("spotifyId", spotifyId);

        return jdbcTemplate.query(sql, params, ROW_MAPPER).stream().findFirst();
    }

    private static FeedbackRecord mapRow(ResultSet rs, int rowNum) throws SQLException {
        return new FeedbackRecord(
            rs.getObject("user_id", UUID.class),
            rs.getString("spotify_id"),
            rs.getShort("label"),
            rs.getTimestamp("created_at").toInstant()
        );
    }
}

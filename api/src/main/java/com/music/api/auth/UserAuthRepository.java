package com.music.api.auth;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class UserAuthRepository {

    private static final RowMapper<UserAuth> ROW_MAPPER = UserAuthRepository::mapUserAuth;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public UserAuthRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<UserAuth> findBySpotifyId(String spotifyId) {
        String sql = """
            SELECT user_id, spotify_id, access_token, refresh_token, scopes, created_at, updated_at
            FROM user_auth
            WHERE spotify_id = :spotifyId
            """;

        return queryForOptional(sql, Map.of("spotifyId", spotifyId));
    }

    public Optional<UserAuth> findByUserId(UUID userId) {
        String sql = """
            SELECT user_id, spotify_id, access_token, refresh_token, scopes, created_at, updated_at
            FROM user_auth
            WHERE user_id = :userId
            """;

        return queryForOptional(sql, Map.of("userId", userId));
    }

    public UserAuth insert(UUID userId, String spotifyId, String accessToken, String refreshToken, String scopes) {
        String sql = """
            INSERT INTO user_auth (user_id, spotify_id, access_token, refresh_token, scopes)
            VALUES (:userId, :spotifyId, :accessToken, :refreshToken, :scopes)
            RETURNING user_id, spotify_id, access_token, refresh_token, scopes, created_at, updated_at
            """;

        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("userId", userId)
            .addValue("spotifyId", spotifyId)
            .addValue("accessToken", accessToken)
            .addValue("refreshToken", refreshToken)
            .addValue("scopes", scopes);

        return jdbcTemplate.queryForObject(sql, params, ROW_MAPPER);
    }

    public UserAuth updateTokens(UUID userId, String accessToken, String refreshToken, String scopes) {
        String sql = """
            UPDATE user_auth
            SET access_token = :accessToken,
                refresh_token = COALESCE(:refreshToken, refresh_token),
                scopes = COALESCE(:scopes, scopes),
                updated_at = NOW()
            WHERE user_id = :userId
            RETURNING user_id, spotify_id, access_token, refresh_token, scopes, created_at, updated_at
            """;

        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("userId", userId)
            .addValue("accessToken", accessToken)
            .addValue("refreshToken", refreshToken)
            .addValue("scopes", scopes);

        return jdbcTemplate.queryForObject(sql, params, ROW_MAPPER);
    }

    private Optional<UserAuth> queryForOptional(String sql, Map<String, ?> params) {
        return jdbcTemplate.query(sql, params, ROW_MAPPER).stream().findFirst();
    }

    private static UserAuth mapUserAuth(ResultSet rs, int rowNum) throws SQLException {
        UUID userId = rs.getObject("user_id", UUID.class);
        String spotifyId = rs.getString("spotify_id");
        String accessToken = rs.getString("access_token");
        String refreshToken = rs.getString("refresh_token");
        String scopes = rs.getString("scopes");
        Instant createdAt = rs.getTimestamp("created_at").toInstant();
        Instant updatedAt = rs.getTimestamp("updated_at").toInstant();
        return new UserAuth(userId, spotifyId, accessToken, refreshToken, scopes, createdAt, updatedAt);
    }
}

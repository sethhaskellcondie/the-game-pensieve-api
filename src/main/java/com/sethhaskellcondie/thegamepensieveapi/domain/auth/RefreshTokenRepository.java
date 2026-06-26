package com.sethhaskellcondie.thegamepensieveapi.domain.auth;

import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.util.Optional;

/**
 * Stores refresh tokens as SHA-256 hashes. Lookups only return tokens that are still usable (not revoked and
 * not expired); rows are retained after revocation so the rotation chain stays auditable.
 */
@Repository
public class RefreshTokenRepository {

    private final JdbcTemplate jdbcTemplate;

    public RefreshTokenRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private RowMapper<RefreshToken> getRowMapper() {
        return (resultSet, rowNumber) -> new RefreshToken(
                resultSet.getInt("id"),
                resultSet.getInt("user_id"),
                resultSet.getString("token_hash"),
                resultSet.getTimestamp("expires_at"),
                resultSet.getBoolean("revoked")
        );
    }

    public void insert(int userId, String tokenHash, Timestamp expiresAt) {
        final String sql = "INSERT INTO refresh_tokens(user_id, token_hash, expires_at, revoked, created_at) VALUES (?, ?, ?, ?, ?);";
        jdbcTemplate.update(sql, userId, tokenHash, expiresAt, false, Timestamp.from(Instant.now()));
    }

    public Optional<RefreshToken> findActiveByHash(String tokenHash) {
        final String sql = "SELECT id, user_id, token_hash, expires_at, revoked FROM refresh_tokens "
                + "WHERE token_hash = ? AND revoked = FALSE AND expires_at > now()";
        try {
            final RefreshToken token = jdbcTemplate.queryForObject(sql, new Object[]{tokenHash}, new int[]{Types.VARCHAR}, getRowMapper());
            return Optional.ofNullable(token);
        } catch (EmptyResultDataAccessException ignored) {
            return Optional.empty();
        }
    }

    public void revokeById(int id) {
        final String sql = "UPDATE refresh_tokens SET revoked = TRUE WHERE id = ?;";
        jdbcTemplate.update(sql, id);
    }
}

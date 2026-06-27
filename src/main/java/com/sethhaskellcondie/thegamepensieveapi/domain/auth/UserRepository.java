package com.sethhaskellcondie.thegamepensieveapi.domain.auth;

import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.util.Optional;

/**
 * Lightweight JdbcTemplate access for user accounts. Kept separate from the generic EntityRepositoryAbstract
 * stack because auth data has no custom fields, no soft-delete, and no response-key plumbing.
 */
@Repository
public class UserRepository {

    private final JdbcTemplate jdbcTemplate;

    public UserRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    // Every column read by the row mapper, in a single place so the two finders stay in sync.
    private static final String SELECT_COLUMNS = "id, email, password_hash, enabled, created_at, updated_at, "
            + "plan, subscription_status, access_until, paddle_customer_id, paddle_subscription_id, last_event_id";

    private RowMapper<User> getRowMapper() {
        return (resultSet, rowNumber) -> new User(
                resultSet.getInt("id"),
                resultSet.getString("email"),
                resultSet.getString("password_hash"),
                resultSet.getBoolean("enabled"),
                resultSet.getTimestamp("created_at"),
                resultSet.getTimestamp("updated_at"),
                resultSet.getString("plan"),
                resultSet.getString("subscription_status"),
                resultSet.getTimestamp("access_until"),
                resultSet.getString("paddle_customer_id"),
                resultSet.getString("paddle_subscription_id"),
                resultSet.getString("last_event_id")
        );
    }

    public Optional<User> findByEmail(String email) {
        final String sql = "SELECT " + SELECT_COLUMNS + " FROM users WHERE email = ?";
        try {
            final User user = jdbcTemplate.queryForObject(sql, new Object[]{email}, new int[]{Types.VARCHAR}, getRowMapper());
            return Optional.ofNullable(user);
        } catch (EmptyResultDataAccessException ignored) {
            return Optional.empty();
        }
    }

    public Optional<User> findById(int id) {
        final String sql = "SELECT " + SELECT_COLUMNS + " FROM users WHERE id = ?";
        try {
            final User user = jdbcTemplate.queryForObject(sql, new Object[]{id}, new int[]{Types.INTEGER}, getRowMapper());
            return Optional.ofNullable(user);
        } catch (EmptyResultDataAccessException ignored) {
            return Optional.empty();
        }
    }

    public boolean existsByEmail(String email) {
        final String sql = "SELECT COUNT(*) FROM users WHERE email = ?";
        final Integer count = jdbcTemplate.queryForObject(sql, Integer.class, email);
        return count != null && count > 0;
    }

    /**
     * Create a user account. {@code accessUntil}/{@code subscriptionStatus} stamp the caller's entitlement
     * window (registration grants a trial); {@code plan} is left to the column's {@code 'free'} default.
     */
    public int insert(String email, String passwordHash, Timestamp accessUntil, String subscriptionStatus) {
        final String sql = "INSERT INTO users(email, password_hash, enabled, access_until, subscription_status, created_at, updated_at) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?);";
        final KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(
                connection -> {
                    PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
                    final Timestamp now = Timestamp.from(Instant.now());
                    ps.setString(1, email);
                    ps.setString(2, passwordHash);
                    ps.setBoolean(3, true);
                    ps.setTimestamp(4, accessUntil);
                    ps.setString(5, subscriptionStatus);
                    ps.setTimestamp(6, now);
                    ps.setTimestamp(7, now);
                    return ps;
                },
                keyHolder
        );
        return (Integer) keyHolder.getKeys().get("id");
    }
}

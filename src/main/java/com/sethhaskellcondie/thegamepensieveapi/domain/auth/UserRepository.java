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
            + "plan, subscription_status, access_until, paddle_customer_id, paddle_subscription_id, last_event_id, "
            + "role_override, showcase_slug, showcase_name";

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
                resultSet.getString("last_event_id"),
                resultSet.getString("role_override"),
                resultSet.getString("showcase_slug"),
                resultSet.getString("showcase_name")
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

    /**
     * Find the owner of a public showcase by its slug (the {@code X-Showcase} header value). An indexed lookup —
     * {@code showcase_slug} is UNIQUE. The caller applies the visibility rule (owner must derive to PAID/ADMIN).
     */
    public Optional<User> findByShowcaseSlug(String slug) {
        final String sql = "SELECT " + SELECT_COLUMNS + " FROM users WHERE showcase_slug = ?";
        try {
            final User user = jdbcTemplate.queryForObject(sql, new Object[]{slug}, new int[]{Types.VARCHAR}, getRowMapper());
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

    /** List every account, ordered by id. Used by the admin role-management API (runs with app privileges,
     * outside the per-request {@code app_rls} transaction — {@code users} is not under RLS). */
    public java.util.List<User> findAll() {
        final String sql = "SELECT " + SELECT_COLUMNS + " FROM users ORDER BY id";
        return jdbcTemplate.query(sql, getRowMapper());
    }

    /**
     * Set or clear a user's {@code role_override} admin pin ({@code null} reverts to auto-derivation). Returns
     * the number of rows updated so the caller can distinguish a missing user (0) from a successful pin (1).
     */
    public int updateRoleOverride(int id, String roleOverride) {
        final String sql = "UPDATE users SET role_override = ?, updated_at = ? WHERE id = ?";
        return jdbcTemplate.update(sql,
                new Object[]{roleOverride, Timestamp.from(Instant.now()), id},
                new int[]{Types.VARCHAR, Types.TIMESTAMP, Types.INTEGER});
    }

    /**
     * Grant or clear a user's public showcase. A null {@code slug} clears the grant (the name goes with it);
     * the caller validates the slug format and translates a duplicate-slug violation. Returns the number of
     * rows updated so the caller can distinguish a missing user (0) from a successful grant (1).
     */
    public int updateShowcase(int id, String slug, String name) {
        final String sql = "UPDATE users SET showcase_slug = ?, showcase_name = ?, updated_at = ? WHERE id = ?";
        return jdbcTemplate.update(sql,
                new Object[]{slug, slug == null ? null : name, Timestamp.from(Instant.now()), id},
                new int[]{Types.VARCHAR, Types.VARCHAR, Types.TIMESTAMP, Types.INTEGER});
    }

    /** Every account holding a showcase slug, ordered by slug. The caller applies the visibility rule. */
    public java.util.List<User> findAllWithShowcaseSlug() {
        final String sql = "SELECT " + SELECT_COLUMNS + " FROM users WHERE showcase_slug IS NOT NULL ORDER BY showcase_slug";
        return jdbcTemplate.query(sql, getRowMapper());
    }

    public boolean existsByEmail(String email) {
        final String sql = "SELECT COUNT(*) FROM users WHERE email = ?";
        final Integer count = jdbcTemplate.queryForObject(sql, Integer.class, email);
        return count != null && count > 0;
    }

    /**
     * Create a user account. {@code accessUntil}/{@code subscriptionStatus} stamp the caller's access
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

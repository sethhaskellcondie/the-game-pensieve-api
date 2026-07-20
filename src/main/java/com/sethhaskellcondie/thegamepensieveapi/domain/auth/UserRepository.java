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
    private static final String SELECT_COLUMNS = "id, email, keycloak_sub, password_hash, enabled, created_at, updated_at, "
            + "plan, subscription_status, access_until, paddle_customer_id, paddle_subscription_id, last_event_id, "
            + "role_override, showcase_slug, showcase_name";

    private RowMapper<User> getRowMapper() {
        return (resultSet, rowNumber) -> new User(
                resultSet.getInt("id"),
                resultSet.getString("email"),
                resultSet.getString("keycloak_sub"),
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

    /**
     * Find an account by email, case-insensitively: Keycloak normalizes emails to lowercase, but seeded rows are
     * typed by hand — a case mismatch here would silently skip claim-on-first-login and JIT-provision a duplicate.
     */
    public Optional<User> findByEmail(String email) {
        final String sql = "SELECT " + SELECT_COLUMNS + " FROM users WHERE LOWER(email) = LOWER(?)";
        try {
            final User user = jdbcTemplate.queryForObject(sql, new Object[]{email}, new int[]{Types.VARCHAR}, getRowMapper());
            return Optional.ofNullable(user);
        } catch (EmptyResultDataAccessException ignored) {
            return Optional.empty();
        }
    }

    /**
     * Find an account by its Keycloak subject claim ({@code sub}) — the primary owner lookup under the secured
     * profile. An indexed lookup ({@code keycloak_sub} is UNIQUE). Empty until the owner's first login has
     * claimed the row (see {@link #updateSub}).
     */
    public Optional<User> findBySub(String sub) {
        final String sql = "SELECT " + SELECT_COLUMNS + " FROM users WHERE keycloak_sub = ?";
        try {
            final User user = jdbcTemplate.queryForObject(sql, new Object[]{sub}, new int[]{Types.VARCHAR}, getRowMapper());
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
     * Claim a seeded (email-created, sub-less) row for a Keycloak account by stamping its {@code sub} on first
     * login. Returns the number of rows updated so the caller can distinguish a missing/already-claimed row (0)
     * from a successful claim (1).
     */
    public int updateSub(int id, String sub) {
        final String sql = "UPDATE users SET keycloak_sub = ?, updated_at = ? WHERE id = ?";
        return jdbcTemplate.update(sql,
                new Object[]{sub, Timestamp.from(Instant.now()), id},
                new int[]{Types.VARCHAR, Types.TIMESTAMP, Types.INTEGER});
    }

    /**
     * Mirror an email change made at the IdP onto the account row (the token's verified email differs from the
     * stored one). Throws {@code DuplicateKeyException} when another row already holds the address ({@code email}
     * is UNIQUE); the caller decides whether that is fatal.
     */
    public int updateEmail(int id, String email) {
        final String sql = "UPDATE users SET email = ?, updated_at = ? WHERE id = ?";
        return jdbcTemplate.update(sql,
                new Object[]{email, Timestamp.from(Instant.now()), id},
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

    /**
     * JIT-provision a user account for a Keycloak identity on first login (no seeded row matched the token's
     * {@code sub} or {@code email}). Keyed by the immutable {@code sub}; {@code accessUntil}/{@code subscriptionStatus}
     * stamp the auto-granted trial window; {@code password_hash} is left NULL (passwords live in Keycloak) and
     * {@code plan} defaults to the column's {@code 'free'}.
     */
    public int insertJit(String email, String keycloakSub, Timestamp accessUntil, String subscriptionStatus) {
        final String sql = "INSERT INTO users(email, keycloak_sub, enabled, access_until, subscription_status, created_at, updated_at) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?);";
        final KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(
                connection -> {
                    PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
                    final Timestamp now = Timestamp.from(Instant.now());
                    ps.setString(1, email);
                    ps.setString(2, keycloakSub);
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

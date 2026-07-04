package com.sethhaskellcondie.thegamepensieveapi.domain.tenant;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.JdbcTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Asserts that {@code V1_15__AddBillingToUsers.sql} adds the price-agnostic billing columns to {@code users},
 * backfills existing rows (including the seeded showcase owner) to {@code plan='free'} with a null access
 * window, and enforces the {@code plan} CHECK constraint.
 *
 * <p>Also asserts the role-based access model's {@code V1_16__AddRoleOverrideToUsers.sql} — a nullable
 * {@code role_override} admin pin defaulting to NULL (auto-derivation) and constrained to the five roles
 * ('GUEST','TRIAL','PAID','LAPSED','ADMIN').
 *
 */
@JdbcTest
@ActiveProfiles("rls-tests")
public class UsersBillingColumnsTests {

    @Autowired
    protected JdbcTemplate jdbcTemplate;

    @Test
    void billingColumns_Exist() {
        // Selecting every new column proves each exists; the query errors if any is missing.
        jdbcTemplate.queryForList(
                "SELECT plan, subscription_status, access_until, paddle_customer_id, paddle_subscription_id, last_event_id "
                        + "FROM users LIMIT 1");
    }

    @Test
    void existingRows_BackfillToFreeWithNoAccessWindow() {
        // The showcase owner is seeded by V1_13, before V1_15 exists, so it exercises the backfill path.
        final String plan = jdbcTemplate.queryForObject(
                "SELECT plan FROM users WHERE is_public_showcase", String.class);
        final java.sql.Timestamp accessUntil = jdbcTemplate.queryForObject(
                "SELECT access_until FROM users WHERE is_public_showcase", java.sql.Timestamp.class);
        assertEquals("free", plan, "Existing rows should backfill to plan='free'.");
        assertNull(accessUntil, "Existing rows should backfill to a null access window.");
    }

    @Test
    void newUser_DefaultsToFreePlan() {
        final String email = "billing-" + java.util.UUID.randomUUID() + "@example.com";
        jdbcTemplate.update("INSERT INTO users(email, password_hash, enabled) VALUES (?, '!', true)", email);
        final String plan = jdbcTemplate.queryForObject("SELECT plan FROM users WHERE email = ?", String.class, email);
        assertEquals("free", plan, "A new user with no explicit plan should default to 'free'.");
    }

    @Test
    void planCheckConstraint_RejectsUnknownValue() {
        final String email = "billing-" + java.util.UUID.randomUUID() + "@example.com";
        assertThrows(DataIntegrityViolationException.class, () -> jdbcTemplate.update(
                "INSERT INTO users(email, password_hash, enabled, plan) VALUES (?, '!', true, 'bogus')", email),
                "The plan CHECK constraint should reject values outside ('free','paid').");
    }

    // ===================== V1_16 role_override (staged red until the migration lands) =====================

    @Test
    void roleOverrideColumn_DefaultsToNull() {
        // A user created without an explicit override has none — the role is auto-derived per request.
        final String email = "role-" + java.util.UUID.randomUUID() + "@example.com";
        jdbcTemplate.update("INSERT INTO users(email, password_hash, enabled) VALUES (?, '!', true)", email);
        final String roleOverride = jdbcTemplate.queryForObject(
                "SELECT role_override FROM users WHERE email = ?", String.class, email);
        assertNull(roleOverride, "role_override should default to NULL so the role is auto-derived.");
    }

    @Test
    void roleOverrideCheckConstraint_AcceptsAValidRole() {
        // A pin to any of the five roles is allowed ('PAID' here — 'ADMIN' is additionally limited to one row
        // by the V1_17 partial unique index, asserted separately below).
        final String email = "role-" + java.util.UUID.randomUUID() + "@example.com";
        jdbcTemplate.update(
                "INSERT INTO users(email, password_hash, enabled, role_override) VALUES (?, '!', true, 'PAID')", email);
        final String roleOverride = jdbcTemplate.queryForObject(
                "SELECT role_override FROM users WHERE email = ?", String.class, email);
        assertEquals("PAID", roleOverride, "A role_override of one of the five roles should be accepted and stored.");
    }

    @Test
    void roleOverrideCheckConstraint_RejectsUnknownValue() {
        final String email = "role-" + java.util.UUID.randomUUID() + "@example.com";
        assertThrows(DataIntegrityViolationException.class, () -> jdbcTemplate.update(
                "INSERT INTO users(email, password_hash, enabled, role_override) VALUES (?, '!', true, 'bogus')", email),
                "The role_override CHECK constraint should reject values outside the five roles.");
    }

    // ===================== V1_18 showcase_slug / showcase_name =====================

    @Test
    void showcaseColumns_ExistAndDefaultToNull() {
        final String email = "showcase-" + java.util.UUID.randomUUID() + "@example.com";
        jdbcTemplate.update("INSERT INTO users(email, password_hash, enabled) VALUES (?, '!', true)", email);
        final java.util.Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT showcase_slug, showcase_name FROM users WHERE email = ?", email);
        assertNull(row.get("showcase_slug"), "showcase_slug should default to NULL — the collection is private.");
        assertNull(row.get("showcase_name"), "showcase_name should default to NULL.");
    }

    @Test
    void seededDefaultShowcaseRow_CarriesTheDefaultSlugAndName() {
        final java.util.Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT showcase_slug, showcase_name FROM users WHERE is_public_showcase");
        assertEquals("seths-collection", row.get("showcase_slug"),
                "V1_18 should seed the default showcase's slug on the is_public_showcase row.");
        assertEquals("Seth's Collection", row.get("showcase_name"),
                "V1_18 should seed the default showcase's display name on the is_public_showcase row.");
    }

    @Test
    void showcaseSlug_MustBeUnique() {
        final String slug = "dup-" + Math.abs(java.util.UUID.randomUUID().getLeastSignificantBits() % 1000000);
        jdbcTemplate.update(
                "INSERT INTO users(email, password_hash, enabled, showcase_slug) VALUES (?, '!', true, ?)",
                "showcase-" + java.util.UUID.randomUUID() + "@example.com", slug);
        assertThrows(DataIntegrityViolationException.class, () -> jdbcTemplate.update(
                "INSERT INTO users(email, password_hash, enabled, showcase_slug) VALUES (?, '!', true, ?)",
                "showcase-" + java.util.UUID.randomUUID() + "@example.com", slug),
                "showcase_slug is the public address — two users must not share one.");
    }

    @Test
    void showcaseSlugCheckConstraint_AcceptsValidShapes() {
        // Lowercase alphanumerics with single interior hyphens. (All inserts succeed, so they can share the
        // test transaction — unlike rejections, where the first failed statement aborts it.)
        for (String valid : new String[]{"a", "abc", "a-b-c", "slug-1", "1-a"}) {
            jdbcTemplate.update(
                    "INSERT INTO users(email, password_hash, enabled, showcase_slug) VALUES (?, '!', true, ?)",
                    "showcase-" + java.util.UUID.randomUUID() + "@example.com",
                    valid + "-" + Math.abs(java.util.UUID.randomUUID().getLeastSignificantBits() % 1000000));
        }
    }

    @Test
    void showcaseSlugCheckConstraint_RejectsAnInvalidFormat() {
        // One rejection proves the CHECK is wired (a failed statement aborts the test transaction, so more
        // shapes can't be probed here); the admin grant API validates the full format matrix per request.
        assertThrows(DataIntegrityViolationException.class, () -> jdbcTemplate.update(
                "INSERT INTO users(email, password_hash, enabled, showcase_slug) VALUES (?, '!', true, 'Has-Uppercase')",
                "showcase-" + java.util.UUID.randomUUID() + "@example.com"),
                "chk_users_showcase_slug should reject a slug with uppercase characters.");
    }

    // ===================== V1_17 single-admin partial unique index =====================

    @Test
    void singleAdminIndex_AllowsOnePinnedAdmin_RejectsASecond() {
        final String first = "admin-" + java.util.UUID.randomUUID() + "@example.com";
        final String second = "admin-" + java.util.UUID.randomUUID() + "@example.com";
        jdbcTemplate.update(
                "INSERT INTO users(email, password_hash, enabled, role_override) VALUES (?, '!', true, 'ADMIN')", first);
        assertThrows(DataIntegrityViolationException.class, () -> jdbcTemplate.update(
                "INSERT INTO users(email, password_hash, enabled, role_override) VALUES (?, '!', true, 'ADMIN')", second),
                "uq_users_single_admin should allow at most one account pinned role_override='ADMIN'.");
    }
}

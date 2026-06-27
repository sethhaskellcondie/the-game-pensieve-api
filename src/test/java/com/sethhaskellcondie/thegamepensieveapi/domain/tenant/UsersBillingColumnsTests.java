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
 * Phase 3 tests-first checkpoint — the V1_15 migration shape.
 * <p>
 * Asserts that {@code V1_15__AddBillingToUsers.sql} adds the price-agnostic entitlement columns to
 * {@code users}, backfills existing rows (including the seeded showcase owner) to {@code plan='free'} with a
 * null access window, and enforces the {@code plan} CHECK constraint.
 * <p>
 * <strong>Expected to FAIL/ERROR until V1_15 lands.</strong> On current code the columns do not exist, so the
 * selects below error — the intended red state for the tests-first checkpoint.
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
}

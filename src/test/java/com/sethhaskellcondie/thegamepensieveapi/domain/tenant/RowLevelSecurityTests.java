package com.sethhaskellcondie.thegamepensieveapi.domain.tenant;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.JdbcTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase 2 multi-tenancy contract — the hard database boundary.
 * <p>
 * Where {@code controllers.MultiTenancyTests} proves isolation through the HTTP API, this proves it at the
 * lowest level: a <strong>raw, handwritten {@code SELECT *}</strong> issued while the connection has assumed
 * the non-superuser {@code app_rls} role and the {@code app.current_owner} session variable set. Row-Level
 * Security — not application code — is what filters the rows, so even SQL that forgets a {@code WHERE owner_id}
 * clause cannot leak another tenant's data.
 * <p>
 * Seeding runs first as the (superuser) test role, which bypasses RLS; we then drop to {@code app_rls} with
 * {@code SET LOCAL ROLE} and read. Everything is transaction-local ({@code SET LOCAL} / {@code set_config(...,
 * true)}) and @JdbcTest rolls the transaction back, so nothing leaks across tests or pooled connections.
 * <p>
 * <strong>Expected to FAIL/ERROR until Part 2 lands.</strong> On current code there is no {@code owner_id}
 * column, no {@code is_public_showcase} flag, no {@code app_rls} role and no RLS policies — so the seeding
 * INSERTs and the {@code SET LOCAL ROLE} below cannot succeed yet. That is the intended red state for the
 * tests-first checkpoint.
 */
@JdbcTest
@ActiveProfiles("rls-tests")
public class RowLevelSecurityTests {

    @Autowired
    protected JdbcTemplate jdbcTemplate;

    @Test
    void rawSelect_AsOwner_ReturnsOnlyOwnRows() {
        final int ownerA = insertUser(false);
        final int ownerB = insertUser(false);
        insertSystem("A-One", ownerA);
        insertSystem("A-Two", ownerA);
        insertSystem("B-One", ownerB);

        assumeOwner(ownerA);

        final List<String> visible = jdbcTemplate.queryForList("SELECT name FROM systems", String.class);
        assertTrue(visible.contains("A-One") && visible.contains("A-Two"),
                "Owner A's raw SELECT should return all of A's own rows.");
        assertFalse(visible.contains("B-One"),
                "Owner A's raw SELECT must not return owner B's rows — RLS should hide them entirely.");
    }

    @Test
    void rawSelect_AsShowcaseOwner_ReturnsShowcaseRows() {
        final int showcaseOwner = insertUser(true);
        final int ownerA = insertUser(false);
        insertSystem("Showcase-One", showcaseOwner);
        insertSystem("A-One", ownerA);

        assumeOwner(showcaseOwner);

        final List<String> visible = jdbcTemplate.queryForList("SELECT name FROM systems", String.class);
        assertTrue(visible.contains("Showcase-One"),
                "Reading as the showcase owner should return the showcase rows.");
        assertFalse(visible.contains("A-One"),
                "Reading as the showcase owner must not return a private owner's rows.");
    }

    @Test
    void rawSelect_AsOwner_DoesNotSeeShowcaseRows() {
        final int showcaseOwner = insertUser(true);
        final int ownerA = insertUser(false);
        insertSystem("Showcase-One", showcaseOwner);
        insertSystem("A-One", ownerA);

        assumeOwner(ownerA);

        final List<String> visible = jdbcTemplate.queryForList("SELECT name FROM systems", String.class);
        assertTrue(visible.contains("A-One"), "Owner A should see their own row.");
        assertFalse(visible.contains("Showcase-One"),
                "A private owner must NOT see the public showcase owner's rows — the showcase never bleeds into a "
                        + "logged-in user's results.");
    }

    @Test
    void rawInsert_OmittingOwnerId_StampsCurrentOwner() {
        final int ownerA = insertUser(false);

        assumeOwner(ownerA);
        // Mirrors the repositories' hand-built INSERTs, which never name owner_id: the column DEFAULT must
        // stamp it from app.current_owner.
        jdbcTemplate.update("INSERT INTO systems(name, generation, handheld, created_at, updated_at) "
                + "VALUES ('Stamped', 1, false, now(), now())");

        final int stampedOwner = jdbcTemplate.queryForObject(
                "SELECT owner_id FROM systems WHERE name = 'Stamped'", Integer.class);
        assertEquals(ownerA, stampedOwner, "An insert with no explicit owner_id should be stamped with the current owner.");
    }

    @Test
    void rawSelect_AsOwner_ReturnsOnlyOwnMetadata() {
        final int ownerA = insertUser(false);
        final int ownerB = insertUser(false);
        insertMetadata("a.setting", ownerA);
        insertMetadata("b.setting", ownerB);

        assumeOwner(ownerA);

        final List<String> visible = jdbcTemplate.queryForList("SELECT key FROM metadata", String.class);
        assertTrue(visible.contains("a.setting"), "Owner A should see their own metadata row.");
        assertFalse(visible.contains("b.setting"), "Owner A must not see owner B's metadata row.");
    }

    // ------------------------------- Private helpers -------------------------------

    /** Seeds a user (as the superuser test role) and returns its id. One user may be the public showcase. */
    private int insertUser(boolean publicShowcase) {
        final String email = "rls-" + java.util.UUID.randomUUID() + "@example.com";
        return jdbcTemplate.queryForObject(
                "INSERT INTO users(email, password_hash, enabled, is_public_showcase) VALUES (?, '!', true, ?) RETURNING id",
                Integer.class, email, publicShowcase);
    }

    private void insertSystem(String name, int ownerId) {
        jdbcTemplate.update(
                "INSERT INTO systems(name, generation, handheld, owner_id, created_at, updated_at) "
                        + "VALUES (?, 1, false, ?, now(), now())",
                name, ownerId);
    }

    private void insertMetadata(String key, int ownerId) {
        jdbcTemplate.update(
                "INSERT INTO metadata(key, value, owner_id, created_at, updated_at) "
                        + "VALUES (?, '{}'::jsonb, ?, now(), now())",
                key, ownerId);
    }

    /**
     * Drop from the (superuser) test role to the restricted app role and set the tenant for the rest of this
     * transaction.
     */
    private void assumeOwner(int ownerId) {
        jdbcTemplate.execute("SET LOCAL ROLE app_rls");
        jdbcTemplate.update("SELECT set_config('app.current_owner', ?, true)", String.valueOf(ownerId));
    }
}

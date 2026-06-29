package com.sethhaskellcondie.thegamepensieveapi.domain.tenant;

import com.sethhaskellcondie.thegamepensieveapi.domain.auth.UserRepository;
import com.sethhaskellcondie.thegamepensieveapi.domain.entity.system.System;
import com.sethhaskellcondie.thegamepensieveapi.domain.entity.system.SystemRepository;
import com.sethhaskellcondie.thegamepensieveapi.domain.exceptions.ExceptionResourceNotFound;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.JdbcTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Sibling of {@link RowLevelSecurityTests}: that suite issues raw SQL to prove the database boundary; this one
 * drives the production {@link SystemRepository} ({@code insert}, {@code getById}, {@code getWithFilters}). The
 * repository SQL carries <strong>no {@code owner_id} predicate</strong> — Row-Level Security alone scopes the
 * results — so these tests confirm that an unmodified repository transparently sees only the current owner's rows
 * and that its hand-built INSERT (which never names {@code owner_id}) is stamped from the session by the column
 * DEFAULT.
 * <p>
 * Rows are seeded <em>through the repository</em> while the connection has assumed the non-superuser
 * {@code app_rls} role with {@code app.current_owner} set, exactly as a request would. Users are seeded first as
 * the (superuser) test role, since {@code app_rls} has no grant on the {@code users} table. Everything is
 * transaction-local and @JdbcTest rolls the transaction back.
 */
@JdbcTest
@ActiveProfiles("rls-tests")
public class RepositoryRowLevelSecurityTests {

    @Autowired
    protected JdbcTemplate jdbcTemplate;
    protected SystemRepository systemRepository;
    protected UserRepository userRepository;

    @BeforeEach
    public void setUp() {
        systemRepository = new SystemRepository(jdbcTemplate);
        userRepository = new UserRepository(jdbcTemplate);
    }

    @Test
    void getWithFilters_AsOwner_ReturnsOnlyOwnRows() {
        final int ownerA = insertUser();
        final int ownerB = insertUser();
        insertSystemAsOwner("A-One", ownerA);
        insertSystemAsOwner("A-Two", ownerA);
        insertSystemAsOwner("B-One", ownerB);

        assumeOwner(ownerA);
        final List<String> visible = systemRepository.getWithFilters(List.of()).stream().map(System::getName).toList();

        assertTrue(visible.contains("A-One") && visible.contains("A-Two"),
                "Owner A's repository read should return all of A's own systems.");
        assertFalse(visible.contains("B-One"),
                "Owner A's repository read must not return owner B's systems — RLS hides them with no owner_id clause in the SQL.");
    }

    @Test
    void getById_OtherUsersSystem_ThrowsNotFound() {
        final int ownerA = insertUser();
        final int ownerB = insertUser();
        assumeOwner(ownerB);
        final int bSystemId = systemRepository.insert(newSystem("B-Only")).getId();

        assumeOwner(ownerA);
        assertThrows(ExceptionResourceNotFound.class, () -> systemRepository.getById(bSystemId),
                "Owner A reading owner B's system by id through the repository should be a not-found — the row is invisible.");
    }

    @Test
    void insert_OmittingOwnerId_StampsCurrentOwner() {
        final int ownerA = insertUser();

        assumeOwner(ownerA);
        // SystemRepository.insert builds an INSERT that never names owner_id; the column DEFAULT must stamp it.
        final int newId = systemRepository.insert(newSystem("Stamped")).getId();

        final int stampedOwner = jdbcTemplate.queryForObject("SELECT owner_id FROM systems WHERE id = ?", Integer.class, newId);
        assertEquals(ownerA, stampedOwner, "A repository insert with no explicit owner_id should be stamped with the current owner.");
    }

    @Test
    void getWithFilters_AsOwner_DoesNotSeeShowcaseRows() {
        final int showcaseOwner = showcaseOwnerId();
        final int ownerA = insertUser();
        insertSystemAsOwner("Showcase-One", showcaseOwner);
        insertSystemAsOwner("A-One", ownerA);

        assumeOwner(ownerA);
        final List<String> visible = systemRepository.getWithFilters(List.of()).stream().map(System::getName).toList();

        assertTrue(visible.contains("A-One"), "Owner A should see their own system through the repository.");
        assertFalse(visible.contains("Showcase-One"),
                "A private owner must NOT see the public showcase owner's systems — the showcase never bleeds into a logged-in user's results.");
    }

    // ------------------------------- Private helpers -------------------------------

    private System newSystem(String name) {
        return new System(null, name, 1, false, null, null, null, new ArrayList<>());
    }

    /** Insert a system through the repository while acting as the given owner (owner_id is stamped from the session). */
    private void insertSystemAsOwner(String name, int ownerId) {
        assumeOwner(ownerId);
        systemRepository.insert(newSystem(name));
    }

    /** Seeds a normal (non-showcase) user through the production {@link UserRepository} and returns its id. */
    private int insertUser() {
        final String email = "rls-repo-" + java.util.UUID.randomUUID() + "@example.com";
        return userRepository.insert(email, "!", null, null);
    }

    /** The V1_13 migration seeds the public showcase owner; resolve it rather than creating another. */
    private int showcaseOwnerId() {
        return jdbcTemplate.queryForObject("SELECT id FROM users WHERE is_public_showcase", Integer.class);
    }

    /**
     * Drop from the (superuser) test role to the restricted app role and set the tenant for the rest of this
     * transaction. Safe to call repeatedly to switch owners — re-issuing SET LOCAL ROLE is a no-op.
     */
    private void assumeOwner(int ownerId) {
        jdbcTemplate.execute("SET LOCAL ROLE app_rls");
        jdbcTemplate.queryForObject("SELECT set_config('app.current_owner', ?, true)", String.class, String.valueOf(ownerId));
    }
}

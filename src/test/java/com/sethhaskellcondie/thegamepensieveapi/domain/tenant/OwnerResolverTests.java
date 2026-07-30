package com.sethhaskellcondie.thegamepensieveapi.domain.tenant;

import com.sethhaskellcondie.thegamepensieveapi.api.tenant.OwnerContext;
import com.sethhaskellcondie.thegamepensieveapi.api.tenant.OwnerResolver;
import com.sethhaskellcondie.thegamepensieveapi.domain.auth.Role;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins {@link OwnerResolver}'s anonymous-caller resolution: with no access token in the SecurityContext (the
 * default permit-all build, or a guest hitting the secured build), the acting owner is the <em>seeded default
 * showcase</em> — the single {@code users} row flagged {@code is_public_showcase} — always as GUEST.
 *
 * <p>These tests exist because that lookup is the one piece of {@code users} access the resolver still issues
 * itself; they hold the behavior still while it moves behind {@code UserRepository}. The token-carrying paths
 * (claim-by-email, JIT provisioning, impersonation) are covered by the secured-profile controller tests.
 */
@SpringBootTest
@ActiveProfiles("test-container")
public class OwnerResolverTests {

    @Autowired
    private OwnerResolver ownerResolver;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void resolveOwner_Anonymous_ResolvesTheSeededShowcaseOwnerAsGuest() {
        final OwnerContext owner = ownerResolver.resolveOwner();

        assertEquals(seededShowcaseOwnerId(), owner.ownerId(), "an anonymous caller should act as the seeded default showcase owner");
        assertEquals(Role.GUEST, owner.role(), "an anonymous caller is always a GUEST");
        assertNull(owner.impersonator(), "a plain anonymous request carries no impersonator");
        assertFalse(owner.showcase(), "resolveOwner is not an X-Showcase view — only resolveShowcase sets that flag");
    }

    @Test
    void resolveOwnerId_CalledRepeatedly_KeepsReturningTheSameShowcaseOwner() {
        final Integer expected = seededShowcaseOwnerId();

        // The id is cached after the first lookup; every later call has to agree with it.
        assertEquals(expected, ownerResolver.resolveOwnerId());
        assertEquals(expected, ownerResolver.resolveOwnerId());
        assertEquals(expected, ownerResolver.resolveOwner().ownerId());
    }

    @Test
    void resolveOwner_ActAsOwnerHeaderFromANonAdmin_IsIgnored() {
        final Integer showcaseOwnerId = seededShowcaseOwnerId();
        final int otherUserId = insertUser();

        // An anonymous caller is a GUEST, so impersonation must not apply — resolution falls back to the caller.
        final OwnerContext owner = ownerResolver.resolveOwner(String.valueOf(otherUserId));

        assertEquals(showcaseOwnerId, owner.ownerId(), "a non-admin caller must not be able to act as another owner");
        assertEquals(Role.GUEST, owner.role());
        assertNull(owner.impersonator());
    }

    @Test
    void resolveShowcase_UnknownSlug_IsEmpty() {
        final Optional<OwnerContext> resolved = ownerResolver.resolveShowcase("no-such-showcase-" + java.util.UUID.randomUUID());

        assertTrue(resolved.isEmpty(), "an unknown showcase slug should resolve to nothing (the filter answers 404)");
    }

    private Integer seededShowcaseOwnerId() {
        return jdbcTemplate.queryForObject("SELECT id FROM users WHERE is_public_showcase", Integer.class);
    }

    /** Seeds a plain (non-showcase) user directly and returns its id. */
    private int insertUser() {
        final String email = "owner-resolver-" + java.util.UUID.randomUUID() + "@example.com";
        return jdbcTemplate.queryForObject("INSERT INTO users(email) VALUES (?) RETURNING id", Integer.class, email);
    }
}

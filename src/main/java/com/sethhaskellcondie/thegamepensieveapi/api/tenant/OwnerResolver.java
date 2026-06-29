package com.sethhaskellcondie.thegamepensieveapi.api.tenant;

import com.sethhaskellcondie.thegamepensieveapi.domain.auth.Role;
import com.sethhaskellcondie.thegamepensieveapi.domain.auth.User;
import com.sethhaskellcondie.thegamepensieveapi.domain.auth.UserRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Resolves the owner id for the current request from the {@link org.springframework.security.core.context.SecurityContext}.
 *
 * <ul>
 *   <li>An authenticated request → the user's id, looked up by the principal's email.</li>
 *   <li>An anonymous request (the default permit-all build, or before login) → the seeded public showcase owner,
 *       so guests transparently read the showcase.</li>
 * </ul>
 *
 * <p>Resolution runs <em>before</em> the request transaction drops to {@code app_rls}, so the {@code users} lookups
 * here execute with the application's normal (superuser) privileges — {@code app_rls} has no grant on {@code users}.
 */
@Component
public class OwnerResolver {

    private final UserRepository userRepository;
    private final JdbcTemplate jdbcTemplate;
    // The showcase owner id never changes after V1_13 seeds it; cache the first lookup.
    private final AtomicReference<Integer> showcaseOwnerId = new AtomicReference<>();

    public OwnerResolver(UserRepository userRepository, JdbcTemplate jdbcTemplate) {
        this.userRepository = userRepository;
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Resolve the owner id and effective {@link Role} for the current request from a single {@code users}
     * lookup. Runs <em>before</em> the request transaction drops to {@code app_rls}, so the {@code users} read
     * here executes with the application's normal (superuser) privileges.
     *
     * <ul>
     *   <li>Authenticated → the user's id and {@link #roleFor(User) derived role}.</li>
     *   <li>Anonymous → the seeded public showcase owner, always GUEST.</li>
     * </ul>
     */
    public OwnerContext resolveOwner() {
        final Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated()
                && authentication.getPrincipal() instanceof UserDetails userDetails) {
            return userRepository.findByEmail(userDetails.getUsername())
                    .map(user -> new OwnerContext(user.id(), roleFor(user)))
                    .orElseGet(() -> new OwnerContext(showcaseOwnerId(), Role.GUEST));
        }
        return new OwnerContext(showcaseOwnerId(), Role.GUEST);
    }

    public Integer resolveOwnerId() {
        return resolveOwner().ownerId();
    }

    /**
     * Derive an authenticated user's role: an admin {@code role_override} pin wins outright; otherwise a future
     * {@code access_until} resolves to TRIAL while {@code subscription_status='trialing'} (else PAID), and no
     * current access window resolves to LAPSED. The CHECK constraint on {@code role_override} guarantees the
     * pinned value is a valid role name.
     */
    private Role roleFor(User user) {
        if (user.roleOverride() != null) {
            return Role.valueOf(user.roleOverride());
        }
        final boolean active = user.accessUntil() != null && user.accessUntil().toInstant().isAfter(Instant.now());
        if (!active) {
            return Role.LAPSED;
        }
        return "trialing".equals(user.subscriptionStatus()) ? Role.TRIAL : Role.PAID;
    }

    private Integer showcaseOwnerId() {
        Integer cached = showcaseOwnerId.get();
        if (cached == null) {
            cached = jdbcTemplate.queryForObject("SELECT id FROM users WHERE is_public_showcase", Integer.class);
            showcaseOwnerId.set(cached);
        }
        return cached;
    }
}

package com.sethhaskellcondie.thegamepensieveapi.api.tenant;

import com.sethhaskellcondie.thegamepensieveapi.domain.auth.AccessService;
import com.sethhaskellcondie.thegamepensieveapi.domain.auth.Capability;
import com.sethhaskellcondie.thegamepensieveapi.domain.auth.Role;
import com.sethhaskellcondie.thegamepensieveapi.domain.auth.User;
import com.sethhaskellcondie.thegamepensieveapi.domain.auth.UserRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Resolves the owner id for the current request from the {@link org.springframework.security.core.context.SecurityContext}.
 *
 * <ul>
 *   <li>An {@code X-Showcase: <slug>} request header → that showcase owner's id, GUEST-scoped, for every caller
 *       (resolved by the tenant filter via {@link #resolveShowcase}; an unknown or not-visible slug is a 404).</li>
 *   <li>An authenticated request → the user's id, looked up by the principal's email.</li>
 *   <li>An anonymous request (the default permit-all build, or before login) → the seeded <em>default</em>
 *       showcase owner (the {@code is_public_showcase} row), so guests transparently read the default showcase.</li>
 * </ul>
 *
 * <p>Resolution runs <em>before</em> the request transaction drops to {@code app_rls}, so the {@code users} lookups
 * here execute with the application's normal (superuser) privileges — {@code app_rls} has no grant on {@code users}.
 */
@Component
public class OwnerResolver {

    private final UserRepository userRepository;
    private final JdbcTemplate jdbcTemplate;
    private final AccessService accessService;
    // The default-showcase owner id never changes after V1_13 seeds it; cache the first lookup. (Slug lookups
    // are per-request indexed reads instead — a slug's visibility depends on the owner's current billing state.)
    private final AtomicReference<Integer> showcaseOwnerId = new AtomicReference<>();

    public OwnerResolver(UserRepository userRepository, JdbcTemplate jdbcTemplate, AccessService accessService) {
        this.userRepository = userRepository;
        this.jdbcTemplate = jdbcTemplate;
        this.accessService = accessService;
    }

    /**
     * Resolve the acting owner id and effective {@link Role} for the current request, ignoring impersonation —
     * i.e. always the real authenticated caller. Used where impersonation must not apply, notably the admin
     * control-plane ({@code /v1/admin/**}), so an admin authorizes as themselves even while an
     * {@code X-Act-As-Owner} header is set. Equivalent to {@link #resolveOwner(String) resolveOwner(null)}.
     *
     * <ul>
     *   <li>Authenticated → the user's id and {@link #deriveRole(User) derived role}.</li>
     *   <li>Anonymous → the seeded public showcase owner, always GUEST.</li>
     * </ul>
     */
    public OwnerContext resolveOwner() {
        return resolveCaller();
    }

    /**
     * Resolve the acting owner id and effective {@link Role} for the current request, honoring admin
     * impersonation. Runs <em>before</em> the request transaction drops to {@code app_rls}, so the {@code users}
     * reads here execute with the application's normal (superuser) privileges.
     *
     * <p>When {@code actAsOwnerHeader} names an existing user <em>and</em> the authenticated caller is an ADMIN,
     * the returned context's owner id and role are the <em>impersonated target's</em> — so RLS and the capability
     * matrix scope the whole request to that user (full act-as) — and the real admin is carried as the
     * {@link OwnerContext#impersonator()}. Resolution is intentionally lenient: a missing/blank/non-numeric
     * header, a non-admin caller, or an unknown target id all fall through to the real caller (impersonation is
     * resolved inside the servlet filter, before {@code DispatcherServlet}, where a thrown exception would bypass
     * the JSON error envelope; {@code GET /v1/auth/me} always reflects the actual acting identity so a no-op
     * header is observable by the front end).
     */
    public OwnerContext resolveOwner(String actAsOwnerHeader) {
        final OwnerContext caller = resolveCaller();
        if (actAsOwnerHeader == null || actAsOwnerHeader.isBlank()
                || !accessService.can(caller.role(), Capability.ACCESS_ADMIN)) {
            return caller;
        }
        final Integer targetId;
        try {
            targetId = Integer.valueOf(actAsOwnerHeader.trim());
        } catch (NumberFormatException e) {
            return caller;
        }
        // The caller is an authenticated ADMIN, so the security principal's username is the admin's email —
        // no extra users lookup needed to label the impersonator.
        final String adminEmail = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findById(targetId)
                .map(target -> new OwnerContext(target.id(), deriveRole(target), new Impersonator(caller.ownerId(), adminEmail)))
                .orElse(caller);
    }

    /**
     * Resolve a public showcase view by slug (the {@code X-Showcase} header). Returns a GUEST-scoped context for
     * the showcase owner when the slug names an existing user whose derived role is PAID or ADMIN — hosting a
     * public showcase is a paid capability, so a lapsed (or trial) owner's slug stays reserved in the database
     * but stops resolving until they derive to PAID again (the showcase is a renewal hook). Empty when the slug
     * is unknown or currently not visible; the tenant filter answers both with the same 404.
     *
     * <p>GUEST scoping applies to <em>every</em> caller, including authenticated ones — a showcase view is
     * read+filter only, a separate path from writable admin impersonation ({@code X-Act-As-Owner}).
     */
    public Optional<OwnerContext> resolveShowcase(String slug) {
        return userRepository.findByShowcaseSlug(slug)
                .filter(owner -> {
                    final Role ownerRole = deriveRole(owner);
                    return ownerRole == Role.PAID || ownerRole == Role.ADMIN;
                })
                .map(owner -> OwnerContext.showcase(owner.id()));
    }

    /** The real authenticated caller (or the showcase GUEST when anonymous), never impersonated. */
    private OwnerContext resolveCaller() {
        final Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated()
                && authentication.getPrincipal() instanceof UserDetails userDetails) {
            return userRepository.findByEmail(userDetails.getUsername())
                    .map(user -> new OwnerContext(user.id(), deriveRole(user)))
                    .orElseGet(() -> new OwnerContext(showcaseOwnerId(), Role.GUEST));
        }
        return new OwnerContext(showcaseOwnerId(), Role.GUEST);
    }

    public Integer resolveOwnerId() {
        return resolveOwner().ownerId();
    }

    /**
     * Derive a user's role: an admin {@code role_override} pin wins outright; otherwise a future
     * {@code access_until} resolves to TRIAL while {@code subscription_status='trialing'} (else PAID), and no
     * current access window resolves to LAPSED. The CHECK constraint on {@code role_override} guarantees the
     * pinned value is a valid role name. Pure (reads only the user's fields), so it is reused by the admin API
     * to report each listed user's effective role.
     */
    public Role deriveRole(User user) {
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

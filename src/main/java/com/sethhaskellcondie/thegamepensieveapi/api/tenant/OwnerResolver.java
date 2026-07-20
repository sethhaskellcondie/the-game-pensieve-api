package com.sethhaskellcondie.thegamepensieveapi.api.tenant;

import com.sethhaskellcondie.thegamepensieveapi.domain.auth.AccessService;
import com.sethhaskellcondie.thegamepensieveapi.domain.auth.Capability;
import com.sethhaskellcondie.thegamepensieveapi.domain.auth.Role;
import com.sethhaskellcondie.thegamepensieveapi.domain.auth.User;
import com.sethhaskellcondie.thegamepensieveapi.domain.auth.UserRepository;
import com.sethhaskellcondie.thegamepensieveapi.domain.exceptions.ExceptionForbidden;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Resolves the owner id for the current request from the {@link org.springframework.security.core.context.SecurityContext}.
 *
 * <ul>
 *   <li>An {@code X-Showcase: <slug>} request header → that showcase owner's id, GUEST-scoped, for every caller
 *       (resolved by the tenant filter via {@link #resolveShowcase}; an unknown or not-visible slug is a 404).</li>
 *   <li>An authenticated request → the user's id, resolved from the access token's {@code sub} (claim-by-email
 *       on first login, JIT trial provisioning when no row exists).</li>
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
    private final long trialDays;
    // The default-showcase owner id never changes after V1_13 seeds it; cache the first lookup. (Slug lookups
    // are per-request indexed reads instead — a slug's visibility depends on the owner's current billing state.)
    private final AtomicReference<Integer> showcaseOwnerId = new AtomicReference<>();

    public OwnerResolver(UserRepository userRepository, JdbcTemplate jdbcTemplate, AccessService accessService,
                         @Value("${entitlement.trial-days}") long trialDays) {
        this.userRepository = userRepository;
        this.jdbcTemplate = jdbcTemplate;
        this.accessService = accessService;
        this.trialDays = trialDays;
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
        // The caller is an authenticated ADMIN, so the access token's email claim is the admin's email —
        // no extra users lookup needed to label the impersonator.
        final String adminEmail = currentToken().map(jwt -> jwt.getClaimAsString("email")).orElse(null);
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

    /**
     * The real authenticated caller (or the showcase GUEST when anonymous), never impersonated. Under the secured
     * profile the caller arrives as a Keycloak access token; the owner is resolved by the immutable {@code sub},
     * then — for a seeded row not yet linked — claimed by {@code email} (its {@code sub} stamped on), and finally
     * JIT-provisioned as a fresh 30-day trial if no row exists. All of this runs before the request drops to
     * {@code app_rls}, so these {@code users} reads/writes use the application's normal privileges.
     */
    private OwnerContext resolveCaller() {
        return currentToken()
                .map(this::resolveOrProvision)
                .orElseGet(() -> new OwnerContext(showcaseOwnerId(), Role.GUEST));
    }

    /** The current request's validated access token, when the caller is authenticated via the resource server. */
    private Optional<Jwt> currentToken() {
        final Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated()
                && authentication.getPrincipal() instanceof Jwt jwt) {
            return Optional.of(jwt);
        }
        return Optional.empty();
    }

    /**
     * Resolve the owner row for a validated token, provisioning as needed: by {@code sub}; else claim a seeded row
     * by {@code email} (stamping the {@code sub}); else JIT-create a trial account. The default-showcase owner and
     * the single ADMIN stay seeder-owned — they are always reached by {@code sub}/{@code email}, never JIT-created.
     *
     * <p>Claiming by email requires the token's {@code email_verified} claim: linking an account to a seeded row on
     * an unverified email would let anyone who registers that address at the IdP take the row over. A token with no
     * email claim at all (e.g. minted without the {@code email} scope, or a service account) is rejected with a 403
     * rather than reaching the JIT insert's NOT NULL constraint.
     */
    private OwnerContext resolveOrProvision(Jwt jwt) {
        final String sub = jwt.getSubject();
        final String email = normalizedEmail(jwt);
        final boolean emailVerified = Boolean.TRUE.equals(jwt.getClaim("email_verified"));
        final Optional<User> bySub = userRepository.findBySub(sub);
        if (bySub.isPresent()) {
            syncEmail(bySub.get(), email, emailVerified);
            return new OwnerContext(bySub.get().id(), deriveRole(bySub.get()));
        }
        if (email == null) {
            throw new ExceptionForbidden("The access token carries no email claim, so no account can be resolved "
                    + "or provisioned for it. Request a token that includes the email scope.");
        }
        if (emailVerified) {
            final Optional<User> byEmail = userRepository.findByEmail(email);
            if (byEmail.isPresent()) {
                userRepository.updateSub(byEmail.get().id(), sub);
                return new OwnerContext(byEmail.get().id(), deriveRole(byEmail.get()));
            }
        }
        return new OwnerContext(provisionTrial(email, sub), Role.TRIAL);
    }

    /** The token's email claim, trimmed and lowercased (Keycloak stores emails lowercase), or null when absent. */
    private String normalizedEmail(Jwt jwt) {
        final String email = jwt.getClaimAsString("email");
        if (email == null || email.isBlank()) {
            return null;
        }
        return email.trim().toLowerCase();
    }

    /**
     * Keep {@code users.email} in step with the IdP: when a sub-linked account presents a different (verified)
     * email, the user changed their address in Keycloak — mirror it. A conflict with another row's email keeps the
     * stored address rather than failing the request ({@code email} is UNIQUE).
     */
    private void syncEmail(User user, String email, boolean emailVerified) {
        if (!emailVerified || email == null || email.equalsIgnoreCase(user.email())) {
            return;
        }
        try {
            userRepository.updateEmail(user.id(), email);
        } catch (DuplicateKeyException conflict) {
            // Another account already holds this address; leave the stored email until the conflict is resolved.
        }
    }

    /**
     * JIT-provision a new trial account for a first-seen Keycloak identity and return its owner id. Two concurrent
     * first requests can race to insert; the loser hits the {@code keycloak_sub} UNIQUE constraint, so fall back to
     * the row the winner created. A duplicate on {@code email} instead means a row with this address exists but is
     * linked to a different (or no) Keycloak account and the verified-email claim path did not apply — refuse
     * rather than hijack the row or surface a raw constraint violation.
     */
    private Integer provisionTrial(String email, String sub) {
        final Timestamp trialAccessUntil = Timestamp.from(Instant.now().plus(trialDays, ChronoUnit.DAYS));
        try {
            return userRepository.insertJit(email, sub, trialAccessUntil, "trialing");
        } catch (DuplicateKeyException raced) {
            final Optional<User> winner = userRepository.findBySub(sub);
            if (winner.isPresent()) {
                return winner.get().id();
            }
            throw new ExceptionForbidden("An account with this email already exists but is not linked to this "
                    + "login. Verify the email on the login account, or contact the administrator.");
        }
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

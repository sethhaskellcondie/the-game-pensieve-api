package com.sethhaskellcondie.thegamepensieveapi.api.tenant;

import com.sethhaskellcondie.thegamepensieveapi.domain.auth.Role;

/**
 * The owner id and effective {@link Role} for a request, resolved together from a single {@code users} lookup
 * by {@link OwnerResolver} — while the connection still has normal privileges, before the per-request demotion
 * to {@code app_rls} (which has no grant on {@code users}).
 *
 * <p>{@code ownerId} and {@code role} are the <em>acting</em> identity: ordinarily the authenticated caller's
 * own, but when an admin impersonates another user (via the {@code X-Act-As-Owner} header) they are the
 * impersonated target's, so RLS and the capability matrix scope the request to that user (full act-as). In that
 * case {@code impersonator} carries the real admin behind the request; it is {@code null} for a normal request.
 *
 * <p>{@code showcase} is true only for an {@code X-Showcase} public showcase view (see {@link #showcase(Integer)}).
 * It is a distinct signal from {@code role == GUEST}: the default permit-all build resolves every anonymous
 * request to GUEST too, but only a genuine showcase view has {@code showcase} set — so read overrides keyed on it
 * never touch the single-user build's own settings.
 */
public record OwnerContext(Integer ownerId, Role role, Impersonator impersonator, boolean showcase) {

    /** A normal (non-impersonated) request: the acting identity is the caller's own. */
    public OwnerContext(Integer ownerId, Role role) {
        this(ownerId, role, null, false);
    }

    /** An impersonated request: {@code impersonator} carries the real admin behind the acting target. */
    public OwnerContext(Integer ownerId, Role role, Impersonator impersonator) {
        this(ownerId, role, impersonator, false);
    }

    /** A public showcase (GUEST, {@code X-Showcase}) view scoped to the showcase owner. */
    public static OwnerContext showcase(Integer ownerId) {
        return new OwnerContext(ownerId, Role.GUEST, null, true);
    }
}

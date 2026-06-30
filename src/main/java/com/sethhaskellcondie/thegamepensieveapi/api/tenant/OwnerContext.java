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
 */
public record OwnerContext(Integer ownerId, Role role, Impersonator impersonator) {

    /** A normal (non-impersonated) request: the acting identity is the caller's own. */
    public OwnerContext(Integer ownerId, Role role) {
        this(ownerId, role, null);
    }
}

package com.sethhaskellcondie.thegamepensieveapi.api.tenant;

import com.sethhaskellcondie.thegamepensieveapi.domain.auth.Role;

/**
 * The owner id and effective {@link Role} for a request, resolved together from a single {@code users} lookup
 * by {@link OwnerResolver} — while the connection still has normal privileges, before the per-request demotion
 * to {@code app_rls} (which has no grant on {@code users}).
 */
public record OwnerContext(Integer ownerId, Role role) {
}

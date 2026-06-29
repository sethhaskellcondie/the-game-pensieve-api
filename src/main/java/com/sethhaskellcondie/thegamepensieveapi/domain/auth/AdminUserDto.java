package com.sethhaskellcondie.thegamepensieveapi.domain.auth;

import java.sql.Timestamp;

/**
 * A user account as exposed to the admin role-management API. {@code role} is the user's effective per-request
 * role ({@link com.sethhaskellcondie.thegamepensieveapi.api.tenant.OwnerResolver#deriveRole derived} the same
 * way a live request resolves it, so it already reflects any {@code roleOverride} pin); {@code roleOverride} and
 * the billing fields are surfaced alongside so an admin can see why a user resolves the way it does and what
 * clearing the pin would fall back to.
 */
public record AdminUserDto(
        Integer id,
        String email,
        Role role,
        String roleOverride,
        Timestamp accessUntil,
        String subscriptionStatus
) {
}

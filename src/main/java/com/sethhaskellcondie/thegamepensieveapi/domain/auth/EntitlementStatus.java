package com.sethhaskellcondie.thegamepensieveapi.domain.auth;

/**
 * The effective access tier for a request, resolved per-request from backend entitlement fields (never from
 * Paddle). See the Phase 3 access model:
 * <ul>
 *   <li>{@code GUEST} — an anonymous request, scoped to the public showcase owner.</li>
 *   <li>{@code PAID} — an authenticated request whose access window ({@code access_until}) is in the future.</li>
 *   <li>{@code LAPSED} — an authenticated request with no current access window.</li>
 * </ul>
 */
public enum EntitlementStatus {
    GUEST,
    PAID,
    LAPSED
}

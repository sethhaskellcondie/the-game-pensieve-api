package com.sethhaskellcondie.thegamepensieveapi.domain.auth;

/**
 * The effective role for a request, resolved per-request by {@code OwnerResolver} from backend fields (never
 * from Paddle): an admin {@code role_override} pin if present, otherwise derived from {@code access_until} /
 * {@code subscription_status}. The role is stashed in {@code TenantContext} <em>before</em> the connection drops
 * to {@code app_rls}, and what each role may do is centralized in the {@link AccessService} capability matrix.
 * <ul>
 *   <li>{@code GUEST} — an anonymous request, scoped to the public showcase owner.</li>
 *   <li>{@code TRIAL} — an authenticated request in its trial window ({@code subscription_status='trialing'}).</li>
 *   <li>{@code PAID} — an authenticated request whose access window ({@code access_until}) is in the future.</li>
 *   <li>{@code LAPSED} — an authenticated request with no current access window.</li>
 *   <li>{@code ADMIN} — an authenticated request pinned to the admin role via {@code role_override}.</li>
 * </ul>
 */
public enum Role {
    GUEST,
    TRIAL,
    PAID,
    LAPSED,
    ADMIN
}

package com.sethhaskellcondie.thegamepensieveapi.domain.auth;

/**
 * The current caller's identity and effective {@link Role}, returned by {@code GET /v1/auth/me}. The role is
 * resolved per-request the same way every other request resolves it (via {@code OwnerResolver}), so the front
 * end can read the caller's effective role without re-deriving it from billing state. Served on the auth path,
 * which bypasses the tenant filter, so the {@code users} read runs with the application's normal privileges.
 *
 * <p>{@code accessUntil} is the caller's access-window expiry as <em>epoch milliseconds</em> (the same
 * {@code access_until} that drives the TRIAL/PAID/LAPSED derivation), surfaced so the front end can show how long
 * the plan stays active. It is {@code null} when the account has no window (e.g. an admin-pinned role). It is an
 * explicit {@code Long} (not a raw {@code Timestamp}) so the wire value is an unambiguous numeric epoch, rather
 * than depending on Jackson's date serialization.
 *
 * <p>When an admin is impersonating another user (via the {@code X-Act-As-Owner} header), the primary
 * {@code id}/{@code email}/{@code role}/{@code accessUntil} fields are the <em>admin's</em> (role always
 * {@code ADMIN}) and {@link #impersonating} carries the target user being acted as — so the front end can render
 * the target's view while knowing an admin is driving. For a normal request {@code impersonating} is {@code null}.
 */
public record MeResponseDto(Integer id, String email, Role role, Long accessUntil, Impersonation impersonating) {

    /** A normal (non-impersonated) caller. */
    public MeResponseDto(Integer id, String email, Role role, Long accessUntil) {
        this(id, email, role, accessUntil, null);
    }
}
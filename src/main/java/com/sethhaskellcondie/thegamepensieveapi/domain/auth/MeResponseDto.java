package com.sethhaskellcondie.thegamepensieveapi.domain.auth;

/**
 * The current caller's identity and effective {@link Role}, returned by {@code GET /v1/auth/me}. The role is
 * resolved per-request the same way every other request resolves it (via {@code OwnerResolver}), so the front
 * end can read the caller's effective role without re-deriving it from billing state. Served on the auth path,
 * which bypasses the tenant filter, so the {@code users} read runs with the application's normal privileges.
 *
 * <p>When an admin is impersonating another user (via the {@code X-Act-As-Owner} header), the primary
 * {@code id}/{@code email}/{@code role} fields are the <em>admin's</em> (always {@code ADMIN}) and
 * {@link #impersonating} carries the target user being acted as — so the front end can render the target's view
 * while knowing an admin is driving. For a normal request {@code impersonating} is {@code null}.
 */
public record MeResponseDto(Integer id, String email, Role role, Impersonation impersonating) {

    /** A normal (non-impersonated) caller. */
    public MeResponseDto(Integer id, String email, Role role) {
        this(id, email, role, null);
    }
}
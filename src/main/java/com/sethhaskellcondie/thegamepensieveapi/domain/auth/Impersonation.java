package com.sethhaskellcondie.thegamepensieveapi.domain.auth;

/**
 * The user an admin is currently impersonating, nested in {@link MeResponseDto} as the {@code impersonating}
 * marker. Carries the target's identity and effective {@link Role} (what that user sees in the app), while the
 * enclosing {@code MeResponseDto} reports the admin as the primary identity. {@code null} on the response when the
 * caller is not impersonating.
 */
public record Impersonation(Integer id, String email, Role role) {
}

package com.sethhaskellcondie.thegamepensieveapi.domain.auth;

/**
 * Body of {@code POST /v1/admin/users/{id}/role}. {@code roleOverride} is the role to pin the target user to —
 * one of the five role names ({@code GUEST/TRIAL/PAID/LAPSED/ADMIN}) — or {@code null} to clear the pin and
 * revert the user to auto-derivation from their billing fields.
 */
public record SetRoleOverrideRequestDto(String roleOverride) {
}

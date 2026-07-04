package com.sethhaskellcondie.thegamepensieveapi.domain.auth;

/**
 * Body of {@code POST /v1/admin/users/{id}/showcase}. {@code slug} is the public address to grant — lowercase
 * alphanumerics with single interior hyphens ({@code ^[a-z0-9](-?[a-z0-9])*$}) — or {@code null}/blank to clear
 * the grant (which also clears the name). {@code name} is the display title shown by the public directory in
 * place of the owner's email.
 */
public record SetShowcaseRequestDto(String slug, String name) {
}

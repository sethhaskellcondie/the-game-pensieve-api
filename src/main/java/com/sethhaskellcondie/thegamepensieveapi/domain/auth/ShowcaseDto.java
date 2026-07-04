package com.sethhaskellcondie.thegamepensieveapi.domain.auth;

/**
 * One public showcase as listed by {@code GET /v1/showcases}: the slug (viewers pass it in the
 * {@code X-Showcase} header) and its display name. Deliberately excludes the owner's email and every other
 * account field — the directory is a public, anonymous endpoint.
 */
public record ShowcaseDto(String slug, String name) {
}

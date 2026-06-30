package com.sethhaskellcondie.thegamepensieveapi.api.tenant;

/**
 * The real ADMIN behind an impersonated request. Present on an {@link OwnerContext} only while an admin is acting
 * as another user (via the {@code X-Act-As-Owner} header); {@code null} otherwise. The acting owner id and role on
 * the {@link OwnerContext} are the <em>impersonated target's</em> — so Row-Level Security and the capability matrix
 * scope the request to that user (full act-as) — while this record preserves who is really driving, so
 * {@code GET /v1/auth/me} can report the admin as the primary identity with the target as an {@code impersonating}
 * marker.
 */
public record Impersonator(Integer adminId, String adminEmail) {
}

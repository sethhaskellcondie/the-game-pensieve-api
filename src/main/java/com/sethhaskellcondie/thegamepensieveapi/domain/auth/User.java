package com.sethhaskellcondie.thegamepensieveapi.domain.auth;

import java.sql.Timestamp;

/**
 * A user account row. This is a lightweight, security-sensitive model that is intentionally kept out of the
 * generic Entity/Gateway/custom-field stack used by the catalog entities.
 *
 * <p>The billing fields ({@code plan}, {@code subscriptionStatus}, {@code accessUntil}, and the Paddle ids)
 * feed the role derivation: a request derives to TRIAL/PAID while {@code accessUntil} is in the future (which
 * both a purchase and a trial set), else LAPSED; {@code plan} is informational and reconciled with Paddle later.
 * {@code roleOverride}, when non-null, is an admin pin that overrides that derivation outright.
 *
 * <p>A non-null {@code showcaseSlug} publishes the user's collection as a public showcase at that address
 * (resolved from the {@code X-Showcase} request header while the owner derives to PAID or ADMIN);
 * {@code showcaseName} is its display title for the public directory.
 *
 * <p>{@code keycloakSub} is the immutable Keycloak subject claim, the stable link between an access token and
 * this row. It is null on a seeded row until the owner's first login claims it (claim-on-first-login in
 * {@code OwnerResolver}); {@code passwordHash} is a legacy column, now unused (passwords live in Keycloak).
 */
public record User(
        Integer id,
        String email,
        String keycloakSub,
        String passwordHash,
        boolean enabled,
        Timestamp createdAt,
        Timestamp updatedAt,
        String plan,
        String subscriptionStatus,
        Timestamp accessUntil,
        String paddleCustomerId,
        String paddleSubscriptionId,
        String lastEventId,
        String roleOverride,
        String showcaseSlug,
        String showcaseName
) {
}

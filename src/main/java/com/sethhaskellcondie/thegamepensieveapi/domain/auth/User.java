package com.sethhaskellcondie.thegamepensieveapi.domain.auth;

import java.sql.Timestamp;

/**
 * A user account row. This is a lightweight, security-sensitive model that is intentionally kept out of the
 * generic Entity/Gateway/custom-field stack used by the catalog entities.
 *
 * <p>The entitlement fields ({@code plan}, {@code subscriptionStatus}, {@code accessUntil}, and the Paddle
 * ids) drive the Phase 3 access model. Effective access is gated by {@code accessUntil} alone — a request is
 * PAID while it is in the future (which both a purchase and a trial set); {@code plan}/{@code subscriptionStatus}
 * are informational and reconciled with Paddle later.
 */
public record User(
        Integer id,
        String email,
        String passwordHash,
        boolean enabled,
        Timestamp createdAt,
        Timestamp updatedAt,
        String plan,
        String subscriptionStatus,
        Timestamp accessUntil,
        String paddleCustomerId,
        String paddleSubscriptionId,
        String lastEventId
) {
}

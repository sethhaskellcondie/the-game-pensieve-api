package com.sethhaskellcondie.thegamepensieveapi.domain.auth;

import java.sql.Timestamp;

/**
 * A user account row. This is a lightweight, security-sensitive model that is intentionally kept out of the
 * generic Entity/Gateway/custom-field stack used by the catalog entities.
 */
public record User(
        Integer id,
        String email,
        String passwordHash,
        boolean enabled,
        Timestamp createdAt,
        Timestamp updatedAt
) {
}

package com.sethhaskellcondie.thegamepensieveapi.domain.auth;

import java.sql.Timestamp;

public record RefreshToken(int id, int userId, String tokenHash, Timestamp expiresAt, boolean revoked) {
}

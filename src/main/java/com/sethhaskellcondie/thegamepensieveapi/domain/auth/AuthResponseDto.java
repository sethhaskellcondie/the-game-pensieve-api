package com.sethhaskellcondie.thegamepensieveapi.domain.auth;

public record AuthResponseDto(String accessToken, String refreshToken, String tokenType, long expiresInMs) {
}

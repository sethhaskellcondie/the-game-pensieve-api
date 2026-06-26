package com.sethhaskellcondie.thegamepensieveapi.api.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * Mints and verifies the self-issued, short-lived HS256 access tokens. The signing secret and access-token
 * lifetime are externalized via configuration so they can be overridden per environment.
 */
@Service
public class JwtService {

    private final SecretKey signingKey;
    private final long accessTokenExpirationMs;

    public JwtService(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.access-token-expiration}") long accessTokenExpirationMs
    ) {
        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.accessTokenExpirationMs = accessTokenExpirationMs;
    }

    public long getAccessTokenExpirationMs() {
        return accessTokenExpirationMs;
    }

    public String generateAccessToken(String subject) {
        final long now = System.currentTimeMillis();
        return Jwts.builder()
                .subject(subject)
                .issuedAt(new Date(now))
                .expiration(new Date(now + accessTokenExpirationMs))
                .signWith(signingKey)
                .compact();
    }

    /**
     * Verify the signature and expiry and return the subject (the user's email). Throws a JwtException
     * (or IllegalArgumentException for a blank token) if the token is invalid; callers treat that as unauthenticated.
     */
    public String extractSubject(String token) {
        final Claims claims = Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
        return claims.getSubject();
    }
}

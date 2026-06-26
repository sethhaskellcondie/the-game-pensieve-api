package com.sethhaskellcondie.thegamepensieveapi.domain.auth;

import com.sethhaskellcondie.thegamepensieveapi.api.security.JwtService;
import com.sethhaskellcondie.thegamepensieveapi.domain.exceptions.ExceptionFailedDbValidation;
import com.sethhaskellcondie.thegamepensieveapi.domain.exceptions.ExceptionInputValidation;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Owns the register/login/refresh logic for the lightweight auth model. Registration is idempotent (a repeated
 * email is rejected, mirroring the project's duplicationCheck convention). Login issues a short-lived access
 * JWT plus a DB-backed refresh token; refresh rotates that token (the presented one is revoked and a new one
 * issued) so a single refresh token can never be replayed.
 */
@Service
public class AuthService {

    private static final String TOKEN_TYPE = "Bearer";

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final long refreshTokenExpirationMs;
    private final SecureRandom secureRandom = new SecureRandom();

    public AuthService(
            UserRepository userRepository,
            RefreshTokenRepository refreshTokenRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            @Value("${jwt.refresh-token-expiration}") long refreshTokenExpirationMs
    ) {
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.refreshTokenExpirationMs = refreshTokenExpirationMs;
    }

    public RegisterResponseDto register(RegisterRequestDto requestDto) throws ExceptionFailedDbValidation {
        final String email = requestDto.email() == null ? null : requestDto.email().trim();
        if (email == null || email.isBlank()) {
            throw new ExceptionInputValidation("Registration failed, an email is required.");
        }
        if (requestDto.password() == null || requestDto.password().isBlank()) {
            throw new ExceptionInputValidation("Registration failed, a password is required.");
        }
        if (userRepository.existsByEmail(email)) {
            throw new ExceptionFailedDbValidation("Registration failed, an account with email: '" + email + "' already exists.");
        }
        final int id = userRepository.insert(email, passwordEncoder.encode(requestDto.password()));
        return new RegisterResponseDto(id, email);
    }

    public AuthResponseDto login(LoginRequestDto requestDto) {
        final String email = requestDto.email() == null ? null : requestDto.email().trim();
        final User user = userRepository.findByEmail(email == null ? "" : email)
                .orElseThrow(() -> new BadCredentialsException("Invalid email or password."));
        if (!user.enabled() || !passwordEncoder.matches(requestDto.password(), user.passwordHash())) {
            throw new BadCredentialsException("Invalid email or password.");
        }
        final String accessToken = jwtService.generateAccessToken(user.email());
        final String refreshToken = issueRefreshToken(user.id());
        return new AuthResponseDto(accessToken, refreshToken, TOKEN_TYPE, jwtService.getAccessTokenExpirationMs());
    }

    public AuthResponseDto refresh(RefreshRequestDto requestDto) {
        if (requestDto.refreshToken() == null || requestDto.refreshToken().isBlank()) {
            throw new BadCredentialsException("Invalid refresh token.");
        }
        final RefreshToken stored = refreshTokenRepository.findActiveByHash(hashToken(requestDto.refreshToken()))
                .orElseThrow(() -> new BadCredentialsException("Invalid refresh token."));
        final User user = userRepository.findById(stored.userId())
                .orElseThrow(() -> new BadCredentialsException("Invalid refresh token."));
        // Rotate: the presented token is single-use, so revoke it and mint a fresh pair.
        refreshTokenRepository.revokeById(stored.id());
        final String accessToken = jwtService.generateAccessToken(user.email());
        final String refreshToken = issueRefreshToken(user.id());
        return new AuthResponseDto(accessToken, refreshToken, TOKEN_TYPE, jwtService.getAccessTokenExpirationMs());
    }

    private String issueRefreshToken(int userId) {
        final byte[] randomBytes = new byte[32];
        secureRandom.nextBytes(randomBytes);
        final String token = Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
        final Timestamp expiresAt = Timestamp.from(Instant.now().plusMillis(refreshTokenExpirationMs));
        refreshTokenRepository.insert(userId, hashToken(token), expiresAt);
        return token;
    }

    private String hashToken(String token) {
        try {
            final MessageDigest digest = MessageDigest.getInstance("SHA-256");
            final byte[] hash = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm is unavailable.", e);
        }
    }
}

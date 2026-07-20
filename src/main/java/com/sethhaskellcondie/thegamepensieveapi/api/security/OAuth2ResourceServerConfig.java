package com.sethhaskellcondie.thegamepensieveapi.api.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

/**
 * Builds the {@link JwtDecoder} the secured chain uses to validate Keycloak RS256 access tokens. Deliberately
 * constructed from a JWKS URI plus an explicit issuer validator rather than {@code issuer-uri} discovery: the API
 * runs on the private compose network and cannot reach the host-facing issuer URL ({@code localhost:8081}) to run
 * OIDC discovery, so keys are fetched over the internal network ({@code keycloak:8080}) while {@code iss} is still
 * validated against the canonical host-facing issuer that Keycloak stamps into the token. Adds audience validation
 * (the shared {@code /mcp} URI) on top of the default timestamp check.
 */
@Configuration
@Profile("secured")
public class OAuth2ResourceServerConfig {

    private final String issuer;
    private final String jwkSetUri;
    private final String audience;

    public OAuth2ResourceServerConfig(
            @Value("${pensieve.oauth2.issuer}") String issuer,
            @Value("${pensieve.oauth2.jwk-set-uri}") String jwkSetUri,
            @Value("${pensieve.oauth2.audience}") String audience
    ) {
        this.issuer = issuer;
        this.jwkSetUri = jwkSetUri;
        this.audience = audience;
    }

    @Bean
    public JwtDecoder jwtDecoder() {
        final NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(jwkSetUri).build();
        final OAuth2TokenValidator<Jwt> validators = new DelegatingOAuth2TokenValidator<>(
                new JwtTimestampValidator(),
                new JwtIssuerValidator(issuer),
                new AudienceValidator(audience));
        decoder.setJwtValidator(validators);
        return decoder;
    }
}

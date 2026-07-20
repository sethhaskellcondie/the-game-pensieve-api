package com.sethhaskellcondie.thegamepensieveapi;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Mix-in base for the {@code secured}-profile test suites: it points the OAuth2 resource-server config at the
 * shared Keycloak Testcontainer ({@link KeycloakTestSupport}) so the app validates the very tokens those tests
 * mint. Subclasses keep their own {@code @SpringBootTest}/{@code @ActiveProfiles}/{@code @AutoConfigureMockMvc}
 * (the datasource profile differs — {@code test-container} vs {@code seeded-tests}); {@code @DynamicPropertySource}
 * is inherited from this superclass regardless.
 *
 * <p>The issuer and JWKS URI are the container's dynamic host URL (both host-reachable in tests — no split, unlike
 * compose where the container fetches keys over the internal network); the audience stays the fixed {@code /mcp}
 * URL from {@code application-secured.properties}. The container is a JVM-wide singleton, so Keycloak starts once
 * for the whole run.
 */
public abstract class SecuredProfileTest {

    @DynamicPropertySource
    static void resourceServerProperties(DynamicPropertyRegistry registry) {
        registry.add("pensieve.oauth2.issuer", KeycloakTestSupport::issuerUri);
        registry.add("pensieve.oauth2.jwk-set-uri", KeycloakTestSupport::jwksUri);
    }
}

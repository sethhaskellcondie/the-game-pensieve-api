package com.sethhaskellcondie.thegamepensieveapi.controllers;

import com.sethhaskellcondie.thegamepensieveapi.KeycloakTestSupport;
import com.sethhaskellcondie.thegamepensieveapi.SecuredProfileTest;
import com.sethhaskellcondie.thegamepensieveapi.TestFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Exercises the authenticated {@code secured} build now that Keycloak is the authorization server: unauthenticated
 * or bad-token protected routes return 401, the public carve-outs (heartbeat) stay open, a real Keycloak RS256
 * Bearer grants access, and {@code /v1/auth/me} reports the caller — JIT-provisioning a trial {@code users} row on
 * first login (login/registration/refresh themselves now live in Keycloak, not this API).
 * <p>
 * Activating the {@code secured} profile alongside {@code test-container} is how the authenticated build is
 * exercised in tests; it is activated in production via {@code SPRING_PROFILES_ACTIVE=secured}.
 * <p>
 * Counterpart: {@link AuthDefaultProfileTests} pins the default permit-all build.
 */
@SpringBootTest
@ActiveProfiles({"test-container", "secured"})
@AutoConfigureMockMvc
public class AuthSecuredProfileTests extends SecuredProfileTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    private TestFactory factory;
    private static final String PASSWORD = "Sup3rSecret!";

    @BeforeEach
    void setUp() {
        factory = new TestFactory(mockMvc);
    }

    private ResultActions searchSystems(String bearerToken) throws Exception {
        var request = post("/v1/systems/function/search")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"filters\": []}");
        if (bearerToken != null) {
            request = request.header("Authorization", "Bearer " + bearerToken);
        }
        return mockMvc.perform(request);
    }

    // A write route is protected under the secured profile (the showcase read surface is now public).
    private ResultActions createSystem(String bearerToken) throws Exception {
        var request = post("/v1/systems")
                .contentType(MediaType.APPLICATION_JSON)
                .content(factory.formatSystemPayload("Sys-" + java.util.UUID.randomUUID().toString().substring(0, 8), 1, false, null));
        if (bearerToken != null) {
            request = request.header("Authorization", "Bearer " + bearerToken);
        }
        return mockMvc.perform(request);
    }

    private ResultActions getMe(String bearerToken) throws Exception {
        var request = get("/v1/auth/me");
        if (bearerToken != null) {
            request = request.header("Authorization", "Bearer " + bearerToken);
        }
        return mockMvc.perform(request);
    }

    // --- Public carve-outs: still reachable without a token under the secured profile ---

    @Test
    void getHeartbeat_NoAuth_Secured_ReturnsOk() throws Exception {
        mockMvc.perform(get("/v1/heartbeat"))
                .andExpectAll(
                        status().isOk(),
                        jsonPath("$.data.message").value("thump thump"),
                        jsonPath("$.data.secureMode").value(true));
    }

    // --- Protected (write) routes require authentication ---

    @Test
    void getProtectedRoute_NoAuth_Secured_Returns401() throws Exception {
        createSystem(null).andExpect(status().isUnauthorized());
    }

    @Test
    void protectedRoute_InvalidToken_Returns401() throws Exception {
        createSystem("not-a-real-jwt").andExpect(status().isUnauthorized());
    }

    @Test
    void protectedRoute_WithBearerToken_ReturnsOk() throws Exception {
        final String accessToken = factory.tokenFor(factory.randomEmail(), PASSWORD);

        searchSystems(accessToken).andExpect(status().isOk());
    }

    // --- Current user (/v1/auth/me) ---

    @Test
    void getMe_NoAuth_Secured_Returns401() throws Exception {
        getMe(null).andExpect(status().isUnauthorized());
    }

    // --- Provisioning guards: tokens no account can be resolved or provisioned for ---

    @Test
    void tokenWithoutEmailClaim_Returns403OnBothPaths() throws Exception {
        // A service-account token is valid (sub, aud, scope) but carries no email claim. It authenticates, but no
        // users row can be resolved or provisioned, so both the auth path (/v1/auth/me, via the controller
        // advice) and the tenant-filter path (an entity route, via the filter's hand-written envelope) answer 403.
        final String accessToken = KeycloakTestSupport.serviceAccountToken(
                "svc-" + UUID.randomUUID().toString().substring(0, 8), PASSWORD);

        getMe(accessToken).andExpectAll(
                status().isForbidden(),
                jsonPath("$.errors").isNotEmpty());
        searchSystems(accessToken).andExpectAll(
                status().isForbidden(),
                jsonPath("$.errors").isNotEmpty());
    }

    @Test
    void tokenWithUnverifiedEmail_MatchingExistingAccount_DoesNotClaimIt() throws Exception {
        // Claim-by-email requires email_verified: an unverified address matching an existing (sub-less) row must
        // not take the row over, and the JIT insert then collides on the UNIQUE email — the request is refused.
        final String email = factory.randomEmail();
        jdbcTemplate.update("INSERT INTO users(email, enabled, access_until, subscription_status, created_at, updated_at) "
                + "VALUES (?, true, now() + interval '30 days', 'trialing', now(), now())", email);
        KeycloakTestSupport.ensureUser(email, PASSWORD, false);
        final String accessToken = KeycloakTestSupport.passwordGrant(email, PASSWORD);

        getMe(accessToken).andExpect(status().isForbidden());
        assertNull(jdbcTemplate.queryForObject("SELECT keycloak_sub FROM users WHERE email = ?", String.class, email),
                "an unverified email must not claim an existing account row");
    }

    @Test
    void verifiedEmailChangedInKeycloak_SyncsOntoUsersRow() throws Exception {
        // users.email mirrors the IdP: once a row is linked by sub, a login after an email change at Keycloak
        // updates the stored address.
        final String email = factory.randomEmail();
        factory.tokenForProvisioned(email, PASSWORD);

        final String newEmail = factory.randomEmail();
        KeycloakTestSupport.updateUserEmail(email, newEmail);
        // The direct-access grant authenticates by username, which remains the original email.
        final String refreshedToken = KeycloakTestSupport.passwordGrant(email, PASSWORD);

        getMe(refreshedToken).andExpectAll(
                status().isOk(),
                jsonPath("$.data.email").value(newEmail));
    }

    @Test
    void getMe_WithBearerToken_JitProvisionsTrialAndReturnsIdentity() throws Exception {
        final String email = factory.randomEmail();
        final String accessToken = factory.tokenFor(email, PASSWORD);

        // A first-seen Keycloak identity is JIT-provisioned with a trial window (subscription_status='trialing'),
        // so it resolves to TRIAL and carries a non-null accessUntil (the trial expiry, epoch milliseconds).
        getMe(accessToken).andExpectAll(
                status().isOk(),
                jsonPath("$.data.email").value(email),
                jsonPath("$.data.id").isNumber(),
                jsonPath("$.data.role").value("TRIAL"),
                jsonPath("$.data.accessUntil").isNumber(),
                jsonPath("$.errors").isEmpty()
        );
    }
}

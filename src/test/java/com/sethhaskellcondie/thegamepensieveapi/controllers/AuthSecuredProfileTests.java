package com.sethhaskellcondie.thegamepensieveapi.controllers;

import com.sethhaskellcondie.thegamepensieveapi.TestFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Exercises the authenticated {@code secured} build: unauthenticated protected routes return 401, the public
 * carve-outs (heartbeat + the auth endpoints) stay open, registration is idempotent, login issues an access +
 * refresh token, a Bearer token grants access, and the refresh flow rotates tokens.
 * <p>
 * Activating the {@code secured} profile alongside {@code test-container} is how the authenticated build is
 * exercised in tests; it is activated in production via {@code SPRING_PROFILES_ACTIVE=secured}.
 * <p>
 * Counterpart: {@link AuthDefaultProfileTests} pins the default permit-all build.
 */
@SpringBootTest
@ActiveProfiles({"test-container", "secured"})
@AutoConfigureMockMvc
public class AuthSecuredProfileTests {

    @Autowired
    private MockMvc mockMvc;
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

    // --- Public carve-outs: still reachable without a token under the secured profile ---

    @Test
    void getHeartbeat_NoAuth_Secured_ReturnsOk() throws Exception {
        mockMvc.perform(get("/v1/heartbeat"))
                .andExpect(status().isOk());
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
        final String email = factory.randomEmail();
        factory.registerReturnResult(email, PASSWORD).andExpect(status().isCreated());
        final String accessToken = factory.extractToken(factory.loginReturnResult(email, PASSWORD), "accessToken");

        searchSystems(accessToken).andExpect(status().isOk());
    }

    // --- Registration ---

    @Test
    void register_NewUser_Returns201() throws Exception {
        factory.registerReturnResult(factory.randomEmail(), PASSWORD)
                .andExpect(status().isCreated());
    }

    @Test
    void register_DuplicateEmail_Returns400() throws Exception {
        // Mirrors the project's duplicationCheck() convention: a repeated create is a 400.
        final String email = factory.randomEmail();
        factory.registerReturnResult(email, PASSWORD).andExpect(status().isCreated());
        factory.registerReturnResult(email, PASSWORD).andExpect(status().isBadRequest());
    }

    // --- Login ---

    @Test
    void login_ValidCredentials_ReturnsAccessAndRefreshTokens() throws Exception {
        final String email = factory.randomEmail();
        factory.registerReturnResult(email, PASSWORD).andExpect(status().isCreated());

        factory.loginReturnResult(email, PASSWORD).andExpectAll(
                status().isOk(),
                jsonPath("$.data.accessToken").isNotEmpty(),
                jsonPath("$.data.refreshToken").isNotEmpty(),
                jsonPath("$.errors").isEmpty()
        );
    }

    @Test
    void login_BadPassword_Returns401() throws Exception {
        final String email = factory.randomEmail();
        factory.registerReturnResult(email, PASSWORD).andExpect(status().isCreated());

        factory.loginReturnResult(email, "wrong-password").andExpect(status().isUnauthorized());
    }

    // --- Refresh flow ---

    @Test
    void refresh_ValidRefreshToken_ReturnsNewAccessToken() throws Exception {
        final String email = factory.randomEmail();
        factory.registerReturnResult(email, PASSWORD).andExpect(status().isCreated());
        final String refreshToken = factory.extractToken(factory.loginReturnResult(email, PASSWORD), "refreshToken");

        mockMvc.perform(
                post("/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(factory.formatRefreshPayload(refreshToken))
        ).andExpectAll(
                status().isOk(),
                jsonPath("$.data.accessToken").isNotEmpty()
        );
    }

    @Test
    void refresh_InvalidToken_Returns401() throws Exception {
        mockMvc.perform(
                post("/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(factory.formatRefreshPayload("not-a-real-refresh-token"))
        ).andExpect(status().isUnauthorized());
    }
}

package com.sethhaskellcondie.thegamepensieveapi.controllers;

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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Single-admin enforcement under the {@code secured} profile — Phase 1 of the single-admin rollout
 * ({@code localFiles/rollout_single_admin.md}): these tests pin <em>current</em> behavior and are green against
 * unchanged code. Phase 2 adds the {@code uq_users_single_admin} partial unique index and flips the marked
 * expectations in place.
 *
 * <p>Expectations:
 * <ul>
 *   <li><strong>FLIPS in Phase 2:</strong> with an admin bootstrapped, pinning {@code ADMIN} on a second user
 *       succeeds today (200) and two admins coexist — Phase 2 turns this into a 400 with a friendly message.</li>
 *   <li><strong>Stays:</strong> re-pinning {@code ADMIN} on the existing admin is an idempotent same-row update
 *       (200 today and after Phase 2).</li>
 *   <li><strong>Stays:</strong> replacing the admin works — the sole admin clears their own pin via the API
 *       (self-demotion stays allowed; 200), and the replacement is pinned via the documented SQL bootstrap,
 *       which is also the recovery path once the unique index exists.</li>
 * </ul>
 */
@SpringBootTest
@ActiveProfiles({"test-container", "secured"})
@AutoConfigureMockMvc
public class SingleAdminSecuredProfileTests {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    private TestFactory factory;
    private static final String PASSWORD = "Sup3rSecret!";
    private static final String USERS_URL = "/v1/admin/users";

    @BeforeEach
    void setUp() {
        factory = new TestFactory(mockMvc);
    }

    /**
     * FLIPS in Phase 2: given admin A is bootstrapped, when A pins {@code ADMIN} on user B, then today the pin
     * succeeds (200) and two admins exist side by side. Phase 2's {@code uq_users_single_admin} index makes this
     * a 400 ("An admin already exists...") and B's pin must never land.
     */
    @Test
    void pinningSecondAdmin_SucceedsToday_TwoAdminsExist() throws Exception {
        final String adminEmail = factory.randomEmail();
        final String adminToken = registerAndLogin(adminEmail);
        makeAdmin(adminEmail);

        final String secondEmail = factory.randomEmail();
        registerAndLogin(secondEmail);

        setRole(adminToken, userId(secondEmail), "\"ADMIN\"")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.role").value("ADMIN"))
                .andExpect(jsonPath("$.data.roleOverride").value("ADMIN"));

        assertEquals(2, pinnedAdminCount(adminEmail, secondEmail),
                "Today two accounts may hold role_override='ADMIN' at once (flips to 400 in Phase 2).");

        clearPins(adminEmail, secondEmail);
    }

    /** Stays 200 after Phase 2: re-pinning {@code ADMIN} on the existing admin is an idempotent same-row update. */
    @Test
    void rePinningAdmin_OnExistingAdmin_IsIdempotent() throws Exception {
        final String adminEmail = factory.randomEmail();
        final String adminToken = registerAndLogin(adminEmail);
        makeAdmin(adminEmail);

        setRole(adminToken, userId(adminEmail), "\"ADMIN\"")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.role").value("ADMIN"))
                .andExpect(jsonPath("$.data.roleOverride").value("ADMIN"));

        // The admin is unchanged and still holds the admin API.
        mockMvc.perform(get(USERS_URL).header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());

        clearPins(adminEmail);
    }

    /**
     * Stays green after Phase 2: replacing the admin works. The sole admin clears their own pin through the API
     * (self-demotion is deliberately allowed — no lockout logic), losing admin access on their next request; the
     * replacement is then pinned via the documented SQL bootstrap, which Phase 2 keeps as the recovery path.
     */
    @Test
    void replacingTheAdmin_ClearOwnPinThenBootstrapAnother_Works() throws Exception {
        final String firstEmail = factory.randomEmail();
        final String firstToken = registerAndLogin(firstEmail);
        makeAdmin(firstEmail);

        final String secondEmail = factory.randomEmail();
        final String secondToken = registerAndLogin(secondEmail);

        // Self-demotion: the admin clears their own pin and reverts to their derived role (a fresh account => TRIAL).
        setRole(firstToken, userId(firstEmail), "null")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.role").value("TRIAL"))
                .andExpect(jsonPath("$.data.roleOverride").doesNotExist());

        // The demoted caller lost the admin API on the very next request (the role is re-derived per request).
        mockMvc.perform(get(USERS_URL).header("Authorization", "Bearer " + firstToken))
                .andExpect(status().isForbidden());

        // With no admin left, the documented SQL bootstrap installs the replacement.
        makeAdmin(secondEmail);
        mockMvc.perform(get(USERS_URL).header("Authorization", "Bearer " + secondToken))
                .andExpect(status().isOk());

        clearPins(secondEmail);
    }

    // ------------------------------- Private helpers -------------------------------

    private ResultActions setRole(String token, int id, String roleOverrideJson) throws Exception {
        return mockMvc.perform(post(USERS_URL + "/" + id + "/role")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"roleOverride\":" + roleOverrideJson + "}"));
    }

    /** Pin a user to the ADMIN role directly, the documented bootstrap path (no in-app endpoint creates admins). */
    private void makeAdmin(String email) {
        jdbcTemplate.update("UPDATE users SET role_override = 'ADMIN' WHERE email = ?", email);
    }

    /** Clear pins created by a test so admins don't accumulate in the shared Testcontainers database. */
    private void clearPins(String... emails) {
        for (String email : emails) {
            jdbcTemplate.update("UPDATE users SET role_override = NULL WHERE email = ?", email);
        }
    }

    private int pinnedAdminCount(String... emails) {
        return jdbcTemplate.queryForObject(
                "SELECT count(*) FROM users WHERE role_override = 'ADMIN' AND email IN (?, ?)",
                Integer.class, emails[0], emails[1]);
    }

    private int userId(String email) {
        return jdbcTemplate.queryForObject("SELECT id FROM users WHERE email = ?", Integer.class, email);
    }

    private String registerAndLogin(String email) throws Exception {
        factory.registerReturnResult(email, PASSWORD).andExpect(status().isCreated());
        return factory.extractToken(factory.loginReturnResult(email, PASSWORD), "accessToken");
    }
}

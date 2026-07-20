package com.sethhaskellcondie.thegamepensieveapi.controllers;

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

import org.springframework.dao.DataIntegrityViolationException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Single-admin enforcement under the {@code secured} profile. Exactly one account may be pinned
 * {@code role_override='ADMIN'} — the operator — enforced by the {@code uq_users_single_admin} partial unique
 * index (V1_17) and surfaced by the admin API as a 400 with a friendly message.
 *
 * <ul>
 *   <li>With an admin bootstrapped, pinning {@code ADMIN} on a second user is rejected (400 via the API, a
 *       constraint violation via direct SQL) and the second pin never lands.</li>
 *   <li>Re-pinning {@code ADMIN} on the existing admin is an idempotent same-row update (200).</li>
 *   <li>Replacing the admin works — the sole admin clears their own pin via the API (self-demotion is allowed;
 *       there is no lockout logic), and the replacement is pinned via the documented SQL bootstrap, which is
 *       also the recovery path.</li>
 * </ul>
 */
@SpringBootTest
@ActiveProfiles({"test-container", "secured"})
@AutoConfigureMockMvc
public class SingleAdminSecuredProfileTests extends SecuredProfileTest {

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
        // Each test bootstraps its own admin; clear any pin left behind by other tests in the shared database
        // (the single-admin index would otherwise reject the bootstrap).
        jdbcTemplate.update("UPDATE users SET role_override = NULL WHERE role_override = 'ADMIN'");
    }

    /**
     * Given admin A is bootstrapped, when A pins {@code ADMIN} on user B, then the pin is rejected with 400 and
     * a friendly message — the {@code uq_users_single_admin} index allows at most one pinned admin — and B's pin
     * never lands. A direct SQL pin fails hard at the same index.
     */
    @Test
    void pinningSecondAdmin_IsRejected() throws Exception {
        final String adminEmail = factory.randomEmail();
        final String adminToken = registerAndLogin(adminEmail);
        makeAdmin(adminEmail);

        final String secondEmail = factory.randomEmail();
        registerAndLogin(secondEmail);

        setRole(adminToken, userId(secondEmail), "\"ADMIN\"")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0]").value("Failed Input Validation: An admin already exists. Clear the current admin's role override first."));

        assertEquals(1, pinnedAdminCount(adminEmail, secondEmail),
                "The rejected pin must not land: only the original admin holds role_override='ADMIN'.");

        // Manual SQL is stopped by the same index — there is no path to a second admin.
        assertThrows(DataIntegrityViolationException.class, () -> makeAdmin(secondEmail),
                "A direct SQL pin of a second admin should violate uq_users_single_admin.");

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
        return factory.tokenForProvisioned(email, PASSWORD);
    }
}

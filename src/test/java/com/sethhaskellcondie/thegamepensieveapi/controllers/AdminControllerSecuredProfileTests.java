package com.sethhaskellcondie.thegamepensieveapi.controllers;

import com.sethhaskellcondie.thegamepensieveapi.SecuredProfileTest;
import com.sethhaskellcondie.thegamepensieveapi.TestFactory;
import com.sethhaskellcondie.thegamepensieveapi.domain.Keychain;
import com.sethhaskellcondie.thegamepensieveapi.domain.filter.Filter;
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

import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Admin role-management API under the {@code secured} profile. An ADMIN (pinned via {@code role_override}) can
 * list users and set/clear another user's {@code role_override}; the change takes effect on the target's next
 * request because the role is re-derived per request. Non-admins are rejected with 403, anonymous callers with
 * 401 (Spring Security), and the routes themselves bypass the tenant transaction filter so they can read/write
 * the {@code users} table.
 */
@SpringBootTest
@ActiveProfiles({"test-container", "secured"})
@AutoConfigureMockMvc
public class AdminControllerSecuredProfileTests extends SecuredProfileTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    private TestFactory factory;
    private static final String PASSWORD = "Sup3rSecret!";
    private static final String USERS_URL = "/v1/admin/users";
    private static final String SEARCH_URL = "/v1/systems/function/search";

    @BeforeEach
    void setUp() {
        factory = new TestFactory(mockMvc);
        // Each test bootstraps its own admin; clear any pin left behind by other tests in the shared database
        // (the single-admin index uq_users_single_admin allows at most one pinned admin at a time).
        jdbcTemplate.update("UPDATE users SET role_override = NULL WHERE role_override = 'ADMIN'");
    }

    /** Given an ADMIN caller, then GET /v1/admin/users returns the user list including the admin itself. */
    @Test
    void admin_CanListUsers() throws Exception {
        final String adminEmail = factory.randomEmail();
        final String adminToken = registerAndLogin(adminEmail);
        makeAdmin(adminEmail);

        mockMvc.perform(get(USERS_URL).header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(greaterThan(0))))
                .andExpect(jsonPath("$.data[*].email", hasItem(adminEmail)));
    }

    /**
     * Given an ADMIN caller, then pinning a target's role_override changes the target's effective role, and
     * clearing it reverts to auto-derivation — verified behaviorally: a TRIAL may filter (200), once pinned to
     * LAPSED it may not (402), and after the pin is cleared it may again (200).
     */
    @Test
    void admin_CanPinAndClearTargetRole() throws Exception {
        final String targetEmail = factory.randomEmail();
        final String targetToken = registerAndLogin(targetEmail);   // resolves to TRIAL
        final int targetId = userId(targetEmail);

        final String adminEmail = factory.randomEmail();
        final String adminToken = registerAndLogin(adminEmail);
        makeAdmin(adminEmail);

        // TRIAL can filter.
        filteredSearch(targetToken).andExpect(status().isOk());

        // Admin pins the target to LAPSED.
        setRole(adminToken, targetId, "\"LAPSED\"")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.role").value("LAPSED"))
                .andExpect(jsonPath("$.data.roleOverride").value("LAPSED"));

        // The pin takes effect on the target's next request: LAPSED cannot filter.
        filteredSearch(targetToken).andExpect(status().isPaymentRequired());

        // Admin clears the pin; the target reverts to its derived TRIAL role.
        setRole(adminToken, targetId, "null")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.role").value("TRIAL"))
                .andExpect(jsonPath("$.data.roleOverride").doesNotExist());

        filteredSearch(targetToken).andExpect(status().isOk());
    }

    /** Given a non-admin (TRIAL) caller, then any /v1/admin/** route is 403. */
    @Test
    void nonAdmin_IsForbidden() throws Exception {
        final String token = registerAndLogin(factory.randomEmail());   // TRIAL

        mockMvc.perform(get(USERS_URL).header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    /** Given an anonymous caller, then /v1/admin/** is 401 at Spring Security (never reaches the controller). */
    @Test
    void anonymous_IsUnauthorized() throws Exception {
        mockMvc.perform(get(USERS_URL))
                .andExpect(status().isUnauthorized());
    }

    /** Given an ADMIN caller, then an unknown role value is rejected with 400. */
    @Test
    void admin_SettingInvalidRole_IsBadRequest() throws Exception {
        final String adminEmail = factory.randomEmail();
        final String adminToken = registerAndLogin(adminEmail);
        makeAdmin(adminEmail);

        setRole(adminToken, userId(adminEmail), "\"bogus\"")
                .andExpect(status().isBadRequest());
    }

    /** Given an ADMIN caller, then pinning a role on a non-existent user is 404. */
    @Test
    void admin_SettingRoleOnMissingUser_IsNotFound() throws Exception {
        final String adminEmail = factory.randomEmail();
        final String adminToken = registerAndLogin(adminEmail);
        makeAdmin(adminEmail);

        setRole(adminToken, 999_999, "\"PAID\"")
                .andExpect(status().isNotFound());
    }

    // ------------------------------- Private helpers -------------------------------

    private ResultActions setRole(String token, int id, String roleOverrideJson) throws Exception {
        return mockMvc.perform(post(USERS_URL + "/" + id + "/role")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"roleOverride\":" + roleOverrideJson + "}"));
    }

    private ResultActions filteredSearch(String token) throws Exception {
        final Filter nameFilter = new Filter(Keychain.SYSTEM_KEY, Filter.FIELD_TYPE_TEXT, "name", Filter.OPERATOR_EQUALS, "anything", false);
        return mockMvc.perform(post(SEARCH_URL)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(factory.formatFiltersPayload(nameFilter)));
    }

    /** Pin a user to the ADMIN role directly, the documented bootstrap path (no in-app endpoint creates admins). */
    private void makeAdmin(String email) {
        jdbcTemplate.update("UPDATE users SET role_override = 'ADMIN' WHERE email = ?", email);
    }

    private int userId(String email) {
        return jdbcTemplate.queryForObject("SELECT id FROM users WHERE email = ?", Integer.class, email);
    }

    private String registerAndLogin(String email) throws Exception {
        return factory.tokenForProvisioned(email, PASSWORD);
    }
}

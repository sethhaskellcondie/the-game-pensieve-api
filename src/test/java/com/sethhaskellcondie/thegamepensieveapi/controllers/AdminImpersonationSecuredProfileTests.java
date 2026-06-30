package com.sethhaskellcondie.thegamepensieveapi.controllers;

import com.fasterxml.jackson.core.type.TypeReference;
import com.sethhaskellcondie.thegamepensieveapi.TestFactory;
import com.sethhaskellcondie.thegamepensieveapi.domain.Keychain;
import com.sethhaskellcondie.thegamepensieveapi.domain.entity.system.SystemResponseDto;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Admin impersonation ("act as user") under the {@code secured} profile. An ADMIN sends an
 * {@code X-Act-As-Owner: <userId>} header to act as another user: Row-Level Security scopes the request to that
 * user's data and the capability matrix adopts that user's effective role (full act-as). The header is ignored
 * for non-admins. {@code GET /v1/auth/me} reports the admin as the primary identity with an {@code impersonating}
 * marker so the front end knows an admin is driving; dropping the header ends impersonation.
 *
 * <p>Admins are bootstrapped exactly as in {@link AdminControllerSecuredProfileTests} — a direct
 * {@code role_override='ADMIN'} pin, the documented bootstrap path (no in-app endpoint creates admins).
 */
@SpringBootTest
@ActiveProfiles({"test-container", "secured"})
@AutoConfigureMockMvc
public class AdminImpersonationSecuredProfileTests {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    private TestFactory factory;
    private static final String PASSWORD = "Sup3rSecret!";
    private static final String SYSTEMS_URL = "/v1/systems";
    private static final String ME_URL = "/v1/auth/me";
    private static final String ACT_AS_HEADER = "X-Act-As-Owner";

    @BeforeEach
    void setUp() {
        factory = new TestFactory(mockMvc);
    }

    /**
     * Given an ADMIN caller, when they send the X-Act-As-Owner header for a target, then the request is scoped to
     * the target's tenant: the admin sees the target's row only while the header is set.
     */
    @Test
    void adminImpersonating_SeesTargetsData() throws Exception {
        final String targetEmail = factory.randomEmail();
        final String targetToken = registerAndLogin(targetEmail);
        final int targetId = userId(targetEmail);
        final String systemName = "Target-System-" + uniqueSuffix();
        createSystemAs(targetToken, systemName, null).andExpect(status().isCreated());

        final String adminEmail = factory.randomEmail();
        final String adminToken = registerAndLogin(adminEmail);
        makeAdmin(adminEmail);

        // Without the header the admin is in their own (empty) tenant and cannot see the target's row.
        assertFalse(searchSystemsByName(adminToken, null, systemName).stream().anyMatch(s -> systemName.equals(s.name())),
                "Admin must not see the target's system without the impersonation header.");
        // With the header the admin acts as the target and sees their row.
        assertTrue(searchSystemsByName(adminToken, targetId, systemName).stream().anyMatch(s -> systemName.equals(s.name())),
                "Admin impersonating the target should see the target's system.");
    }

    /**
     * Given an ADMIN caller impersonating a target, then GET /v1/auth/me reports the admin as the primary
     * identity (role ADMIN) with the target nested under {@code impersonating}; without the header it reports the
     * admin alone with no marker.
     */
    @Test
    void me_WhileImpersonating_ReportsAdminPrimaryWithTargetMarker() throws Exception {
        final String targetEmail = factory.randomEmail();
        registerAndLogin(targetEmail);                       // a fresh account resolves to TRIAL
        final int targetId = userId(targetEmail);

        final String adminEmail = factory.randomEmail();
        final String adminToken = registerAndLogin(adminEmail);
        makeAdmin(adminEmail);
        final int adminId = userId(adminEmail);

        getMe(adminToken, targetId).andExpectAll(
                status().isOk(),
                jsonPath("$.data.id").value(adminId),
                jsonPath("$.data.email").value(adminEmail),
                jsonPath("$.data.role").value("ADMIN"),
                jsonPath("$.data.impersonating.id").value(targetId),
                jsonPath("$.data.impersonating.email").value(targetEmail),
                jsonPath("$.data.impersonating.role").value("TRIAL"),
                jsonPath("$.errors").isEmpty()
        );

        getMe(adminToken, null).andExpectAll(
                status().isOk(),
                jsonPath("$.data.id").value(adminId),
                jsonPath("$.data.role").value("ADMIN"),
                jsonPath("$.data.impersonating").isEmpty()
        );
    }

    /**
     * Given an ADMIN impersonating a target whose role permits writes (TRIAL), then a create lands in the
     * target's tenant — proving full act-as, not read-only: the target sees the row on their own token.
     */
    @Test
    void adminImpersonating_CanWriteIntoTargetsTenant() throws Exception {
        final String targetEmail = factory.randomEmail();
        final String targetToken = registerAndLogin(targetEmail);   // TRIAL holds WRITE
        final int targetId = userId(targetEmail);

        final String adminEmail = factory.randomEmail();
        final String adminToken = registerAndLogin(adminEmail);
        makeAdmin(adminEmail);

        final String systemName = "Impersonated-Write-" + uniqueSuffix();
        createSystemAs(adminToken, systemName, targetId).andExpect(status().isCreated());

        // The row was stamped to the target, so the target finds it on their own token.
        assertTrue(searchSystemsByName(targetToken, null, systemName).stream().anyMatch(s -> systemName.equals(s.name())),
                "A system the admin created while impersonating should belong to the target's tenant.");
    }

    /**
     * Given an ADMIN impersonating a LAPSED target, then a write is rejected with 403 — the effective role is the
     * target's (LAPSED lacks WRITE), not the admin's. This is what "full act-as" means: capabilities follow the
     * impersonated user.
     */
    @Test
    void adminImpersonating_LapsedTarget_CannotWrite() throws Exception {
        final String targetEmail = factory.randomEmail();
        registerAndLogin(targetEmail);
        final int targetId = userId(targetEmail);
        pinRole(targetEmail, "LAPSED");

        final String adminEmail = factory.randomEmail();
        final String adminToken = registerAndLogin(adminEmail);
        makeAdmin(adminEmail);

        createSystemAs(adminToken, "Should-Fail-" + uniqueSuffix(), targetId).andExpect(status().isForbidden());
    }

    /**
     * Given a non-admin caller, then the X-Act-As-Owner header is ignored: GET /v1/auth/me reports the caller's
     * own identity with no impersonation marker, and a search stays scoped to the caller's own (empty) tenant.
     */
    @Test
    void nonAdminWithHeader_IsIgnored() throws Exception {
        final String otherEmail = factory.randomEmail();
        final String otherToken = registerAndLogin(otherEmail);   // the user a non-admin would try to act as
        final int otherId = userId(otherEmail);
        final String systemName = "Other-System-" + uniqueSuffix();
        createSystemAs(otherToken, systemName, null).andExpect(status().isCreated());

        final String callerEmail = factory.randomEmail();
        final String callerToken = registerAndLogin(callerEmail);   // a plain TRIAL user
        final int callerId = userId(callerEmail);

        // The header is ignored: the caller is still themselves, with no impersonation marker.
        getMe(callerToken, otherId).andExpectAll(
                status().isOk(),
                jsonPath("$.data.id").value(callerId),
                jsonPath("$.data.role").value("TRIAL"),
                jsonPath("$.data.impersonating").isEmpty()
        );
        // And data is still scoped to the caller, not the user named in the header.
        assertFalse(searchSystemsByName(callerToken, otherId, systemName).stream().anyMatch(s -> systemName.equals(s.name())),
                "A non-admin's X-Act-As-Owner header must not expose another user's data.");
    }

    // ------------------------------- Private helpers -------------------------------

    private ResultActions getMe(String token, Integer actAsOwner) throws Exception {
        return mockMvc.perform(withActAs(get(ME_URL).header("Authorization", "Bearer " + token), actAsOwner));
    }

    private ResultActions createSystemAs(String token, String name, Integer actAsOwner) throws Exception {
        return mockMvc.perform(withActAs(post(SYSTEMS_URL)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(factory.formatSystemPayload(name, 1, false, null)), actAsOwner));
    }

    private List<SystemResponseDto> searchSystemsByName(String token, Integer actAsOwner, String name) throws Exception {
        final Filter filter = new Filter(Keychain.SYSTEM_KEY, Filter.FIELD_TYPE_TEXT, "name", Filter.OPERATOR_EQUALS, name, false);
        final ResultActions result = mockMvc.perform(withActAs(post(SYSTEMS_URL + "/function/search")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(factory.formatFiltersPayload(filter)), actAsOwner))
                .andExpect(status().isOk());
        return factory.extractDataList(result, new TypeReference<List<SystemResponseDto>>() { });
    }

    private MockHttpServletRequestBuilder withActAs(MockHttpServletRequestBuilder builder, Integer actAsOwner) {
        return actAsOwner == null ? builder : builder.header(ACT_AS_HEADER, String.valueOf(actAsOwner));
    }

    /** Pin a user to the ADMIN role directly, the documented bootstrap path (no in-app endpoint creates admins). */
    private void makeAdmin(String email) {
        pinRole(email, "ADMIN");
    }

    private void pinRole(String email, String role) {
        jdbcTemplate.update("UPDATE users SET role_override = ? WHERE email = ?", role, email);
    }

    private int userId(String email) {
        return jdbcTemplate.queryForObject("SELECT id FROM users WHERE email = ?", Integer.class, email);
    }

    private String uniqueSuffix() {
        // Controller (@SpringBootTest) tests commit to a shared Testcontainers DB, so names must be unique.
        return java.util.UUID.randomUUID().toString().substring(0, 8);
    }

    private String registerAndLogin(String email) throws Exception {
        factory.registerReturnResult(email, PASSWORD).andExpect(status().isCreated());
        return factory.extractToken(factory.loginReturnResult(email, PASSWORD), "accessToken");
    }
}

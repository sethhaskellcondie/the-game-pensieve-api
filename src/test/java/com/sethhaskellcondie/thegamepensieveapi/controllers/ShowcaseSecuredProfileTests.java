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

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Public showcases under the {@code secured} profile — Phase 1 of the showcase rollout
 * ({@code localFiles/rollout_single_admin.md}): these tests pin <em>current</em> behavior and are green against
 * unchanged code. Later phases flip the marked expectations in place.
 *
 * <p>Expectations:
 * <ul>
 *   <li><strong>FLIPS in Phase 3 (viewer switching):</strong> the {@code X-Showcase: <slug>} header is ignored
 *       today — anonymous callers get the default showcase regardless, and authenticated callers stay in their
 *       own tenant with their own capabilities (a write with the header set succeeds). Phase 3 makes a known
 *       slug resolve to that owner's data GUEST-scoped (writes 403) and an unknown slug a 404.</li>
 *   <li><strong>Never flips:</strong> anonymous with no header reads and filters the default showcase.</li>
 *   <li><strong>FLIPS in Phase 4 (management + directory):</strong> {@code GET /v1/showcases} does not exist —
 *       anonymous is stopped at Spring Security (401, the route is not permitAll) and an authenticated call
 *       reaches the dispatcher and 404s. Phase 4 ships it as a permitAll directory returning 200. The admin
 *       user DTO carries no {@code showcaseSlug}/{@code showcaseName} fields yet; Phase 4 adds them.</li>
 * </ul>
 */
@SpringBootTest
@ActiveProfiles({"test-container", "secured"})
@AutoConfigureMockMvc
public class ShowcaseSecuredProfileTests {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    private TestFactory factory;
    private static final String PASSWORD = "Sup3rSecret!";
    private static final String SYSTEMS_URL = "/v1/systems";
    private static final String SEARCH_URL = "/v1/systems/function/search";
    private static final String SHOWCASES_URL = "/v1/showcases";
    private static final String ADMIN_USERS_URL = "/v1/admin/users";
    private static final String SHOWCASE_HEADER = "X-Showcase";

    @BeforeEach
    void setUp() {
        factory = new TestFactory(mockMvc);
    }

    // ============================ Viewer switching (flips in Phase 3) ============================

    /**
     * FLIPS in Phase 3: given an anonymous caller sending {@code X-Showcase}, then today the header is ignored
     * and the default showcase is served — even for a slug that will never exist. Phase 3 splits this: a known
     * slug resolves to that owner's data, an unknown slug becomes a filter-written 404.
     */
    @Test
    void anonymousWithShowcaseHeader_HeaderIgnoredToday_DefaultShowcaseServed() throws Exception {
        final String showcaseName = "Showcase-System-" + uniqueSuffix();
        seedSystemOwnedByShowcase(showcaseName);

        final ResultActions result = mockMvc.perform(anonymousSearch(showcaseName)
                        .header(SHOWCASE_HEADER, "no-such-slug"))
                .andExpect(status().isOk());
        assertTrue(extractSystems(result).stream().anyMatch(s -> showcaseName.equals(s.name())),
                "Today the X-Showcase header is ignored: an anonymous request still reads the default showcase.");
    }

    /**
     * FLIPS in Phase 3: given an authenticated PAID caller sending {@code X-Showcase}, then today the header is
     * ignored — the request stays in the caller's own tenant with the caller's own capabilities, so they read
     * their own data and a write with the header set succeeds (201). Phase 3 makes the header win even for
     * authenticated callers: a known slug serves the showcase owner's data GUEST-scoped, and the write is a 403.
     */
    @Test
    void authenticatedWithShowcaseHeader_HeaderIgnoredToday_OwnDataAndWritesServed() throws Exception {
        final String email = factory.randomEmail();
        final String token = registerAndLogin(email);
        makePaid(email);

        final String ownName = "Own-System-" + uniqueSuffix();
        mockMvc.perform(post(SYSTEMS_URL)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(factory.formatSystemPayload(ownName, 1, false, null)))
                .andExpect(status().isCreated());

        // Reads with the header still land in the caller's own tenant.
        final ResultActions result = mockMvc.perform(anonymousSearch(ownName)
                        .header("Authorization", "Bearer " + token)
                        .header(SHOWCASE_HEADER, "no-such-slug"))
                .andExpect(status().isOk());
        assertTrue(extractSystems(result).stream().anyMatch(s -> ownName.equals(s.name())),
                "Today the X-Showcase header is ignored: an authenticated request still reads the caller's own data.");

        // Writes with the header succeed with the caller's own role (flips to 403 GUEST-scoping in Phase 3).
        mockMvc.perform(post(SYSTEMS_URL)
                        .header("Authorization", "Bearer " + token)
                        .header(SHOWCASE_HEADER, "no-such-slug")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(factory.formatSystemPayload("Own-System-" + uniqueSuffix(), 1, false, null)))
                .andExpect(status().isCreated());
    }

    /** Never flips: an anonymous caller with no header reads (GET by id) and filters the default showcase. */
    @Test
    void anonymousNoHeader_ReadsAndFiltersDefaultShowcase() throws Exception {
        final String showcaseName = "Showcase-System-" + uniqueSuffix();
        final int systemId = seedSystemOwnedByShowcase(showcaseName);

        mockMvc.perform(get(SYSTEMS_URL + "/" + systemId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value(showcaseName));

        final ResultActions result = mockMvc.perform(anonymousSearch(showcaseName))
                .andExpect(status().isOk());
        assertTrue(extractSystems(result).stream().anyMatch(s -> showcaseName.equals(s.name())),
                "An anonymous request with no header should read and filter the default showcase.");
    }

    // ============================ Management + directory (flips in Phase 4) ============================

    /**
     * FLIPS in Phase 4: {@code GET /v1/showcases} does not exist today. Anonymous callers are stopped at Spring
     * Security (401 — the route holds no permitAll rule), and authenticated callers reach the dispatcher and 404.
     * Phase 4 ships the directory as a permitAll endpoint returning 200 with the visible showcases.
     */
    @Test
    void showcaseDirectory_DoesNotExistToday() throws Exception {
        mockMvc.perform(get(SHOWCASES_URL))
                .andExpect(status().isUnauthorized());

        final String token = registerAndLogin(factory.randomEmail());
        mockMvc.perform(get(SHOWCASES_URL).header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    /** FLIPS in Phase 4: the admin user DTO carries no showcase fields yet; Phase 4 adds showcaseSlug + showcaseName. */
    @Test
    void adminUsersDto_HasNoShowcaseFieldsToday() throws Exception {
        final String adminEmail = factory.randomEmail();
        final String adminToken = registerAndLogin(adminEmail);
        makeAdmin(adminEmail);

        mockMvc.perform(get(ADMIN_USERS_URL).header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].showcaseSlug").doesNotExist())
                .andExpect(jsonPath("$.data[0].showcaseName").doesNotExist());

        clearPin(adminEmail);
    }

    // ------------------------------- Private helpers -------------------------------

    /** A search request for systems matching the given name; add Authorization/X-Showcase headers as needed. */
    private MockHttpServletRequestBuilder anonymousSearch(String name) {
        final Filter filter = new Filter(Keychain.SYSTEM_KEY, Filter.FIELD_TYPE_TEXT, "name", Filter.OPERATOR_EQUALS, name, false);
        return post(SEARCH_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(factory.formatFiltersPayload(filter));
    }

    private List<SystemResponseDto> extractSystems(ResultActions result) throws Exception {
        return factory.extractDataList(result, new TypeReference<List<SystemResponseDto>>() { });
    }

    /**
     * Insert a system owned by the seeded public showcase owner, directly via JDBC (the secured API can't create
     * showcase-owned rows). The explicit owner_id and the superuser test connection sidestep RLS/the tenant filter.
     */
    private int seedSystemOwnedByShowcase(String name) {
        return jdbcTemplate.queryForObject(
                "INSERT INTO systems(name, generation, handheld, owner_id, created_at, updated_at) "
                        + "VALUES (?, 1, false, (SELECT id FROM users WHERE is_public_showcase), now(), now()) "
                        + "RETURNING id",
                Integer.class, name);
    }

    /**
     * Promote a user to an active paid subscription so they resolve to PAID on the next request — simulates the
     * (deferred) Paddle billing webhook, which has no in-app code path yet.
     */
    private void makePaid(String email) {
        jdbcTemplate.update(
                "UPDATE users SET plan = 'paid', subscription_status = 'active', "
                        + "access_until = now() + interval '30 days' WHERE email = ?",
                email);
    }

    /** Pin a user to the ADMIN role directly, the documented bootstrap path (no in-app endpoint creates admins). */
    private void makeAdmin(String email) {
        jdbcTemplate.update("UPDATE users SET role_override = 'ADMIN' WHERE email = ?", email);
    }

    /** Clear a pin created by a test so admins don't accumulate in the shared Testcontainers database. */
    private void clearPin(String email) {
        jdbcTemplate.update("UPDATE users SET role_override = NULL WHERE email = ?", email);
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

package com.sethhaskellcondie.thegamepensieveapi.controllers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sethhaskellcondie.thegamepensieveapi.SeededUsersFixture;
import com.sethhaskellcondie.thegamepensieveapi.TestFactory;
import com.sethhaskellcondie.thegamepensieveapi.domain.Keychain;
import com.sethhaskellcondie.thegamepensieveapi.domain.filter.Filter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The role/showcase verification matrix run against the full multirole seed set (see
 * {@link SeededUsersFixture} and the "Seeding Multirole Test Data" section of {@code documentation/Notes.md}).
 * Where the other secured-profile tests each build the minimal data they need, this suite seeds the complete
 * fixture — eight users across TRIAL/PAID/LAPSED, two public showcases, and a populated default showcase — and
 * asserts each capability-matrix row and the showcase-switching features against that realistic data set.
 *
 * <p>The fixture is heavy (eight ~220KB imports), so it seeds <em>once per class</em> and runs under its own
 * {@code seeded-tests} profile — a dedicated Testcontainers database, mirroring the {@code filter-tests}/
 * {@code import-tests} pattern — so the fixed seed emails and slugs never collide with the other suites.
 * Run it alone with {@code ./mvnw test -Dtest=SeededDataMatrixTests} (Docker required).
 */
@SpringBootTest
@ActiveProfiles({"seeded-tests", "secured"})
@AutoConfigureMockMvc
public class SeededDataMatrixTests {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    private TestFactory factory;
    private SeededUsersFixture fixture;
    private static boolean seeded = false;

    private static final String SYSTEMS_URL = "/v1/systems";
    private static final String SEARCH_URL = "/v1/systems/function/search";
    private static final String IMPORT_URL = "/v1/function/import";
    private static final String SHOWCASES_URL = "/v1/showcases";
    private static final String ADMIN_USERS_URL = "/v1/admin/users";
    private static final String SHOWCASE_HEADER = "X-Showcase";
    private static final String EMPTY_IMPORT_BODY =
            "{\"data\":{\"customFields\":[],\"toys\":[],\"systems\":[],\"videoGameBoxes\":[],\"boardGameBoxes\":[],\"metadata\":[]}}";

    @BeforeEach
    void setUp() throws Exception {
        factory = new TestFactory(mockMvc);
        fixture = new SeededUsersFixture(mockMvc, jdbcTemplate);
        // The full seed is expensive; run it once per class. The suite is single-threaded and the
        // seeded-tests Testcontainers database lives for the whole JVM, so a static guard is enough.
        if (!seeded) {
            fixture.seedAll();
            seeded = true;
        }
    }

    // ============================ GUEST (anonymous, default showcase) ============================

    /** Given an anonymous caller with no header, then the seeded default showcase is readable and filterable. */
    @Test
    void guest_ReadsAndFiltersTheDefaultShowcase() throws Exception {
        final Set<String> names = systemNames(mockMvc.perform(anonymousSearch(null)).andExpect(status().isOk()));
        assertFalse(names.isEmpty(), "The default showcase should be populated with the sample data set.");

        mockMvc.perform(anonymousSearch("anything")).andExpect(status().isOk());
    }

    // ============================ TRIAL ============================

    /** Given a seeded TRIAL user, then it can write its own data but the IMPORT capability is denied. */
    @Test
    void trialUser_CanWriteButNotImport() throws Exception {
        final String token = fixture.login(SeededUsersFixture.TRIAL_1);

        mockMvc.perform(post(SYSTEMS_URL)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(factory.formatSystemPayload("Trial-Write-" + uniqueSuffix(), 1, false, null)))
                .andExpect(status().isCreated());

        mockMvc.perform(post(IMPORT_URL)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(EMPTY_IMPORT_BODY))
                .andExpect(status().isForbidden());
    }

    // ============================ PAID ============================

    /** Given a seeded PAID user, then a filtered search succeeds and returns exactly their own imported rows. */
    @Test
    void paidUser_FiltersExactlyTheirOwnSeededData() throws Exception {
        final String token = fixture.login(SeededUsersFixture.PAID_1);

        final Set<String> ownNames = systemNames(
                searchSystems(token, factory.formatFiltersPayload(new ArrayList<Filter>())).andExpect(status().isOk()));
        assertEquals(expectedSystemNames(SeededUsersFixture.PAID_1.seedFile()), ownNames,
                "A PAID user's list must be exactly the systems from their own seed file — RLS admits nothing else.");

        searchSystems(token, factory.formatFiltersPayload(nameFilter(ownNames.iterator().next())))
                .andExpect(status().isOk());
    }

    // ============================ LAPSED ============================

    /** Given a seeded LAPSED user, then it can list its data unfiltered but filter/write/import are denied. */
    @Test
    void lapsedUser_CanListButNotFilterWriteOrImport() throws Exception {
        final String token = fixture.login(SeededUsersFixture.LAPSED_1);

        final Set<String> names = systemNames(
                searchSystems(token, factory.formatFiltersPayload(new ArrayList<Filter>())).andExpect(status().isOk()));
        assertEquals(expectedSystemNames(SeededUsersFixture.LAPSED_1.seedFile()), names,
                "A LAPSED user should still read their own imported data unfiltered.");

        searchSystems(token, factory.formatFiltersPayload(nameFilter("anything")))
                .andExpect(status().isPaymentRequired());

        mockMvc.perform(post(SYSTEMS_URL)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(factory.formatSystemPayload("Lapsed-Write-" + uniqueSuffix(), 1, false, null)))
                .andExpect(status().isForbidden());

        mockMvc.perform(post(IMPORT_URL)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(EMPTY_IMPORT_BODY))
                .andExpect(status().isForbidden());
    }

    // ============================ ADMIN ============================

    /** Given the seeded admin, then the admin API works, the single-admin rule holds, and non-admins are 403. */
    @Test
    void admin_ManagesUsersAndSingleAdminRuleHolds() throws Exception {
        final String adminToken = fixture.login(SeededUsersFixture.ADMIN_EMAIL, SeededUsersFixture.ADMIN_PASSWORD);

        mockMvc.perform(get(ADMIN_USERS_URL).header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.email == 'trial1@email.com')].role").value("TRIAL"))
                .andExpect(jsonPath("$.data[?(@.email == 'lapsed1@email.com')].role").value("LAPSED"))
                .andExpect(jsonPath("$.data[?(@.email == 'showcase1@email.com')].showcaseSlug").value("showcase-one"));

        // Pinning a second admin trips uq_users_single_admin — exactly one operator, always.
        mockMvc.perform(post(ADMIN_USERS_URL + "/" + fixture.userId(SeededUsersFixture.PAID_2.email()) + "/role")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roleOverride\":\"ADMIN\"}"))
                .andExpect(status().isBadRequest());

        final String paidToken = fixture.login(SeededUsersFixture.PAID_1);
        mockMvc.perform(get(ADMIN_USERS_URL).header("Authorization", "Bearer " + paidToken))
                .andExpect(status().isForbidden());
    }

    // ============================ Showcases ============================

    /** Given the two granted showcases, then the public directory lists exactly them (names, never emails). */
    @Test
    void showcaseDirectory_ListsExactlyTheGrantedShowcases() throws Exception {
        mockMvc.perform(get(SHOWCASES_URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[?(@.slug == 'showcase-one')].name").value("Showcase One"))
                .andExpect(jsonPath("$.data[?(@.slug == 'showcase-two')].name").value("Showcase Two"));
    }

    /** Given an anonymous viewer, then the X-Showcase header switches between the two seeded showcases. */
    @Test
    void anonymousViewer_SwitchesShowcasesViaHeader() throws Exception {
        final Set<String> showcaseOneNames = systemNames(
                mockMvc.perform(anonymousSearch(null).header(SHOWCASE_HEADER, "showcase-one"))
                        .andExpect(status().isOk()));
        assertEquals(expectedSystemNames(SeededUsersFixture.SHOWCASE_1.seedFile()), showcaseOneNames,
                "X-Showcase: showcase-one should serve exactly that owner's seeded collection.");

        final Set<String> showcaseTwoNames = systemNames(
                mockMvc.perform(anonymousSearch(null).header(SHOWCASE_HEADER, "showcase-two"))
                        .andExpect(status().isOk()));
        assertEquals(expectedSystemNames(SeededUsersFixture.SHOWCASE_2.seedFile()), showcaseTwoNames,
                "X-Showcase: showcase-two should serve exactly that owner's seeded collection.");

        assertNotEquals(showcaseOneNames, showcaseTwoNames,
                "The two seeded showcases must be distinguishable data sets.");

        mockMvc.perform(anonymousSearch(null).header(SHOWCASE_HEADER, "no-such-slug"))
                .andExpect(status().isNotFound());
    }

    /** Given an authenticated PAID viewer sending X-Showcase, then the view is the showcase's data, GUEST-scoped. */
    @Test
    void authenticatedViewer_ShowcaseViewIsGuestScoped() throws Exception {
        final String token = fixture.login(SeededUsersFixture.PAID_1);

        final Set<String> names = systemNames(
                mockMvc.perform(anonymousSearch(null)
                                .header("Authorization", "Bearer " + token)
                                .header(SHOWCASE_HEADER, "showcase-one"))
                        .andExpect(status().isOk()));
        assertEquals(expectedSystemNames(SeededUsersFixture.SHOWCASE_1.seedFile()), names,
                "The header must win for authenticated callers — the showcase's data, not the viewer's own.");
        assertNotEquals(expectedSystemNames(SeededUsersFixture.PAID_1.seedFile()), names,
                "The viewer's own collection must not appear in a showcase view.");

        // GUEST scoping: a write attempt while the header is set is 403 even though the viewer is PAID.
        mockMvc.perform(post(SYSTEMS_URL)
                        .header("Authorization", "Bearer " + token)
                        .header(SHOWCASE_HEADER, "showcase-one")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(factory.formatSystemPayload("Viewer-Write-" + uniqueSuffix(), 1, false, null)))
                .andExpect(status().isForbidden());
    }

    // ============================ Helpers ============================

    private MockHttpServletRequestBuilder anonymousSearch(String name) {
        final String payload = name == null
                ? factory.formatFiltersPayload(new ArrayList<Filter>())
                : factory.formatFiltersPayload(
                        new Filter(Keychain.SYSTEM_KEY, Filter.FIELD_TYPE_TEXT, "name", Filter.OPERATOR_EQUALS, name, false));
        return post(SEARCH_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload);
    }

    private ResultActions searchSystems(String token, String filtersPayload) throws Exception {
        return mockMvc.perform(post(SEARCH_URL)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(filtersPayload));
    }

    private List<Filter> nameFilter(String name) {
        return List.of(new Filter(Keychain.SYSTEM_KEY, Filter.FIELD_TYPE_TEXT, "name", Filter.OPERATOR_EQUALS, name, false));
    }

    private Set<String> systemNames(ResultActions result) throws Exception {
        final String responseString = result.andReturn().getResponse().getContentAsString();
        final JsonNode dataNode = new ObjectMapper().readTree(responseString).get("data");
        final Set<String> names = new HashSet<>();
        dataNode.forEach(node -> names.add(node.get("name").asText()));
        return names;
    }

    /** The system names a seed file contains — what an owner's collection must be exactly, per RLS scoping. */
    private Set<String> expectedSystemNames(String seedFile) throws Exception {
        final JsonNode root = new ObjectMapper().readTree(new ClassPathResource("seeders/" + seedFile).getInputStream());
        final Set<String> names = new HashSet<>();
        root.get("systems").forEach(node -> names.add(node.get("name").asText()));
        return names;
    }

    private String uniqueSuffix() {
        return UUID.randomUUID().toString().substring(0, 8);
    }
}

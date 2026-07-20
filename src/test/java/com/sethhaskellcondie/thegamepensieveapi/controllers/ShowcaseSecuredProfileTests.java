package com.sethhaskellcondie.thegamepensieveapi.controllers;

import com.fasterxml.jackson.core.type.TypeReference;
import com.sethhaskellcondie.thegamepensieveapi.SecuredProfileTest;
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

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Public showcases under the {@code secured} profile. A showcase is a paid user's own collection made public:
 * a non-null {@code users.showcase_slug} (V1_18) is both the entitlement and the public address. Viewers switch
 * showcases with an {@code X-Showcase: <slug>} header, resolved by the tenant filter to the owner's tenant with
 * GUEST scoping (read + filter only) — for <em>every</em> caller, authenticated ones included, and taking
 * precedence over {@code X-Act-As-Owner}. A slug resolves only while its owner derives to PAID or ADMIN; an
 * unknown or not-visible slug is a filter-written 404. Anonymous callers with no header keep getting the
 * seeded <em>default</em> showcase (the {@code is_public_showcase} row) exactly as before.
 */
@SpringBootTest
@ActiveProfiles({"test-container", "secured"})
@AutoConfigureMockMvc
public class ShowcaseSecuredProfileTests extends SecuredProfileTest {

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
        // Some tests bootstrap an admin; clear any pin left behind by other tests in the shared database
        // (the single-admin index uq_users_single_admin allows at most one pinned admin at a time).
        jdbcTemplate.update("UPDATE users SET role_override = NULL WHERE role_override = 'ADMIN'");
    }

    // ============================ Viewer switching ============================

    /** Given an anonymous caller sending an {@code X-Showcase} slug no user holds, then the request is a 404. */
    @Test
    void anonymousWithUnknownShowcaseSlug_Is404() throws Exception {
        mockMvc.perform(anonymousSearch("anything").header(SHOWCASE_HEADER, "no-such-slug"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.data").isEmpty())
                .andExpect(jsonPath("$.errors[0]").value("No public showcase exists for the requested X-Showcase slug."));
    }

    /**
     * Given two paid users with public showcases, then an anonymous viewer switches between their collections
     * with the {@code X-Showcase} header — each slug serves exactly its owner's data.
     */
    @Test
    void anonymousSwitchesBetweenShowcases_ViaHeader() throws Exception {
        final ShowcaseOwner ownerA = createShowcaseOwner();
        final ShowcaseOwner ownerB = createShowcaseOwner();

        final ResultActions viewA = mockMvc.perform(anonymousSearch(null).header(SHOWCASE_HEADER, ownerA.slug()))
                .andExpect(status().isOk());
        final List<SystemResponseDto> systemsA = extractSystems(viewA);
        assertTrue(systemsA.stream().anyMatch(s -> ownerA.systemName().equals(s.name())),
                "X-Showcase with owner A's slug should serve A's collection.");
        assertFalse(systemsA.stream().anyMatch(s -> ownerB.systemName().equals(s.name())),
                "Owner B's data must not leak into owner A's showcase view.");

        final ResultActions viewB = mockMvc.perform(anonymousSearch(null).header(SHOWCASE_HEADER, ownerB.slug()))
                .andExpect(status().isOk());
        final List<SystemResponseDto> systemsB = extractSystems(viewB);
        assertTrue(systemsB.stream().anyMatch(s -> ownerB.systemName().equals(s.name())),
                "X-Showcase with owner B's slug should serve B's collection.");
        assertFalse(systemsB.stream().anyMatch(s -> ownerA.systemName().equals(s.name())),
                "Owner A's data must not leak into owner B's showcase view.");
    }

    /**
     * Given an authenticated caller sending {@code X-Showcase}, then the header wins even though they are logged
     * in: the request is scoped to the showcase owner's tenant as GUEST — they read the showcase (not their own
     * data), and GUEST capabilities apply (write → 403, backup → 403). Browsing a showcase never requires
     * logging out, and it can never mutate the showcase.
     */
    @Test
    void authenticatedWithShowcaseHeader_IsGuestScopedToShowcase() throws Exception {
        final ShowcaseOwner owner = createShowcaseOwner();

        final String viewerEmail = factory.randomEmail();
        final String viewerToken = registerAndLogin(viewerEmail);
        makePaid(viewerEmail);
        final String viewerOwnName = "Own-System-" + uniqueSuffix();
        mockMvc.perform(post(SYSTEMS_URL)
                        .header("Authorization", "Bearer " + viewerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(factory.formatSystemPayload(viewerOwnName, 1, false, null)))
                .andExpect(status().isCreated());

        // Reads with the header land in the showcase owner's tenant, not the caller's own.
        final ResultActions result = mockMvc.perform(anonymousSearch(null)
                        .header("Authorization", "Bearer " + viewerToken)
                        .header(SHOWCASE_HEADER, owner.slug()))
                .andExpect(status().isOk());
        final List<SystemResponseDto> systems = extractSystems(result);
        assertTrue(systems.stream().anyMatch(s -> owner.systemName().equals(s.name())),
                "An authenticated viewer with X-Showcase should read the showcase owner's data.");
        assertFalse(systems.stream().anyMatch(s -> viewerOwnName.equals(s.name())),
                "The viewer's own data must not appear in a showcase view.");

        // GUEST scoping: the showcase view cannot write...
        mockMvc.perform(post(SYSTEMS_URL)
                        .header("Authorization", "Bearer " + viewerToken)
                        .header(SHOWCASE_HEADER, owner.slug())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(factory.formatSystemPayload("Should-Fail-" + uniqueSuffix(), 1, false, null)))
                .andExpect(status().isForbidden());
        // ...nor back up the showcase.
        mockMvc.perform(post("/v1/function/backup")
                        .header("Authorization", "Bearer " + viewerToken)
                        .header(SHOWCASE_HEADER, owner.slug()))
                .andExpect(status().isForbidden());
    }

    /**
     * Given both {@code X-Showcase} and {@code X-Act-As-Owner} on an ADMIN request, then {@code X-Showcase} wins:
     * the explicit read-only view request is served GUEST-scoped (write → 403) with the showcase owner's data,
     * not the impersonation target's.
     */
    @Test
    void showcaseHeader_WinsOverActAsOwner() throws Exception {
        final ShowcaseOwner owner = createShowcaseOwner();

        final String targetEmail = factory.randomEmail();
        final String targetToken = registerAndLogin(targetEmail);
        final String targetName = "Target-System-" + uniqueSuffix();
        mockMvc.perform(post(SYSTEMS_URL)
                        .header("Authorization", "Bearer " + targetToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(factory.formatSystemPayload(targetName, 1, false, null)))
                .andExpect(status().isCreated());

        final String adminEmail = factory.randomEmail();
        final String adminToken = registerAndLogin(adminEmail);
        makeAdmin(adminEmail);
        final int targetId = jdbcTemplate.queryForObject("SELECT id FROM users WHERE email = ?", Integer.class, targetEmail);

        final ResultActions result = mockMvc.perform(anonymousSearch(null)
                        .header("Authorization", "Bearer " + adminToken)
                        .header("X-Act-As-Owner", String.valueOf(targetId))
                        .header(SHOWCASE_HEADER, owner.slug()))
                .andExpect(status().isOk());
        final List<SystemResponseDto> systems = extractSystems(result);
        assertTrue(systems.stream().anyMatch(s -> owner.systemName().equals(s.name())),
                "With both headers set, X-Showcase should win and serve the showcase owner's data.");
        assertFalse(systems.stream().anyMatch(s -> targetName.equals(s.name())),
                "The impersonation target's data must not be served while X-Showcase is set.");

        // And the showcase view stays read-only even for an admin (GUEST, not the impersonated role).
        mockMvc.perform(post(SYSTEMS_URL)
                        .header("Authorization", "Bearer " + adminToken)
                        .header("X-Act-As-Owner", String.valueOf(targetId))
                        .header(SHOWCASE_HEADER, owner.slug())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(factory.formatSystemPayload("Should-Fail-" + uniqueSuffix(), 1, false, null)))
                .andExpect(status().isForbidden());

        clearPin(adminEmail);
    }

    /**
     * Given a showcase owner who lapses, then their slug stops resolving (404 — the showcase is a renewal hook)
     * but stays reserved in the database; when they derive to PAID again the same address resolves again.
     * A TRIAL owner holding a slug is likewise not visible until they are PAID.
     */
    @Test
    void showcaseVisibility_FollowsOwnersDerivedRole() throws Exception {
        // A TRIAL owner's slug does not resolve yet.
        final String email = factory.randomEmail();
        final String token = registerAndLogin(email);   // fresh account => TRIAL
        final String slug = "showcase-" + uniqueSuffix();
        grantShowcase(email, slug);
        mockMvc.perform(anonymousSearch("anything").header(SHOWCASE_HEADER, slug))
                .andExpect(status().isNotFound());

        // Once PAID, the same slug serves their collection.
        makePaid(email);
        final String systemName = "Showcase-System-" + uniqueSuffix();
        mockMvc.perform(post(SYSTEMS_URL)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(factory.formatSystemPayload(systemName, 1, false, null)))
                .andExpect(status().isCreated());
        final ResultActions visible = mockMvc.perform(anonymousSearch(null).header(SHOWCASE_HEADER, slug))
                .andExpect(status().isOk());
        assertTrue(extractSystems(visible).stream().anyMatch(s -> systemName.equals(s.name())),
                "A PAID owner's slug should serve their collection.");

        // Lapsing hides the showcase but keeps the address.
        makeLapsed(email);
        mockMvc.perform(anonymousSearch("anything").header(SHOWCASE_HEADER, slug))
                .andExpect(status().isNotFound());
        assertEquals(slug, jdbcTemplate.queryForObject(
                        "SELECT showcase_slug FROM users WHERE email = ?", String.class, email),
                "A lapsed owner's slug stays reserved in the database while resolution 404s.");

        // Renewal makes the same address resolve again.
        makePaid(email);
        mockMvc.perform(anonymousSearch("anything").header(SHOWCASE_HEADER, slug))
                .andExpect(status().isOk());
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

    // ============================ Claiming the default showcase (admin bootstrap) ============================

    /**
     * The documented bootstrap end to end via claim-on-first-login: the operator points the seeded default-showcase
     * row at their own Keycloak email and pins {@code role_override='ADMIN'} (slug and name are already seeded by
     * V1_18); their first login claims that row by email (stamping {@code keycloak_sub}), and they edit the default
     * showcase as their own collection. The write is immediately visible anonymously — both with no header (the
     * default showcase) and via the seeded slug, which resolves now that the owner derives to ADMIN — and the
     * default showcase joins the directory.
     */
    @Test
    void claimedDefaultShowcase_AdminEditsItAsTheirOwnCollection() throws Exception {
        final String originalEmail = jdbcTemplate.queryForObject(
                "SELECT email FROM users WHERE is_public_showcase", String.class);
        try {
            // The claim: the seeded default-showcase row becomes the operator's account (single ADMIN +
            // default-showcase owner). Point it at the operator's Keycloak email and pin ADMIN; the operator's
            // first login then resolves by sub → none → by email → this row, stamping the sub onto it (the claim).
            final String claimedEmail = factory.randomEmail();
            jdbcTemplate.update(
                    "UPDATE users SET email = ?, role_override = 'ADMIN' WHERE is_public_showcase",
                    claimedEmail);

            // The admin logs in (claiming the row) and edits the default showcase as their own collection (ADMIN holds WRITE).
            final String adminToken = factory.tokenFor(claimedEmail, PASSWORD);
            final String systemName = "Claimed-Showcase-System-" + uniqueSuffix();
            mockMvc.perform(post(SYSTEMS_URL)
                            .header("Authorization", "Bearer " + adminToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(factory.formatSystemPayload(systemName, 1, false, null)))
                    .andExpect(status().isCreated());

            // Anonymous, no header: the write is already in the default showcase.
            final ResultActions defaultView = mockMvc.perform(anonymousSearch(systemName)).andExpect(status().isOk());
            assertTrue(extractSystems(defaultView).stream().anyMatch(s -> systemName.equals(s.name())),
                    "An admin's write should be immediately visible in the default showcase.");

            // The seeded slug resolves now that its owner derives to ADMIN, and the directory lists it.
            final ResultActions slugView = mockMvc.perform(anonymousSearch(systemName)
                            .header(SHOWCASE_HEADER, "seths-collection"))
                    .andExpect(status().isOk());
            assertTrue(extractSystems(slugView).stream().anyMatch(s -> systemName.equals(s.name())),
                    "The claimed default showcase should also resolve via its seeded slug.");
            assertTrue(mockMvc.perform(get(SHOWCASES_URL)).andReturn().getResponse().getContentAsString()
                            .contains("\"seths-collection\""),
                    "The claimed default showcase should appear in the public directory.");
        } finally {
            // Restore the seeded row for the rest of the shared-database suite (slug/name stay as V1_18 seeded);
            // clear the stamped keycloak_sub so the row returns to its unclaimed default-showcase state.
            jdbcTemplate.update(
                    "UPDATE users SET email = ?, keycloak_sub = NULL, role_override = NULL WHERE is_public_showcase",
                    originalEmail);
        }
    }

    // ============================ Public directory ============================

    /**
     * Given the public directory {@code GET /v1/showcases}, then it lists visible showcases (slug + display
     * name only — never emails) for anonymous and authenticated callers alike, and a lapsed owner's showcase
     * drops out of the listing exactly when its slug stops resolving.
     */
    @Test
    void showcaseDirectory_ListsOnlyVisibleShowcases() throws Exception {
        final ShowcaseOwner visible = createShowcaseOwner();
        final ShowcaseOwner lapsed = createShowcaseOwner();
        makeLapsed(lapsed.email());

        final ResultActions anonymous = mockMvc.perform(get(SHOWCASES_URL)).andExpect(status().isOk());
        final String body = anonymous.andReturn().getResponse().getContentAsString();
        assertTrue(body.contains("\"" + visible.slug() + "\""),
                "A PAID owner's showcase should be listed in the public directory.");
        assertTrue(body.contains("Showcase " + visible.slug()),
                "The directory should carry the showcase's display name.");
        assertFalse(body.contains("\"" + lapsed.slug() + "\""),
                "A lapsed owner's showcase must drop out of the directory.");
        assertFalse(body.contains(visible.email()),
                "The public directory must never expose an owner's email.");

        // The directory reads identically for an authenticated caller.
        final String token = registerAndLogin(factory.randomEmail());
        mockMvc.perform(get(SHOWCASES_URL).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    // ============================ Admin showcase management ============================

    /**
     * Given an ADMIN caller, then granting a showcase (slug + name) publishes the target's collection — the DTO
     * echoes the grant, the slug resolves via {@code X-Showcase}, it appears in the directory and in
     * {@code GET /v1/admin/users} — and clearing it (null slug) unpublishes: resolution 404s, the directory and
     * DTO drop the slug and name.
     */
    @Test
    void admin_GrantsAndClearsAShowcase() throws Exception {
        final String ownerEmail = factory.randomEmail();
        final String ownerToken = registerAndLogin(ownerEmail);
        makePaid(ownerEmail);
        final int ownerId = userId(ownerEmail);
        final String systemName = "Granted-System-" + uniqueSuffix();
        mockMvc.perform(post(SYSTEMS_URL)
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(factory.formatSystemPayload(systemName, 1, false, null)))
                .andExpect(status().isCreated());

        final String adminEmail = factory.randomEmail();
        final String adminToken = registerAndLogin(adminEmail);
        makeAdmin(adminEmail);

        final String slug = "granted-" + uniqueSuffix();
        setShowcase(adminToken, ownerId, "\"" + slug + "\"", "\"A Granted Collection\"")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.showcaseSlug").value(slug))
                .andExpect(jsonPath("$.data.showcaseName").value("A Granted Collection"));

        // The grant is live: the slug resolves, and both listings surface it.
        final ResultActions view = mockMvc.perform(anonymousSearch(null).header(SHOWCASE_HEADER, slug))
                .andExpect(status().isOk());
        assertTrue(extractSystems(view).stream().anyMatch(s -> systemName.equals(s.name())),
                "A freshly granted slug should serve the owner's collection.");
        assertTrue(mockMvc.perform(get(SHOWCASES_URL)).andReturn().getResponse().getContentAsString().contains("\"" + slug + "\""),
                "A freshly granted showcase should appear in the public directory.");
        mockMvc.perform(get(ADMIN_USERS_URL).header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.id == " + ownerId + ")].showcaseSlug").value(slug));

        // Clearing unpublishes and releases nothing else: the DTO nulls both fields, resolution 404s.
        setShowcase(adminToken, ownerId, "null", "null")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.showcaseSlug").doesNotExist())
                .andExpect(jsonPath("$.data.showcaseName").doesNotExist());
        mockMvc.perform(anonymousSearch("anything").header(SHOWCASE_HEADER, slug))
                .andExpect(status().isNotFound());
        assertFalse(mockMvc.perform(get(SHOWCASES_URL)).andReturn().getResponse().getContentAsString().contains("\"" + slug + "\""),
                "A cleared showcase must drop out of the public directory.");

        clearPin(adminEmail);
    }

    /** Given a slug already held by another user, then granting it again is a 400 with a friendly message. */
    @Test
    void admin_GrantingADuplicateSlug_IsBadRequest() throws Exception {
        final ShowcaseOwner existing = createShowcaseOwner();
        final String otherEmail = factory.randomEmail();
        registerAndLogin(otherEmail);

        final String adminEmail = factory.randomEmail();
        final String adminToken = registerAndLogin(adminEmail);
        makeAdmin(adminEmail);

        setShowcase(adminToken, userId(otherEmail), "\"" + existing.slug() + "\"", "\"Duplicate\"")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0]").value(
                        "Failed Input Validation: The showcase slug '" + existing.slug() + "' is already taken."));

        clearPin(adminEmail);
    }

    /** Given a malformed slug, then the grant is rejected with 400 before touching the database. */
    @Test
    void admin_GrantingAnInvalidSlug_IsBadRequest() throws Exception {
        final String targetEmail = factory.randomEmail();
        registerAndLogin(targetEmail);
        final int targetId = userId(targetEmail);

        final String adminEmail = factory.randomEmail();
        final String adminToken = registerAndLogin(adminEmail);
        makeAdmin(adminEmail);

        for (String invalid : new String[]{"Has-Uppercase", "-leading-hyphen", "trailing-hyphen-", "double--hyphen", "under_score", "spa ce"}) {
            setShowcase(adminToken, targetId, "\"" + invalid + "\"", "\"Bad Slug\"")
                    .andExpect(status().isBadRequest());
        }

        clearPin(adminEmail);
    }

    /** Given an unknown user id, then the grant is a 404; given a non-admin caller, then the route is 403. */
    @Test
    void showcaseGrant_UnknownUser404_NonAdmin403() throws Exception {
        final String adminEmail = factory.randomEmail();
        final String adminToken = registerAndLogin(adminEmail);
        makeAdmin(adminEmail);
        setShowcase(adminToken, 999_999, "\"orphan-slug\"", "\"Nobody\"")
                .andExpect(status().isNotFound());
        clearPin(adminEmail);

        final String plainToken = registerAndLogin(factory.randomEmail());   // TRIAL
        setShowcase(plainToken, 1, "\"forbidden-slug\"", "\"Nope\"")
                .andExpect(status().isForbidden());
    }

    // ------------------------------- Private helpers -------------------------------

    /**
     * A search request for systems matching the given name (or an unfiltered list when {@code name} is null);
     * add Authorization/X-Showcase headers as needed.
     */
    private MockHttpServletRequestBuilder anonymousSearch(String name) {
        final String payload = name == null
                ? factory.formatFiltersPayload(new ArrayList<Filter>())
                : factory.formatFiltersPayload(
                        new Filter(Keychain.SYSTEM_KEY, Filter.FIELD_TYPE_TEXT, "name", Filter.OPERATOR_EQUALS, name, false));
        return post(SEARCH_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload);
    }

    /** A paid user with a public showcase: registered, promoted to PAID, granted a slug, owning one system. */
    private record ShowcaseOwner(String email, String slug, String systemName) {
    }

    private ShowcaseOwner createShowcaseOwner() throws Exception {
        final String email = factory.randomEmail();
        final String token = registerAndLogin(email);
        makePaid(email);
        final String slug = "showcase-" + uniqueSuffix();
        grantShowcase(email, slug);
        final String systemName = "Showcase-System-" + uniqueSuffix();
        mockMvc.perform(post(SYSTEMS_URL)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(factory.formatSystemPayload(systemName, 1, false, null)))
                .andExpect(status().isCreated());
        return new ShowcaseOwner(email, slug, systemName);
    }

    /**
     * Grant a showcase slug directly via JDBC — the admin grant endpoint's job, done at the database level so
     * these resolution tests do not depend on the management API.
     */
    private void grantShowcase(String email, String slug) {
        jdbcTemplate.update("UPDATE users SET showcase_slug = ?, showcase_name = ? WHERE email = ?",
                slug, "Showcase " + slug, email);
    }

    /**
     * Expire a user's access window so they resolve to LAPSED on the next request — simulates the (deferred)
     * Paddle billing webhook, which has no in-app code path yet.
     */
    private void makeLapsed(String email) {
        jdbcTemplate.update(
                "UPDATE users SET plan = 'paid', subscription_status = 'past_due', "
                        + "access_until = now() - interval '1 day' WHERE email = ?",
                email);
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

    /** POST the showcase grant; pass JSON literals (quoted strings or null) for the slug and name. */
    private ResultActions setShowcase(String token, int id, String slugJson, String nameJson) throws Exception {
        return mockMvc.perform(post(ADMIN_USERS_URL + "/" + id + "/showcase")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"slug\":" + slugJson + ",\"name\":" + nameJson + "}"));
    }

    private int userId(String email) {
        return jdbcTemplate.queryForObject("SELECT id FROM users WHERE email = ?", Integer.class, email);
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
        return factory.tokenForProvisioned(email, PASSWORD);
    }
}

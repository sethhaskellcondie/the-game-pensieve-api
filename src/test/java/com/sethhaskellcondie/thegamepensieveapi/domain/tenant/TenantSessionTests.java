package com.sethhaskellcondie.thegamepensieveapi.domain.tenant;

import com.sethhaskellcondie.thegamepensieveapi.TestFactory;
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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Pins the per-request tenant boundary that {@code TenantTransactionFilter} establishes, observed from outside the
 * filter: every tenant-scoped request runs on a connection demoted to {@code app_rls} with {@code app.current_owner}
 * set (so RLS hides other owners' rows), both settings are transaction-local (so a pooled connection is never left
 * demoted), the skipped paths still answer without that transaction, and an unknown {@code X-Showcase} slug is a 404
 * in the standard envelope.
 *
 * <p>These are the behaviors that have to survive moving the two session statements behind a repository.
 */
@SpringBootTest
@ActiveProfiles("test-container")
@AutoConfigureMockMvc
public class TenantSessionTests {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    private TestFactory factory;

    @BeforeEach
    void setUp() {
        factory = new TestFactory(mockMvc);
    }

    @Test
    void request_AnotherOwnersRow_IsInvisible() throws Exception {
        final int otherOwnerId = insertUser();
        final String name = "Tenant-Hidden-" + uniqueSuffix();
        final int hiddenId = insertSystemOwnedBy(name, otherOwnerId);

        // Demoted role + app.current_owner means RLS hides the row: not readable by id...
        mockMvc.perform(get("/v1/systems/" + hiddenId)).andExpect(status().isNotFound());
        // ...and absent from search.
        mockMvc.perform(post("/v1/systems/function/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(searchByNamePayload(name)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    @Test
    void request_OwnRow_IsVisible() throws Exception {
        final SystemResponseDto system = factory.postSystem();

        // The same boundary must not hide the acting owner's own rows.
        mockMvc.perform(get("/v1/systems/" + system.id())).andExpect(status().isOk());
        mockMvc.perform(post("/v1/systems/function/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(searchByNamePayload(system.name())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1));
    }

    @Test
    void afterRequests_PooledConnection_IsNotLeftDemotedOrScoped() throws Exception {
        mockMvc.perform(get("/v1/function/counts")).andExpect(status().isOk());
        factory.postSystem();

        // SET LOCAL ROLE / set_config(..., true) are transaction-local, so a connection handed back to the pool
        // must come back with normal privileges and no tenant set.
        final String role = jdbcTemplate.queryForObject("SELECT current_user", String.class);
        assertNotEquals("app_rls", role, "the app_rls demotion must not outlive the request transaction");

        final String owner = jdbcTemplate.queryForObject("SELECT current_setting('app.current_owner', true)", String.class);
        assertTrue(owner == null || owner.isBlank(), "app.current_owner must not outlive the request transaction, was: " + owner);
    }

    @Test
    void skippedPaths_AnswerWithoutTheTenantTransaction() throws Exception {
        // These read the users table, which app_rls has no grant on — they must stay outside the boundary.
        mockMvc.perform(get("/v1/heartbeat")).andExpect(status().isOk());
        mockMvc.perform(get("/v1/showcases")).andExpect(status().isOk());
        mockMvc.perform(get("/v1/auth/me")).andExpect(status().isOk());
    }

    @Test
    void unknownShowcaseSlug_Is404AndDoesNotEchoTheSlug() throws Exception {
        final String slug = "no-such-showcase-" + uniqueSuffix();

        mockMvc.perform(get("/v1/function/counts").header("X-Showcase", slug))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.data").doesNotExist())
                .andExpect(jsonPath("$.errors[0]").value("No public showcase exists for the requested X-Showcase slug."))
                .andExpect(result -> assertFalse(result.getResponse().getContentAsString().contains(slug),
                        "the 404 body must not echo the requested slug back"));
    }

    @Test
    void handledFailureInsideTheTransaction_StillReturnsItsErrorEnvelope() throws Exception {
        final SystemResponseDto system = factory.postSystem();

        // A duplicate insert fails validation inside the request transaction; the filter swallows the resulting
        // rollback so the caller still gets the error envelope rather than a 500.
        mockMvc.perform(post("/v1/systems")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(factory.formatSystemPayload(system.name(), system.generation(), system.handheld(), null)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors").isNotEmpty());
    }

    // ------------------------------- Private helpers -------------------------------

    private String searchByNamePayload(String name) {
        return factory.formatFiltersPayload(new Filter("system", Filter.FIELD_TYPE_TEXT, "name", Filter.OPERATOR_EQUALS, name, false));
    }

    /** Seeds a plain (non-showcase) user directly, bypassing the tenant filter, and returns its id. */
    private int insertUser() {
        final String email = "tenant-session-" + java.util.UUID.randomUUID() + "@example.com";
        return jdbcTemplate.queryForObject("INSERT INTO users(email) VALUES (?) RETURNING id", Integer.class, email);
    }

    /**
     * Insert a system owned by another user directly via JDBC — the API can only ever write rows for the acting
     * owner, so seeding another tenant's row has to sidestep the filter (the test connection is a superuser and
     * bypasses RLS).
     */
    private int insertSystemOwnedBy(String name, int ownerId) {
        return jdbcTemplate.queryForObject(
                "INSERT INTO systems(name, generation, handheld, owner_id, created_at, updated_at) "
                        + "VALUES (?, 1, false, ?, now(), now()) RETURNING id",
                Integer.class, name, ownerId);
    }

    private String uniqueSuffix() {
        // Controller (@SpringBootTest) tests commit to a shared Testcontainers DB, so names must be unique.
        return java.util.UUID.randomUUID().toString().substring(0, 8);
    }
}

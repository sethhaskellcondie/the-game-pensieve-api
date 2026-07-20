package com.sethhaskellcondie.thegamepensieveapi;

import java.nio.charset.StandardCharsets;
import java.util.List;

import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Seeds the multirole test data set documented in {@code documentation/Notes.md} ("Seeding Multirole Test
 * Data"): one bootstrap admin, eight users covering TRIAL/PAID/LAPSED, two public showcases, and a populated
 * default showcase — everything needed to exercise each role and the showcase-switching features against
 * realistic multi-user data.
 *
 * <p>This fixture is the integration-test consumer of the seed set; {@code scripts/seed-test-data.sh} is the
 * live-environment consumer. Both run the <em>same choreography</em> over the <em>same seed files</em> in
 * {@code src/main/resources/seeders/} so they never drift: obtain a Keycloak token (the account is created on
 * demand), make a first authenticated call so the {@code users} row is JIT-provisioned, pin PAID so the account
 * may import, import as the user so RLS stamps every row to that owner, then pin the final role. Final roles are
 * always pinned, never left to derivation — a derived TRIAL would silently lapse when its {@code access_until}
 * window passes, rotting the fixtures.
 *
 * <p>Idempotent: re-running against an already-seeded database is a no-op (Keycloak user creation and JIT
 * provisioning tolerate an existing account/row, imports resolve existing rows by name/title, pins and slug
 * grants re-apply cleanly).
 *
 * <p>Requires the {@code secured} profile (the choreography exercises the real auth/role/RLS stack) and a
 * working directory of the repo root (the default-showcase step calls {@code POST /v1/function/seedSampleData},
 * which reads {@code sampleData.json} from the working directory — true under {@code ./mvnw test}).
 */
public class SeededUsersFixture {

    /** One seeded account: its credentials, the role it is pinned to after import, and its data set. */
    public record SeededUser(String email, String password, String finalRole, String seedFile, String showcaseSlug, String showcaseName) {
    }

    public static final String ADMIN_EMAIL = "seeder-admin@email.com";
    public static final String ADMIN_PASSWORD = "seeder-admin";
    /** The seeded default-showcase marker row (V1_13) that anonymous no-header requests resolve to. */
    public static final String DEFAULT_SHOWCASE_EMAIL = "showcase@internal.local";

    public static final SeededUser TRIAL_1 = new SeededUser("trial1@email.com", "trial1", "TRIAL", "seedTrialData1.json", null, null);
    public static final SeededUser TRIAL_2 = new SeededUser("trial2@email.com", "trial2", "TRIAL", "seedTrialData2.json", null, null);
    public static final SeededUser PAID_1 = new SeededUser("paid1@email.com", "paid1", "PAID", "seedPaidData1.json", null, null);
    public static final SeededUser PAID_2 = new SeededUser("paid2@email.com", "paid2", "PAID", "seedPaidData2.json", null, null);
    public static final SeededUser LAPSED_1 = new SeededUser("lapsed1@email.com", "lapsed1", "LAPSED", "seedLapsedData1.json", null, null);
    public static final SeededUser LAPSED_2 = new SeededUser("lapsed2@email.com", "lapsed2", "LAPSED", "seedLapsedData2.json", null, null);
    public static final SeededUser SHOWCASE_1 = new SeededUser("showcase1@email.com", "showcase1", "PAID", "seedShowcaseData1.json", "showcase-one", "Showcase One");
    public static final SeededUser SHOWCASE_2 = new SeededUser("showcase2@email.com", "showcase2", "PAID", "seedShowcaseData2.json", "showcase-two", "Showcase Two");

    public static final List<SeededUser> SEEDED_USERS = List.of(TRIAL_1, TRIAL_2, PAID_1, PAID_2, LAPSED_1, LAPSED_2, SHOWCASE_1, SHOWCASE_2);

    private final MockMvc mockMvc;
    private final JdbcTemplate jdbcTemplate;
    private final TestFactory factory;

    public SeededUsersFixture(MockMvc mockMvc, JdbcTemplate jdbcTemplate) {
        this.mockMvc = mockMvc;
        this.jdbcTemplate = jdbcTemplate;
        this.factory = new TestFactory(mockMvc);
    }

    /** Run the full choreography: admin bootstrap, the eight users + their data, showcase grants, default showcase. */
    public void seedAll() throws Exception {
        final String adminToken = bootstrapAdmin();
        for (SeededUser user : SEEDED_USERS) {
            seedUser(adminToken, user);
        }
        seedDefaultShowcase(adminToken);
    }

    /**
     * Provision the fixture admin (Keycloak account + JIT {@code users} row) and pin it via the same direct SQL the
     * documented bootstrap uses (there is no in-app endpoint that creates admins). Any admin pinned by another test
     * class in the shared Testcontainers database is cleared first — the {@code uq_users_single_admin} index allows
     * exactly one.
     */
    public String bootstrapAdmin() throws Exception {
        final String token = login(ADMIN_EMAIL, ADMIN_PASSWORD);
        provision(token);
        jdbcTemplate.update("UPDATE users SET role_override = NULL WHERE role_override = 'ADMIN' AND email <> ?", ADMIN_EMAIL);
        jdbcTemplate.update("UPDATE users SET role_override = 'ADMIN' WHERE email = ?", ADMIN_EMAIL);
        return token;
    }

    /** Ensure a Keycloak account exists for the given credentials and return a fresh RS256 access token for it. */
    public String login(String email, String password) throws Exception {
        return factory.tokenFor(email, password);
    }

    public String login(SeededUser user) throws Exception {
        return login(user.email(), user.password());
    }

    public int userId(String email) {
        return jdbcTemplate.queryForObject("SELECT id FROM users WHERE email = ?", Integer.class, email);
    }

    private void seedUser(String adminToken, SeededUser user) throws Exception {
        final String userToken = login(user);
        // First authenticated call JIT-provisions the users row (30-day trial) so the admin can then pin its role.
        provision(userToken);
        final int id = userId(user.email());
        // A JIT-provisioned account derives to TRIAL, which lacks IMPORT — pin PAID so the account can load its data.
        pinRole(adminToken, id, "PAID");
        importSeedFile(userToken, user.seedFile());
        pinRole(adminToken, id, user.finalRole());
        if (user.showcaseSlug() != null) {
            grantShowcase(adminToken, id, user.showcaseSlug(), user.showcaseName());
        }
    }

    /** Trigger JIT provisioning of the token owner's {@code users} row by making its first authenticated call. */
    private void provision(String token) throws Exception {
        mockMvc.perform(get("/v1/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    /**
     * Populate the default showcase: the seeded marker row is unloggable (password hash {@code '!'}), so the
     * admin imports for it through writable impersonation. Impersonation adopts the <em>target's</em> role, and
     * the unpinned marker row derives to LAPSED (no IMPORT) — so it is pinned PAID for the import and cleared
     * after (anonymous resolution to the default showcase does not depend on its role).
     */
    private void seedDefaultShowcase(String adminToken) throws Exception {
        final int defaultOwnerId = userId(DEFAULT_SHOWCASE_EMAIL);
        pinRole(adminToken, defaultOwnerId, "PAID");
        mockMvc.perform(post("/v1/function/seedSampleData")
                        .header("Authorization", "Bearer " + adminToken)
                        .header("X-Act-As-Owner", String.valueOf(defaultOwnerId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.errors").isEmpty());
        pinRole(adminToken, defaultOwnerId, null);
    }

    /** Pin (or with {@code null}, clear) a user's role override through the admin API. */
    private void pinRole(String adminToken, int id, String role) throws Exception {
        final String roleJson = role == null ? "null" : "\"" + role + "\"";
        mockMvc.perform(post("/v1/admin/users/" + id + "/role")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roleOverride\":" + roleJson + "}"))
                .andExpect(status().isOk());
    }

    private void grantShowcase(String adminToken, int id, String slug, String name) throws Exception {
        mockMvc.perform(post("/v1/admin/users/" + id + "/showcase")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"slug\":\"" + slug + "\",\"name\":\"" + name + "\"}"))
                .andExpect(status().isOk());
    }

    /** Import a seed file as the given user: the file is the bare backup shape, wrapped under the "data" key. */
    private void importSeedFile(String token, String seedFile) throws Exception {
        final String fileContent = new String(
                new ClassPathResource("seeders/" + seedFile).getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        mockMvc.perform(post("/v1/function/import")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"data\":" + fileContent + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.errors").isEmpty());
    }
}

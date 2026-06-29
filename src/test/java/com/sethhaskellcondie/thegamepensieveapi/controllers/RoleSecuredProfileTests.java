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

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Role-based access model under the {@code secured} profile. The five roles — GUEST, TRIAL, PAID, LAPSED,
 * ADMIN — are auto-derived per request from {@code access_until}/{@code subscription_status} (plus a future
 * admin {@code role_override} pin), and the capability matrix is the single source of truth for what each may do.
 *
 * <p><strong>TRIAL (newly registered, trial access window)</strong>
 * <ul>
 *   <li>Given a new user makes an account, then that account is granted a trial access window and resolves to TRIAL.</li>
 *   <li>Given a TRIAL account, then they can write new rows to the tables.</li>
 *   <li>Given a TRIAL account, then those requests can include filters in them.</li>
 * </ul>
 *
 * <p><strong>GUEST (anonymous showcase request)</strong>
 * <ul>
 *   <li>Given a GUEST request, then it can read and filter data — but only the showcase data.</li>
 *   <li>Given a GUEST request, then it cannot write to the system.</li>
 * </ul>
 *
 * <p><strong>LAPSED (authenticated, access window expired)</strong>
 * <ul>
 *   <li>Given a LAPSED account, then it can read its own data and back that data up.</li>
 *   <li>Given a LAPSED account, then its search requests cannot include filters (402).</li>
 * </ul>
 */
@SpringBootTest
@ActiveProfiles({"test-container", "secured"})
@AutoConfigureMockMvc
public class RoleSecuredProfileTests {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    private TestFactory factory;
    private static final String PASSWORD = "Sup3rSecret!";
    private static final String SYSTEMS_URL = "/v1/systems";
    private static final String BACKUP_URL = "/v1/function/backup";
    private static final String IMPORT_URL = "/v1/function/import";
    // A well-formed but empty backup; importing it is a valid no-op that returns 200 (creates nothing).
    private static final String EMPTY_IMPORT_BODY =
            "{\"data\":{\"customFields\":[],\"toys\":[],\"systems\":[],\"videoGameBoxes\":[],\"boardGameBoxes\":[],\"metadata\":[]}}";

    @BeforeEach
    void setUp() {
        factory = new TestFactory(mockMvc);
    }

    // ============================ TRIAL (newly registered) ============================

    /** Given a new user makes an account, then that account is granted a trial window and resolves to TRIAL. */
    @Test
    void newAccount_ResolvesToTrialRole() throws Exception {
        final String email = factory.randomEmail();
        factory.registerReturnResult(email, PASSWORD).andExpect(status().isCreated());

        final Timestamp accessUntil = jdbcTemplate.queryForObject(
                "SELECT access_until FROM users WHERE email = ?", Timestamp.class, email);
        final String subscriptionStatus = jdbcTemplate.queryForObject(
                "SELECT subscription_status FROM users WHERE email = ?", String.class, email);

        assertNotNull(accessUntil, "A newly registered account should be granted a trial access window.");
        assertTrue(accessUntil.toInstant().isAfter(Instant.now()),
                "The trial access window should extend into the future.");
        assertEquals("trialing", subscriptionStatus,
                "A newly registered account should be marked as trialing, so it derives to the TRIAL role.");
    }

    /** Given a TRIAL account, then they can write new rows to the tables. */
    @Test
    void trialAccount_CanWriteNewRows() throws Exception {
        final String token = registerAndLogin(factory.randomEmail());

        mockMvc.perform(post(SYSTEMS_URL)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(factory.formatSystemPayload("Sys-" + uniqueSuffix(), 1, false, null)))
                .andExpect(status().isCreated());
    }

    /** Given a TRIAL account, then those requests can include filters in them. */
    @Test
    void trialAccount_RequestsCanIncludeFilters() throws Exception {
        final String token = registerAndLogin(factory.randomEmail());

        searchSystems(token, factory.formatFiltersPayload(nameFilter("anything")))
                .andExpect(status().isOk());
    }

    // ============================ PAID (authenticated, active subscription) ============================

    /**
     * Given a PAID account (active subscription, not a trial), then it holds every own-data capability —
     * write, filter, backup, and import — the full PAID matrix row minus ACCESS_ADMIN. Import is the capability
     * newly gated this phase ({@code BackupImportGateway#importBackupData}), and PAID passes it.
     */
    @Test
    void paidAccount_CanWriteFilterBackupAndImport() throws Exception {
        final String email = factory.randomEmail();
        final String token = registerAndLogin(email);
        makePaid(email);

        // write
        createSystemAs(token, "Paid-System-" + uniqueSuffix());
        // filter
        searchSystems(token, factory.formatFiltersPayload(nameFilter("anything")))
                .andExpect(status().isOk());
        // backup
        mockMvc.perform(post(BACKUP_URL).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        // import
        mockMvc.perform(post(IMPORT_URL)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(EMPTY_IMPORT_BODY))
                .andExpect(status().isOk());
    }

    // ============================ GUEST (anonymous showcase) ============================

    /** Given a GUEST request, then it can read and filter the showcase data. */
    @Test
    void guestRequest_CanReadAndFilterShowcaseData() throws Exception {
        final String showcaseName = "Showcase-System-" + uniqueSuffix();
        seedSystemOwnedByShowcase(showcaseName);

        final ResultActions result = searchSystems(null, factory.formatFiltersPayload(nameFilter(showcaseName)))
                .andExpect(status().isOk());
        assertTrue(extractSystems(result).stream().anyMatch(s -> showcaseName.equals(s.name())),
                "A GUEST (anonymous) request should be able to read and filter the showcase data.");
    }

    /** Given a GUEST request, then it sees only the showcase data — never a private owner's rows. */
    @Test
    void guestRequest_SeesOnlyShowcaseData() throws Exception {
        final String privateName = "Private-System-" + uniqueSuffix();
        final String ownerToken = registerAndLogin(factory.randomEmail());
        createSystemAs(ownerToken, privateName);

        final ResultActions result = searchSystems(null, factory.formatFiltersPayload(nameFilter(privateName)))
                .andExpect(status().isOk());
        assertFalse(extractSystems(result).stream().anyMatch(s -> privateName.equals(s.name())),
                "A GUEST (anonymous) request must not see another owner's private data.");
    }

    /** Given a GUEST request, then it cannot write to the system (blocked at Spring Security as anonymous → 401). */
    @Test
    void guestRequest_CannotWrite() throws Exception {
        mockMvc.perform(post(SYSTEMS_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(factory.formatSystemPayload("Sys-" + uniqueSuffix(), 1, false, null)))
                .andExpect(status().isUnauthorized());
    }

    /**
     * Given a GUEST request, then it cannot back up data. The GUEST role lacks the BACKUP capability (the gate is
     * centralized in BackupImportGateway), but an anonymous request is already rejected at Spring Security (401)
     * before it ever reaches that gate.
     */
    @Test
    void guestRequest_CannotBackUp() throws Exception {
        mockMvc.perform(post(BACKUP_URL))
                .andExpect(status().isUnauthorized());
    }

    /** Given a GUEST request, then it cannot import data — anonymous → 401 at Spring Security. */
    @Test
    void guestRequest_CannotImport() throws Exception {
        mockMvc.perform(post(IMPORT_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());
    }

    // ============================ LAPSED (authenticated, expired) ============================

    /** Given a LAPSED account, then it can read its own data and back that data up. */
    @Test
    void lapsedAccount_CanReadAndBackUpOwnData() throws Exception {
        final String email = factory.randomEmail();
        final String token = registerAndLogin(email);
        final String ownName = "Own-System-" + uniqueSuffix();
        createSystemAs(token, ownName);          // created while still on the trial (TRIAL)
        makeLapsed(email);

        // Reads its own data: an unfiltered list still returns the owner's rows.
        final ResultActions list = searchSystems(token, factory.formatFiltersPayload(new ArrayList<Filter>()))
                .andExpect(status().isOk());
        assertTrue(extractSystems(list).stream().anyMatch(s -> ownName.equals(s.name())),
                "A LAPSED account should still be able to read its own data.");

        // Backs up its data.
        mockMvc.perform(post(BACKUP_URL).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    /** Given a LAPSED account, then its search requests cannot include filters. */
    @Test
    void lapsedAccount_CannotIncludeFiltersInSearch() throws Exception {
        final String email = factory.randomEmail();
        final String token = registerAndLogin(email);
        makeLapsed(email);

        searchSystems(token, factory.formatFiltersPayload(nameFilter("anything")))
                .andExpect(status().isPaymentRequired());
    }

    // ------------------------------- Private helpers -------------------------------

    private Filter nameFilter(String name) {
        return new Filter(Keychain.SYSTEM_KEY, Filter.FIELD_TYPE_TEXT, "name", Filter.OPERATOR_EQUALS, name, false);
    }

    /** POST a system search; pass a null token for an anonymous (GUEST) request. */
    private ResultActions searchSystems(String token, String filtersPayload) throws Exception {
        var request = post(SYSTEMS_URL + "/function/search")
                .contentType(MediaType.APPLICATION_JSON)
                .content(filtersPayload);
        if (token != null) {
            request = request.header("Authorization", "Bearer " + token);
        }
        return mockMvc.perform(request);
    }

    private List<SystemResponseDto> extractSystems(ResultActions result) throws Exception {
        return factory.extractDataList(result, new TypeReference<List<SystemResponseDto>>() { });
    }

    private void createSystemAs(String token, String name) throws Exception {
        mockMvc.perform(post(SYSTEMS_URL)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(factory.formatSystemPayload(name, 1, false, null)))
                .andExpect(status().isCreated());
    }

    /**
     * Insert a system owned by the seeded public showcase owner, directly via JDBC (the secured API can't create
     * showcase-owned rows). The explicit owner_id and the superuser test connection sidestep RLS/the tenant filter.
     */
    private void seedSystemOwnedByShowcase(String name) {
        final int showcaseOwnerId = jdbcTemplate.queryForObject(
                "SELECT id FROM users WHERE is_public_showcase = true", Integer.class);
        jdbcTemplate.update(
                "INSERT INTO systems(name, generation, handheld, owner_id, created_at, updated_at) "
                        + "VALUES (?, 1, false, ?, now(), now())",
                name, showcaseOwnerId);
    }

    /**
     * Promote a user to an active paid subscription so they resolve to PAID (not TRIAL) on the next request —
     * a future access window with {@code subscription_status='active'} rather than {@code 'trialing'}.
     *
     * <p>This writes the billing columns directly to <em>simulate the (deferred) Paddle billing webhook</em>:
     * those subscription-state transitions have no in-app code path yet — the only production write to these
     * columns is the trial grant at registration — so the test stamps the resulting state itself.
     */
    private void makePaid(String email) {
        jdbcTemplate.update(
                "UPDATE users SET plan = 'paid', subscription_status = 'active', "
                        + "access_until = now() + interval '30 days' WHERE email = ?",
                email);
    }

    /**
     * Expire a user's access window so they resolve to LAPSED on the next request (the role is read per-request).
     *
     * <p>Like {@link #makePaid}, this writes the billing columns directly to <em>simulate the (deferred) Paddle
     * billing webhook</em> — a past-due subscription whose access window has elapsed — since no in-app code path
     * performs that transition yet.
     */
    private void makeLapsed(String email) {
        jdbcTemplate.update(
                "UPDATE users SET plan = 'paid', subscription_status = 'past_due', "
                        + "access_until = now() - interval '1 day' WHERE email = ?",
                email);
    }

    private String uniqueSuffix() {
        return java.util.UUID.randomUUID().toString().substring(0, 8);
    }

    private String registerAndLogin(String email) throws Exception {
        factory.registerReturnResult(email, PASSWORD).andExpect(status().isCreated());
        return factory.extractToken(factory.loginReturnResult(email, PASSWORD), "accessToken");
    }
}
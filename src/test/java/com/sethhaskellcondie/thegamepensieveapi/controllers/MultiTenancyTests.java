package com.sethhaskellcondie.thegamepensieveapi.controllers;

import com.fasterxml.jackson.core.type.TypeReference;
import com.sethhaskellcondie.thegamepensieveapi.TestFactory;
import com.sethhaskellcondie.thegamepensieveapi.domain.Keychain;
import com.sethhaskellcondie.thegamepensieveapi.domain.backupimport.BackupDataDto;
import com.sethhaskellcondie.thegamepensieveapi.domain.entity.system.SystemResponseDto;
import com.sethhaskellcondie.thegamepensieveapi.domain.entity.toy.ToyResponseDto;
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

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Runs under the {@code secured} profile so that two distinct authenticated owners (A and B) exist. The
 * Phase-2 promise is that one owner's data is completely invisible and immutable to another: searches,
 * reads-by-id, updates, and deletes all behave as though the other owner's rows don't exist.
 * <p>
 * Counterpart at the database layer: {@code domain.tenant.RowLevelSecurityTests} proves the same isolation
 * holds against a handwritten {@code SELECT *}.
 * <p>
 * An authenticated user sees ONLY their own rows — never the public showcase owner's. The showcase is
 * reachable solely through the anonymous/public build (where unauthenticated requests resolve to the
 * showcase owner); it never bleeds into a logged-in user's results. {@link #search_ShowcaseOwnersSystem_NotReturned}
 * and {@link #getById_ShowcaseOwnersSystem_NotFound} pin that boundary.
 */
@SpringBootTest
@ActiveProfiles({"test-container", "secured"})
@AutoConfigureMockMvc
public class MultiTenancyTests {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    private TestFactory factory;
    private static final String PASSWORD = "Sup3rSecret!";
    private static final String SYSTEMS_URL = "/v1/systems";
    private static final String TOYS_URL = "/v1/toys";
    private static final String BACKUP_URL = "/v1/function/backup";

    @BeforeEach
    void setUp() {
        factory = new TestFactory(mockMvc);
    }

    // ----------------------------- Systems: read isolation -----------------------------

    @Test
    void search_OtherUsersSystem_NotReturned() throws Exception {
        final String tokenA = registerAndLogin();
        final String tokenB = registerAndLogin();
        final String systemName = "A-System-" + uniqueSuffix();

        createSystemAs(tokenA, systemName);

        // The owner finds their own row...
        assertTrue(searchSystemsByNameAs(tokenA, systemName).stream().anyMatch(s -> systemName.equals(s.name())),
                "Owner A should see their own system in search results.");
        // ...but another user must not.
        assertFalse(searchSystemsByNameAs(tokenB, systemName).stream().anyMatch(s -> systemName.equals(s.name())),
                "User B must not see user A's system in search results.");
    }

    @Test
    void getById_OtherUsersSystem_NotFound() throws Exception {
        final String tokenA = registerAndLogin();
        final String tokenB = registerAndLogin();
        final int systemId = createSystemAs(tokenA, "A-System-" + uniqueSuffix()).id();

        // Owner can read it.
        mockMvc.perform(get(SYSTEMS_URL + "/" + systemId).header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk());
        // Another user gets a 404 — the row is invisible to them.
        mockMvc.perform(get(SYSTEMS_URL + "/" + systemId).header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isNotFound());
    }

    // ----------------------------- Systems: write isolation -----------------------------

    @Test
    void update_OtherUsersSystem_NotFound() throws Exception {
        final String tokenA = registerAndLogin();
        final String tokenB = registerAndLogin();
        final int systemId = createSystemAs(tokenA, "A-System-" + uniqueSuffix()).id();

        final String payload = factory.formatSystemPayload("HijackAttempt-" + uniqueSuffix(), 9, true, null);
        mockMvc.perform(put(SYSTEMS_URL + "/" + systemId)
                        .header("Authorization", "Bearer " + tokenB)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isNotFound());
    }

    @Test
    void delete_OtherUsersSystem_NotFound() throws Exception {
        final String tokenA = registerAndLogin();
        final String tokenB = registerAndLogin();
        final int systemId = createSystemAs(tokenA, "A-System-" + uniqueSuffix()).id();

        mockMvc.perform(delete(SYSTEMS_URL + "/" + systemId).header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isNotFound());
    }

    // ----------------------------- Insert ownership round-trip -----------------------------

    @Test
    void createdSystem_IsVisibleToOwnerOnReread() throws Exception {
        final String tokenA = registerAndLogin();
        final String systemName = "A-System-" + uniqueSuffix();
        final int systemId = createSystemAs(tokenA, systemName).id();

        // A row A creates is stamped to A and round-trips back to A on a fresh read.
        mockMvc.perform(get(SYSTEMS_URL + "/" + systemId).header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk());
        assertTrue(searchSystemsByNameAs(tokenA, systemName).stream().anyMatch(s -> systemName.equals(s.name())),
                "Owner A should find the system they just created.");
    }

    // ----------------------------- Custom-field-bearing entity: toys -----------------------------

    @Test
    void search_OtherUsersToy_NotReturned() throws Exception {
        final String tokenA = registerAndLogin();
        final String tokenB = registerAndLogin();
        final String toyName = "A-Toy-" + uniqueSuffix();

        createToyAs(tokenA, toyName, "A-Set-" + uniqueSuffix());

        assertTrue(searchToysByNameAs(tokenA, toyName).stream().anyMatch(t -> toyName.equals(t.name())),
                "Owner A should see their own toy in search results.");
        assertFalse(searchToysByNameAs(tokenB, toyName).stream().anyMatch(t -> toyName.equals(t.name())),
                "User B must not see user A's toy in search results.");
    }

    // ----------------------------- Showcase owner is not visible to authenticated users -----------------------------

    @Test
    void search_ShowcaseOwnersSystem_NotReturned() throws Exception {
        final String showcaseName = "Showcase-System-" + uniqueSuffix();
        seedSystemOwnedByShowcase(showcaseName);
        final String token = registerAndLogin();

        // The public showcase is reachable only by the anonymous build — a logged-in user sees only their own rows.
        assertFalse(searchSystemsByNameAs(token, showcaseName).stream().anyMatch(s -> showcaseName.equals(s.name())),
                "An authenticated user must not see the showcase owner's system in search results.");
    }

    @Test
    void getById_ShowcaseOwnersSystem_NotFound() throws Exception {
        final int showcaseSystemId = seedSystemOwnedByShowcase("Showcase-System-" + uniqueSuffix());
        final String token = registerAndLogin();

        mockMvc.perform(get(SYSTEMS_URL + "/" + showcaseSystemId).header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    // ----------------------------- Backup: content isolation -----------------------------

    /**
     * The backup endpoint returns the caller's entire collection in one payload, so a status code alone can't
     * prove isolation (the capability tests in {@code RoleSecuredProfileTests} already pin that backup is
     * permitted). This asserts on the payload itself: owner A's backup carries A's rows and none of user B's
     * or the showcase owner's — the backup read path is scoped by RLS like every other read.
     */
    @Test
    void backup_ContainsOnlyOwnData() throws Exception {
        final String tokenA = registerAndLogin();
        final String tokenB = registerAndLogin();
        final String systemNameA = "A-System-" + uniqueSuffix();
        final String toyNameA = "A-Toy-" + uniqueSuffix();
        final String systemNameB = "B-System-" + uniqueSuffix();
        final String toyNameB = "B-Toy-" + uniqueSuffix();
        final String showcaseName = "Showcase-System-" + uniqueSuffix();

        createSystemAs(tokenA, systemNameA);
        createToyAs(tokenA, toyNameA, "A-Set-" + uniqueSuffix());
        createSystemAs(tokenB, systemNameB);
        createToyAs(tokenB, toyNameB, "B-Set-" + uniqueSuffix());
        seedSystemOwnedByShowcase(showcaseName);

        final BackupDataDto backup = backupAs(tokenA);
        final Set<String> systemNames = backup.systems().stream().map(SystemResponseDto::name).collect(Collectors.toSet());
        final Set<String> toyNames = backup.toys().stream().map(ToyResponseDto::name).collect(Collectors.toSet());

        // The backup contains the owner's own rows...
        assertTrue(systemNames.contains(systemNameA), "Owner A's backup should contain their own system.");
        assertTrue(toyNames.contains(toyNameA), "Owner A's backup should contain their own toy.");
        // ...and never another owner's or the showcase owner's.
        assertFalse(systemNames.contains(systemNameB), "Owner A's backup must not contain user B's system.");
        assertFalse(toyNames.contains(toyNameB), "Owner A's backup must not contain user B's toy.");
        assertFalse(systemNames.contains(showcaseName), "Owner A's backup must not contain the showcase owner's system.");
    }

    // ------------------------------- Private helpers -------------------------------

    /**
     * Insert a system owned by the seeded public showcase owner directly via JDBC (the secured API can't create
     * showcase-owned rows). The explicit owner_id and the superuser test connection sidestep RLS/the tenant filter,
     * so this seeds a row that an authenticated user should never be able to reach.
     */
    private int seedSystemOwnedByShowcase(String name) {
        final int showcaseOwnerId = jdbcTemplate.queryForObject(
                "SELECT id FROM users WHERE is_public_showcase = true", Integer.class);
        return jdbcTemplate.queryForObject(
                "INSERT INTO systems(name, generation, handheld, owner_id, created_at, updated_at) "
                        + "VALUES (?, 1, false, ?, now(), now()) RETURNING id",
                Integer.class, name, showcaseOwnerId);
    }

    private String uniqueSuffix() {
        // Controller (@SpringBootTest) tests commit to a shared Testcontainers DB, so names must be unique.
        return java.util.UUID.randomUUID().toString().substring(0, 8);
    }

    private String registerAndLogin() throws Exception {
        final String email = factory.randomEmail();
        factory.registerReturnResult(email, PASSWORD).andExpect(status().isCreated());
        return factory.extractToken(factory.loginReturnResult(email, PASSWORD), "accessToken");
    }

    private SystemResponseDto createSystemAs(String token, String name) throws Exception {
        final ResultActions result = mockMvc.perform(post(SYSTEMS_URL)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(factory.formatSystemPayload(name, 1, false, null)))
                .andExpect(status().isCreated());
        return factory.resultToDto(result, SystemResponseDto.class);
    }

    private ToyResponseDto createToyAs(String token, String name, String set) throws Exception {
        final ResultActions result = mockMvc.perform(post(TOYS_URL)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(factory.formatToyPayload(name, set, null)))
                .andExpect(status().isCreated());
        return factory.resultToDto(result, ToyResponseDto.class);
    }

    private BackupDataDto backupAs(String token) throws Exception {
        final ResultActions result = mockMvc.perform(post(BACKUP_URL).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        return factory.resultToDto(result, BackupDataDto.class);
    }

    private List<SystemResponseDto> searchSystemsByNameAs(String token, String name) throws Exception {
        final Filter filter = new Filter(Keychain.SYSTEM_KEY, Filter.FIELD_TYPE_TEXT, "name", Filter.OPERATOR_EQUALS, name, false);
        final ResultActions result = mockMvc.perform(post(SYSTEMS_URL + "/function/search")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(factory.formatFiltersPayload(filter)))
                .andExpect(status().isOk());
        return factory.extractDataList(result, new TypeReference<List<SystemResponseDto>>() { });
    }

    private List<ToyResponseDto> searchToysByNameAs(String token, String name) throws Exception {
        final Filter filter = new Filter(Keychain.TOY_KEY, Filter.FIELD_TYPE_TEXT, "name", Filter.OPERATOR_EQUALS, name, false);
        final ResultActions result = mockMvc.perform(post(TOYS_URL + "/function/search")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(factory.formatFiltersPayload(filter)))
                .andExpect(status().isOk());
        return factory.extractDataList(result, new TypeReference<List<ToyResponseDto>>() { });
    }
}

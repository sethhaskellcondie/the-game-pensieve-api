package com.sethhaskellcondie.thegamepensieveapi.controllers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sethhaskellcondie.thegamepensieveapi.TestFactory;
import com.sethhaskellcondie.thegamepensieveapi.domain.Keychain;
import com.sethhaskellcondie.thegamepensieveapi.domain.entity.system.SystemResponseDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@code GET /v1/function/counts} returns the per-entity item counts (every Keychain key present) plus their
 * sum, scoped like the search endpoints (RLS; soft-deleted rows excluded). The MCP sidecar's
 * {@code get_collection_summary} tool consumes this shape verbatim.
 */
@SpringBootTest
@ActiveProfiles("test-container")
@AutoConfigureMockMvc
public class CountsTests {

    @Autowired
    private MockMvc mockMvc;
    private TestFactory factory;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        factory = new TestFactory(mockMvc);
    }

    @Test
    void getCounts_ReturnsEveryEntityKeyAndConsistentTotal() throws Exception {
        // Guarantee at least one countable row in two different entity types.
        factory.postSystem();
        factory.postToyReturnResult().andExpect(status().isCreated());

        final MvcResult result = mockMvc.perform(get("/v1/function/counts"))
                .andExpect(status().isOk())
                .andReturn();

        final JsonNode data = objectMapper.readTree(result.getResponse().getContentAsString()).get("data");
        final JsonNode counts = data.get("counts");
        int sum = 0;
        for (String key : Keychain.getAllKeys()) {
            assertTrue(counts.has(key), "counts is missing entity key: " + key);
            assertTrue(counts.get(key).asInt() >= 0);
            sum += counts.get(key).asInt();
        }
        assertEquals(Keychain.getAllKeys().size(), counts.size(), "counts holds exactly the Keychain keys");
        assertEquals(sum, data.get("total").asInt(), "total equals the sum of the per-entity counts");
        assertTrue(counts.get(Keychain.SYSTEM_KEY).asInt() >= 1);
        assertTrue(counts.get(Keychain.TOY_KEY).asInt() >= 1);
    }

    /**
     * One new row in every entity table moves that entity's count by exactly one, and the total by exactly six.
     * Deltas (not absolutes) because @SpringBootTest commits to a shared Testcontainers database.
     */
    @Test
    void getCounts_OneRowAddedToEveryTable_EachKeyGoesUpByExactlyOne() throws Exception {
        final Map<String, Integer> before = fetchCounts();
        final int totalBefore = fetchTotal();

        // A video game box posts its own system and its own (new) video game; a board game box posts its own board game.
        factory.postVideoGameBox();
        factory.postToyReturnResult().andExpect(status().isCreated());
        factory.postBoardGameBox();

        final Map<String, Integer> after = fetchCounts();
        for (String key : Keychain.getAllKeys()) {
            assertEquals(before.get(key) + 1, after.get(key), "the count for " + key + " should have gone up by exactly one");
        }
        assertEquals(totalBefore + Keychain.getAllKeys().size(), fetchTotal(), "the total should have gone up by one per entity type");
    }

    /**
     * Counts exclude soft-deleted rows — the same rule the search endpoints apply. This is the behavior that a
     * count issued through the entity repositories has to keep.
     */
    @Test
    void getCounts_SoftDeletedSystem_NoLongerCounted() throws Exception {
        final SystemResponseDto system = factory.postSystem();
        final int countWithSystem = fetchCounts().get(Keychain.SYSTEM_KEY);
        final int totalWithSystem = fetchTotal();

        mockMvc.perform(delete("/v1/systems/" + system.id())).andExpect(status().isNoContent());

        assertEquals(countWithSystem - 1, fetchCounts().get(Keychain.SYSTEM_KEY), "a soft-deleted system should drop out of the counts");
        assertEquals(totalWithSystem - 1, fetchTotal(), "a soft-deleted system should drop out of the total as well");
    }

    private JsonNode fetchData() throws Exception {
        final MvcResult result = mockMvc.perform(get("/v1/function/counts"))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("data");
    }

    private Map<String, Integer> fetchCounts() throws Exception {
        final JsonNode counts = fetchData().get("counts");
        final Map<String, Integer> results = new LinkedHashMap<>();
        for (String key : Keychain.getAllKeys()) {
            results.put(key, counts.get(key).asInt());
        }
        return results;
    }

    private int fetchTotal() throws Exception {
        return fetchData().get("total").asInt();
    }
}

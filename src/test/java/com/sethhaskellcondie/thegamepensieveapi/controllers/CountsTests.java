package com.sethhaskellcondie.thegamepensieveapi.controllers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sethhaskellcondie.thegamepensieveapi.TestFactory;
import com.sethhaskellcondie.thegamepensieveapi.domain.Keychain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
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
}

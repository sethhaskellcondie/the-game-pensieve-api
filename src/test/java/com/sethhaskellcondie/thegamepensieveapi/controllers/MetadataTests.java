package com.sethhaskellcondie.thegamepensieveapi.controllers;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sethhaskellcondie.thegamepensieveapi.TestFactory;
import com.sethhaskellcondie.thegamepensieveapi.domain.metadata.Metadata;
import com.sethhaskellcondie.thegamepensieveapi.domain.metadata.MetadataGateway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import java.io.UnsupportedEncodingException;
import java.util.Map;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test-container")
@AutoConfigureMockMvc
public class MetadataTests {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private MetadataGateway metadataGateway;
    private TestFactory factory;
    private final String baseUrl = "/v1/metadata";
    private final String baseUrlSlash = "/v1/metadata/";

    @BeforeEach
    void setUp() {
        factory = new TestFactory(mockMvc);
    }

    @Test
    void testMetadataEndpoints() throws Exception {
        //Test 1: Successful Post, Return 201
        final String testKey = "testKey";
        final String testValue = "{\"property1\":\"value1\",\"property2\":\"value2\"}";

        final ResultActions postResult = factory.postMetadataReturnResult(testKey, testValue);
        
        // PostgreSQL formats JSON with spaces
        String expectedValue = testValue.replace("\":", "\": ").replace(",\"", ", \"");
        validateMetadataResponseBody(postResult, testKey, expectedValue);

    }

    @Test
    void postMetadata_valueInvalidJson_ReturnBadRequest() throws Exception {
        final String testKey = "invalidJsonKey";
        final String invalidJson = "this is not valid json";

        final ResultActions invalidPostResult = factory.postMetadataReturnResult(testKey, invalidJson);

        invalidPostResult
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors").isArray())
                .andExpect(jsonPath("$.errors[0]").value(containsString("Value must be valid JSON")));
    }

    @Test
    void patchMetadata_valueInvalidJson_ReturnBadRequest() throws Exception {
        final String patchKey = "patchValidationKey";
        
        final String invalidPatchJson = "invalid json here";
        final ResultActions invalidPatchResult = factory.patchMetadataValueReturnResult(patchKey, invalidPatchJson);
        
        invalidPatchResult
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors").isArray())
                .andExpect(jsonPath("$.errors[0]").value(containsString("Value must be valid JSON")));
    }

    private Metadata resultToMetadata(ResultActions result) throws UnsupportedEncodingException, JsonProcessingException {
        final MvcResult mvcResult = result.andReturn();
        final String responseString = mvcResult.getResponse().getContentAsString();
        final Map<String, Metadata> body = new ObjectMapper().readValue(responseString, new TypeReference<>() { });
        return body.get("data");
    }

    private void validateMetadataResponseBody(ResultActions result, String expectedKey, String expectedValue) throws Exception {
        result.andExpectAll(
                jsonPath("$.data.id").isNotEmpty(),
                jsonPath("$.data.key").value(expectedKey),
                jsonPath("$.data.value").value(expectedValue),
                jsonPath("$.data.createdAt").isNotEmpty(),
                jsonPath("$.data.updatedAt").isNotEmpty(),
                jsonPath("$.errors").isEmpty()
        );
        MvcResult mvcResult = result.andReturn();
        String responseString = mvcResult.getResponse().getContentAsString();
        Map<String, Object> responseMap = new ObjectMapper().readValue(responseString, Map.class);
        Map<String, Object> data = (Map<String, Object>) responseMap.get("data");
        String returnedValue = (String) data.get("value");
        try {
            new ObjectMapper().readTree(returnedValue);
        } catch (Exception e) {
            throw new AssertionError("Returned value is not valid JSON: " + returnedValue, e);
        }
    }
}
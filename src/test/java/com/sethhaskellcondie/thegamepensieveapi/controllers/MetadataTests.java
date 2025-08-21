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
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import java.io.UnsupportedEncodingException;
import java.util.Map;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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

    //Test 1.5: Get by ID?

    //Test 2: Delete Metadata key1 return no content

    //Test 3: Get Metadata with key1 return not found

    //Test 4: POST new Metadata with the same key return created (revived old metadata)

    //Test 5: POST new Metadata with the same key return bad request

    //Test 6: GET ALL Metadata to return ok

    //Test 7: PATCH Metadata with key1 return ok

    @Test
    void postAndPatchMetadata_ValidInput_ReturnSuccess() throws Exception {
        //Test 1: Successful Post, Return 201
        final String testKey = "key1";
        final String testValue = "{\"property1\":\"value1\",\"property2\":\"value2\"}";

        final String postPayload = formatMetadataPayload(testKey, testValue);
        final ResultActions postResult = mockMvc.perform(
                post("/v1/metadata")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(postPayload)
        );
        postResult.andExpect(status().isCreated());
        
        String expectedValue = testValue.replace("\":", "\": ").replace(",\"", ", \"");
        validateMetadataResponseBody(postResult, testKey, expectedValue);

        //Test 2: Successful PATCH, Return 200
        final String updatedValue = "{\"property1\":\"updatedValue1\",\"property3\":\"newValue3\"}";

        final String patchPayload = formatMetadataPatchPayload(updatedValue);
        final ResultActions patchResult = mockMvc.perform(
                patch("/v1/metadata/" + testKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(patchPayload)
        );

        patchResult.andExpect(status().isOk());
        
        // PostgreSQL formats JSON with spaces
        String expectedUpdatedValue = updatedValue.replace("\":", "\": ").replace(",\"", ", \"");
        validateMetadataResponseBody(patchResult, testKey, expectedUpdatedValue);
    }

    @Test
    void postMetadata_valueInvalidJson_ReturnBadRequest() throws Exception {
        final String testKey = "invalidPostJsonKey";
        final String invalidJson = "this is not valid json";

        final String formattedJson = formatMetadataPayload(testKey, invalidJson);
        final ResultActions invalidPostResult = mockMvc.perform(
                post("/v1/metadata")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(formattedJson)
        );

        invalidPostResult
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors").isArray())
                .andExpect(jsonPath("$.errors[0]").value(containsString("Value must be valid JSON")));
    }

    @Test
    void patchMetadata_valueInvalidJson_ReturnBadRequest() throws Exception {
        final String patchKey = "invalidPatchJsonKey";
        final String invalidPatchJson = "invalid json here";
        
        final String patchPayload = formatMetadataPatchPayload(invalidPatchJson);
        
        final ResultActions invalidPatchResult = mockMvc.perform(
                patch("/v1/metadata/" + patchKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(patchPayload)
        );
        
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

    private String formatMetadataPatchPayload(String value) {
        final String json = """
                {
                    "value": "%s"
                }
                """;
        return String.format(json, value.replace("\"", "\\\""));
    }

    private String formatMetadataPayload(String key, String value) {
        final String json = """
                {
                    "metadata": {
                        "id": null,
                        "key": "%s",
                        "value": "%s",
                        "createdAt": null,
                        "updatedAt": null,
                        "deletedAt": null
                    }
                }
                """;
        return String.format(json, key, value.replace("\"", "\\\""));
    }
}
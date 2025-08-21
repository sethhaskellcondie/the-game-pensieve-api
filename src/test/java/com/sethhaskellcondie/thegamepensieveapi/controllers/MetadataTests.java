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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
    private final String baseUrl = "/v1/metadata";
    private final String baseUrlSlash = "/v1/metadata/";

    @Test
    void postAndPatchMetadata_ValidInput_ReturnSuccess() throws Exception {
        //Test 1: Successful Post, Return 201
        final String testKey = "keyBrothers1";
        final String testValue = "{\"propertyBrothers1\":\"valueBrothers1\",\"propertyBrothers2\":\"valueBrothers2\"}";

        final String postPayload = formatMetadataPayload(testKey, testValue);
        final ResultActions postResult = mockMvc.perform(
                post(baseUrl)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(postPayload)
        );
        postResult.andExpect(status().isCreated());
        
        String expectedValue = testValue.replace("\":", "\": ").replace(",\"", ", \"");
        validateMetadataResponseBody(postResult, testKey, expectedValue);

        //Test 2: Successful PATCH, Return 200
        final String updatedValue = "{\"propertyBrothers1\":\"updatedBrothers1\",\"propertyBrothers3\":\"newBrothers3\"}";

        final String patchPayload = formatMetadataPatchPayload(updatedValue);
        final ResultActions patchResult = mockMvc.perform(
                patch(baseUrlSlash + testKey)
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
                post(baseUrl)
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
                patch(baseUrlSlash + patchKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(patchPayload)
        );
        
        invalidPatchResult
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors").isArray())
                .andExpect(jsonPath("$.errors[0]").value(containsString("Value must be valid JSON")));
    }

    @Test
    void deleteMetadata_ReuseDeletedKey_OverwriteDeletedKey() throws Exception {
        //Test 1: Post Metadata return 201
        final String testKey = "testKey99";
        final String testValue = "{\"property1\":\"value1\",\"property2\":\"value2\"}";

        final String postPayload = formatMetadataPayload(testKey, testValue);
        final ResultActions postResult = mockMvc.perform(
                post(baseUrl)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(postPayload)
        );
        postResult.andExpect(status().isCreated());
        
        String expectedValue = testValue.replace("\":", "\": ").replace(",\"", ", \"");
        validateMetadataResponseBody(postResult, testKey, expectedValue);

        //Test 2: Delete Metadata testKey99 return no content
        final ResultActions deleteResult = mockMvc.perform(
                delete(baseUrlSlash + testKey)
        );
        deleteResult.andExpect(status().isNoContent());

        //Test 3: Get Metadata with testKey99 return not found
        final ResultActions getResult = mockMvc.perform(
                get(baseUrlSlash + testKey)
        );
        getResult.andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errors").isArray())
                .andExpect(jsonPath("$.errors.length()").value(1));

        //Test 4: POST new Metadata with the same key return created (revived old metadata)
        final String newTestValue = "{\"differentProperty1\":\"differentValue1\",\"differentProperty2\":\"differentValue2\"}";
        final String newPostPayload = formatMetadataPayload(testKey, newTestValue);
        final ResultActions newPostResult = mockMvc.perform(
                post(baseUrl)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(newPostPayload)
        );
        newPostResult.andExpect(status().isCreated());
        
        String expectedNewValue = newTestValue.replace("\":", "\": ").replace(",\"", ", \"");
        validateMetadataResponseBody(newPostResult, testKey, expectedNewValue);
    }


    // -------------- private helper methods --------------


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
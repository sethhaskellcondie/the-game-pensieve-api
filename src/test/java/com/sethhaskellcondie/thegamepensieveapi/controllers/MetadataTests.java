package com.sethhaskellcondie.thegamepensieveapi.controllers;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sethhaskellcondie.thegamepensieveapi.TestFactory;
import com.sethhaskellcondie.thegamepensieveapi.domain.metadata.Metadata;
import com.sethhaskellcondie.thegamepensieveapi.domain.metadata.MetadataGateway;
import com.sethhaskellcondie.thegamepensieveapi.domain.exceptions.ExceptionResourceNotFound;
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
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
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
        final String testValue = "{\"property1\":\"value1\",\"property2\":\"value2\",\"property3\":\"value3\"}";

        final ResultActions postResult = factory.postMetadataReturnResult(testKey, testValue);
        validateMetadataResponseBody(postResult, testKey, testValue);
        
        final Metadata createdMetadata = resultToMetadata(postResult);

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
    }
}
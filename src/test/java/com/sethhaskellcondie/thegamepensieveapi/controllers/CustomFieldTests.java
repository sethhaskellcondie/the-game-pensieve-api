package com.sethhaskellcondie.thegamepensieveapi.controllers;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sethhaskellcondie.thegamepensieveapi.TestFactory;
import com.sethhaskellcondie.thegamepensieveapi.domain.customfield.CustomField;
import com.sethhaskellcondie.thegamepensieveapi.domain.customfield.CustomFieldGateway;
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

/**
 * Custom fields are not entities they belong to entities.
 * The custom field endpoints are mostly used for set up work, making sure that the custom fields are set up as intended before entering in other entity's data.
 * Custom fields must contain a valid type, and a unique name.
 * Custom fields cannot be PUT (overwritten) only the name can be PATCHed.
 */

@SpringBootTest
@ActiveProfiles("test-container")
@AutoConfigureMockMvc
public class CustomFieldTests {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private CustomFieldGateway customFieldGateway;
    private TestFactory factory;
    private final String baseUrl = "/v1/custom_fields";
    private final String baseUrlSlash = "/v1/custom_fields/";

    @BeforeEach
    void setUp() {
        factory = new TestFactory(mockMvc);
    }

    @Test
    void postCustomField_HappyPath_CustomFieldReturned() throws Exception {
        final String expectedName = "ToyLine";
        final String expectedType = "text";
        final String expectedEntityKey = "toy";

        final ResultActions result = factory.postCustomFieldReturnResult(expectedName, expectedType, expectedEntityKey);

        validateCustomFieldResponseBody(result, expectedName, expectedType, expectedEntityKey);
    }

    @Test
    void postCustomField_InvalidTypeInvalidKey_ReturnErrors() throws Exception {
        final String json = factory.formatCustomFieldPayload("", "invalid", "also_invalid");

        final ResultActions result = mockMvc.perform(
                post(baseUrl)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json)
        );

        result.andExpectAll(
                status().isBadRequest(),
                jsonPath("$.data").isEmpty(),
                jsonPath("$.errors.length()").value(2)
        );
    }

    @Test
    void getCustomFields_HappyPath_ReturnAll() throws Exception {
        //at least two results
        CustomField customField1 = resultToResponseDto(factory.postCustomFieldReturnResult());
        CustomField customField2 = resultToResponseDto(factory.postCustomFieldReturnResult());

        final ResultActions result = mockMvc.perform(get(baseUrl));

        result.andExpectAll(
                status().isOk(),
                content().contentType(MediaType.APPLICATION_JSON),
                jsonPath("$.data").isArray()
        );
        final MvcResult mvcResult = result.andReturn();
        final String responseString = mvcResult.getResponse().getContentAsString();
        final Map<String, List<CustomField>> body = new ObjectMapper().readValue(responseString, new TypeReference<>() { });
        final List<CustomField> returnedCustomFields = body.get("data");
        assertAll(
                "The get all custom fields response body is formatted incorrectly",
                () -> assertTrue(returnedCustomFields.size() > 1),
                () -> assertTrue(returnedCustomFields.contains(customField1)),
                () -> assertTrue(returnedCustomFields.contains(customField2))
        );
    }

    @Test
    void patchCustomFieldName_HappyPath_CustomFieldReturned() throws Exception {
        final CustomField existingCustomField = resultToResponseDto(factory.postCustomFieldReturnResult());
        final String newName = "patched name!";
        final CustomField expectedCustomField = new CustomField(existingCustomField.id(), newName, existingCustomField.type(), existingCustomField.entityKey());

        final String json = """
                {
                    "name": "%s"
                }
                """;
        final String formattedJson = String.format(json, newName);
        final ResultActions result = mockMvc.perform(
                patch(baseUrlSlash + existingCustomField.id())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(formattedJson)
        );

        result.andExpect(status().isOk());
        validateCustomFieldResponseBody(result, expectedCustomField);
    }

    @Test
    void patchCustomFieldName_InvalidId_ReturnError() throws Exception {
        final String json = """
                {
                    "name": "validName"
                }
                """;
        final ResultActions result = mockMvc.perform(
                patch(baseUrlSlash + "-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json)
        );

        result.andExpectAll(
                status().isNotFound(),
                jsonPath("$.data").isEmpty(),
                jsonPath("$.errors.length()").value(1)
        );
    }

    @Test
    void deleteCustomField_HappyPath_CustomFieldDeleted() throws Exception {
        final CustomField existingCustomField = resultToResponseDto(factory.postCustomFieldReturnResult());

        final ResultActions result = mockMvc.perform(delete(baseUrlSlash + existingCustomField.id()));

        result.andExpectAll(
                status().isNoContent(),
                jsonPath("$.data").isEmpty(),
                jsonPath("$.errors").isEmpty()
        );

        assertThrows(ExceptionResourceNotFound.class, () -> customFieldGateway.getById(existingCustomField.id()));
    }

    @Test
    void postCustomField_DuplicateFound_ReturnErrors() throws Exception {
        final String duplicateName = "Oops Entered Twice";
        final String type = "text";
        final String entityKey = "toy";
        final CustomField existingCustomField = resultToResponseDto(factory.postCustomFieldReturnResult(duplicateName, type, entityKey));
        final String json = factory.formatCustomFieldPayload(duplicateName, type, entityKey);

        final ResultActions result = mockMvc.perform(
                post(baseUrl)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json)
        );

        result.andExpectAll(
                status().isBadRequest(),
                jsonPath("$.data").isEmpty(),
                jsonPath("$.errors.length()").value(1)
        );

        postCustomField_DeletedDuplicateFound_CustomFieldCreated(existingCustomField);
    }

    void postCustomField_DeletedDuplicateFound_CustomFieldCreated(CustomField existingCustomField) throws Exception {
        mockMvc.perform(delete(baseUrlSlash + existingCustomField.id()));

        final ResultActions result = factory.postCustomFieldReturnResult(existingCustomField.name(), existingCustomField.type(), existingCustomField.entityKey());

        validateCustomFieldResponseBody(result, existingCustomField.name(), existingCustomField.type(), existingCustomField.entityKey());
    }

    @Test
    void deleteCustomField_InvalidId_ReturnError() throws Exception {
        final ResultActions result = mockMvc.perform(delete(baseUrlSlash + "-1"));

        result.andExpectAll(
                status().isNotFound(),
                jsonPath("$.data").isEmpty(),
                jsonPath("$.errors.length()").value(1)
        );
    }

    private CustomField resultToResponseDto(ResultActions result) throws UnsupportedEncodingException, JsonProcessingException {
        final MvcResult mvcResult = result.andReturn();
        final String responseString = mvcResult.getResponse().getContentAsString();
        final Map<String, CustomField> body = new ObjectMapper().readValue(responseString, new TypeReference<>() { });
        return body.get("data");
    }

    private void validateCustomFieldResponseBody(ResultActions result, String expectedName, String expectedType, String expectedEntityKey) throws Exception {
        result.andExpectAll(
                jsonPath("$.data.id").isNotEmpty(),
                jsonPath("$.data.name").value(expectedName),
                jsonPath("$.data.type").value(expectedType),
                jsonPath("$.data.entityKey").value(expectedEntityKey),
                jsonPath("$.errors").isEmpty()
        );
    }

    private void validateCustomFieldResponseBody(ResultActions result, CustomField customField) throws Exception {
        result.andExpectAll(
                jsonPath("$.data.id").value(customField.id()),
                jsonPath("$.data.name").value(customField.name()),
                jsonPath("$.data.type").value(customField.type()),
                jsonPath("$.data.entityKey").value(customField.entityKey()),
                jsonPath("$.errors").isEmpty()
        );
    }
}

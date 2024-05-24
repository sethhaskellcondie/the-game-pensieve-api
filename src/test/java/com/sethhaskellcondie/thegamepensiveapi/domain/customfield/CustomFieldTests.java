package com.sethhaskellcondie.thegamepensiveapi.domain.customfield;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sethhaskellcondie.thegamepensiveapi.domain.TestFactory;
import com.sethhaskellcondie.thegamepensiveapi.exceptions.ExceptionResourceNotFound;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import java.io.UnsupportedEncodingException;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test-container")
@AutoConfigureMockMvc
public class CustomFieldTests {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    private TestFactory factory;
    private CustomFieldRepository repository;
    private final String baseUrl = "/v1/custom_fields";
    private final String baseUrlSlash = "/v1/custom_fields/";

    @BeforeEach
    void setUp() {
        factory = new TestFactory(mockMvc);
        repository = new CustomFieldRepository(jdbcTemplate);
    }

    @Test
    void postCustomField_HappyPath_CustomFieldReturned() throws Exception {
        final String expectedName = "ToyLine";
        final String expectedType = "text";
        final String expectedEntityKey = "toy";

        final ResultActions result = factory.postCustomCustomField(expectedName, expectedType, expectedEntityKey);

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
        CustomField customField1 = resultToResponseDto(factory.postCustomField());
        CustomField customField2 = resultToResponseDto(factory.postCustomField());

        final ResultActions result = mockMvc.perform(get(baseUrl));

        result.andDo(print());
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
    void getCustomFieldsValuesCount_ZeroResults_ReturnZero() {
        //TODO finish this after values have been implemented
    }

    @Test
    void getCustomFieldsValuesCount_TwoResults_ReturnTwo() {
        //TODO finish this after values have been implemented
    }

    @Test
    void patchCustomFieldName_HappyPath_CustomFieldReturned() throws Exception {
        final CustomField existingCustomField = resultToResponseDto(factory.postCustomField());
        final String newName = "patched name!";

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
        validateCustomFieldResponseBody(result, resultToResponseDto(result));
    }

    @Test
    void patchCustomFieldName_InvalidId_ReturnError() throws Exception {
        final String json = """
                {
                    "name": "validButMissingName"
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
        final CustomField existingCustomField = resultToResponseDto(factory.postCustomField());

        final ResultActions result = mockMvc.perform(delete(baseUrlSlash + existingCustomField.id()));

        result.andExpectAll(
                status().isNoContent(),
                jsonPath("$.data").isEmpty(),
                jsonPath("$.errors").isEmpty()
        );

        assertThrows(ExceptionResourceNotFound.class, () -> repository.getById(existingCustomField.id()));
        CustomField deletedCustomField = repository.getByIdIncludeDeleted(existingCustomField.id());
        assertEquals(existingCustomField, deletedCustomField);
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

package com.sethhaskellcondie.thegamepensiveapi.domain.customfield;

import com.sethhaskellcondie.thegamepensiveapi.domain.TestFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test-container")
@AutoConfigureMockMvc
public class CustomFieldTests {

    @Autowired
    private MockMvc mockMvc;
    private TestFactory factory;
    //since there is not a getById endpoint we will call getById through the repository
    private CustomFieldRepository repository;
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
    void getCustomFields_HappyPath_ReturnAll() {
        //Seed two then return at least two
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
    void patchCustomFieldName_HappyPath_CustomFieldReturned() {

    }

    @Test
    void patchCustomFieldName_InvalidId_ReturnError() {

    }

    @Test
    void deleteCustomField_HappyPath_CustomFieldDeleted() {

    }

    @Test
    void deleteCustomField_InvalidId_ReturnError() {

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

    private void validationCustomFieldResponseBody(ResultActions result, CustomField customField) throws Exception {
        result.andExpectAll(
                jsonPath("$.data.id").value(customField.id()),
                jsonPath("$.data.name").value(customField.name()),
                jsonPath("$.data.type").value(customField.type()),
                jsonPath("$.data.entityKey").value(customField.entityKey()),
                jsonPath("$.errors").isEmpty()
        );
    }
}

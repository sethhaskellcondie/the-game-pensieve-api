package com.sethhaskellcondie.thegamepensieveapi.controllers;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sethhaskellcondie.thegamepensieveapi.TestFactory;
import com.sethhaskellcondie.thegamepensieveapi.domain.customfield.CustomField;
import com.sethhaskellcondie.thegamepensieveapi.domain.customfield.CustomFieldGateway;
import com.sethhaskellcondie.thegamepensieveapi.domain.customfield.CustomFieldOption;
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

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
    void postNonEnumCustomField_HappyPath_CustomFieldReturned() throws Exception {
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
        result.andExpect(jsonPath("$.roundTripMs").isNotEmpty());
        final List<CustomField> returnedCustomFields = factory.extractDataList(result, new TypeReference<List<CustomField>>() { });
        assertAll(
                "The get all custom fields response body is formatted incorrectly",
                () -> assertTrue(returnedCustomFields.size() > 1),
                () -> assertTrue(returnedCustomFields.contains(customField1)),
                () -> assertTrue(returnedCustomFields.contains(customField2))
        );
    }

    @Test
    void getCustomFieldsByEntityKey_HappyPath_ReturnOnlyMatchingEntityKey() throws Exception {
        final String toyEntityKey = "toy";
        final String boardGameEntityKey = "boardGame";
        
        final CustomField toyCustomField = resultToResponseDto(factory.postCustomFieldReturnResult("ToyField", "text", toyEntityKey));
        final CustomField boardGameCustomField = resultToResponseDto(factory.postCustomFieldReturnResult("BoardGameField", "text", boardGameEntityKey));

        final ResultActions result = mockMvc.perform(get(baseUrl + "/entity/" + toyEntityKey));

        result.andExpectAll(
                status().isOk(),
                content().contentType(MediaType.APPLICATION_JSON),
                jsonPath("$.data").isArray()
        );
        
        result.andExpect(jsonPath("$.roundTripMs").isNotEmpty());
        final List<CustomField> returnedCustomFields = factory.extractDataList(result, new TypeReference<List<CustomField>>() { });

        assertAll(
                "The get custom fields by entity key response should only return matching entity key",
                () -> assertTrue(returnedCustomFields.contains(toyCustomField)),
                () -> assertFalse(returnedCustomFields.contains(boardGameCustomField))
        );
    }

    @Test
    void getCustomFieldsByEntityKey_InvalidEntityKey_ReturnError() throws Exception {
        final ResultActions result = mockMvc.perform(get(baseUrl + "/entity/-1"));

        result.andExpectAll(
                status().isNotFound(),
                jsonPath("$.data").isEmpty(),
                jsonPath("$.errors.length()").value(1)
        );
    }

    @Test
    void patchCustomFieldName_HappyPath_CustomFieldReturned() throws Exception {
        final CustomField existingCustomField = resultToResponseDto(factory.postCustomFieldReturnResult());
        final String newName = "patched name!";
        final CustomField expectedCustomField = CustomField.withoutOptions(existingCustomField.id(), newName, existingCustomField.type(), existingCustomField.entityKey());

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
                jsonPath("$.errors").isEmpty(),
                jsonPath("$.roundTripMs").isNotEmpty()
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

    @Test
    void postEnumCustomField_WithOptions_FirstOptionIsDefault() throws Exception {
        for (String type : CustomField.getEnumCustomFieldTypes()) {
            final String name = "EnumField-" + type;
            final List<String> options = List.of("Option A", "Option B");
            final ResultActions result = factory.postCustomFieldReturnResult(name, type, "toy", options);
            result.andExpectAll(
                    status().isCreated(),
                    jsonPath("$.data.type").value(type),
                    jsonPath("$.data.options.length()").value(2),
                    jsonPath("$.data.options[0].name").value("Option A"),
                    jsonPath("$.data.options[0].isDefault").value(true),
                    jsonPath("$.data.options[1].name").value("Option B"),
                    jsonPath("$.data.options[1].isDefault").value(false)
            );
        }
    }

    @Test
    void postEnumCustomField_NoOptions_ReturnError() throws Exception {
        for (String type : CustomField.getEnumCustomFieldTypes()) {
            final String json = factory.formatCustomFieldPayload("EnumNoOptions-" + type, type, "toy");
            mockMvc.perform(
                    post(baseUrl)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json)
            ).andExpectAll(
                    status().isBadRequest(),
                    jsonPath("$.errors.length()").value(1)
            );
        }
    }

    @Test
    void postOption_AddedAfterCreation_AppearsInListNotDefault() throws Exception {
        final int fieldId = factory.postCustomFieldReturnId("DropdownField", CustomField.TYPE_DROPDOWN, "toy", List.of("Initial Option"));
        final String optionName = "New Option";

        final ResultActions result = postOption(fieldId, optionName);

        result.andExpectAll(
                status().isCreated(),
                jsonPath("$.data.id").value(fieldId),
                jsonPath("$.data.options.length()").value(2),
                jsonPath("$.data.options[1].name").value(optionName),
                jsonPath("$.data.options[1].isDefault").value(false)
        );
    }

    @Test
    void postOption_NonEnumType_ReturnError() throws Exception {
        final int fieldId = factory.postCustomFieldReturnId("TextField", "text", "toy");

        final ResultActions results = mockMvc.perform(
                post(baseUrlSlash + fieldId + "/options")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"custom_field_option\": {\"name\": \"BadOption\"}}")
        );

        results.andExpectAll(
                status().isBadRequest(),
                jsonPath("$.errors.length()").value(1)
        );
    }

    @Test
    void patchOptionName_HappyPath_OptionUpdated() throws Exception {
        final int fieldId = factory.postCustomFieldReturnId("ProgressField", CustomField.TYPE_PROGRESS_BAR, "toy", List.of("Initial Option"));
        final CustomFieldOption option = resultToOptionDto(postOption(fieldId, "Old Name"));
        final String newName = "New Name";

        final ResultActions result = mockMvc.perform(
                patch(baseUrlSlash + fieldId + "/options/" + option.id())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"" + newName + "\"}")
        );

        result.andExpectAll(
                status().isOk(),
                jsonPath("$.data.id").value(fieldId),
                jsonPath("$.data.options[?(@.id == " + option.id() + ")].name").value(newName)
        );
    }

    @Test
    void patchOptionDefault_HappyPath_DefaultChanged() throws Exception {
        final int fieldId = factory.postCustomFieldReturnId("DropdownDefault", CustomField.TYPE_DROPDOWN, "toy", List.of("Option A"));
        final CustomFieldOption optionA = resultToOptionDto(mockMvc.perform(get(baseUrlSlash + fieldId)));
        final CustomFieldOption optionB = resultToOptionDto(postOption(fieldId, "Option B"));

        final ResultActions result = mockMvc.perform(
                patch(baseUrlSlash + fieldId + "/options/" + optionB.id() + "/default")
        );

        result.andExpectAll(
                status().isOk(),
                jsonPath("$.data.options[?(@.id == " + optionA.id() + ")].isDefault").value(false),
                jsonPath("$.data.options[?(@.id == " + optionB.id() + ")].isDefault").value(true)
        );
    }

    @Test
    void deleteOption_NonDefaultOption_OptionDeleted() throws Exception {
        final int fieldId = factory.postCustomFieldReturnId("DeleteDropdown", CustomField.TYPE_DROPDOWN, "toy", List.of("Option A"));
        final CustomFieldOption optionA = resultToOptionDto(mockMvc.perform(get(baseUrlSlash + fieldId)));
        final CustomFieldOption optionB = resultToOptionDto(postOption(fieldId, "Option B"));

        final CustomField fieldAfterDelete = resultToResponseDto(
                mockMvc.perform(delete(baseUrlSlash + fieldId + "/options/" + optionB.id()))
                        .andExpect(status().isOk())
        );

        assertAll(
                "Option B should be gone; Option A should remain",
                () -> assertTrue(fieldAfterDelete.options().stream().anyMatch(o -> o.id() == optionA.id())),
                () -> assertFalse(fieldAfterDelete.options().stream().anyMatch(o -> o.id() == optionB.id()))
        );
    }

    @Test
    void deleteOption_DefaultOption_ReturnError() throws Exception {
        final int fieldId = factory.postCustomFieldReturnId("NoDeleteDefault", CustomField.TYPE_DROPDOWN, "toy", List.of("Only Option"));
        final CustomFieldOption defaultOption = resultToOptionDto(mockMvc.perform(get(baseUrlSlash + fieldId)));

        final ResultActions result = mockMvc.perform(delete(baseUrlSlash + fieldId + "/options/" + defaultOption.id()));

        result.andExpectAll(
                status().isBadRequest(),
                jsonPath("$.errors.length()").value(1)
        );
    }

    @Test
    void deleteOption_CascadesValuesToDefault() throws Exception {
        final int fieldId = factory.postCustomFieldReturnId("CascadeDropdown", CustomField.TYPE_DROPDOWN, "toy", List.of("Default Option"));
        final CustomFieldOption defaultOpt = resultToOptionDto(mockMvc.perform(get(baseUrlSlash + fieldId)));
        final CustomFieldOption otherOpt = resultToOptionDto(postOption(fieldId, "Other Option"));

        final CustomField fieldAfterDelete = resultToResponseDto(
                mockMvc.perform(delete(baseUrlSlash + fieldId + "/options/" + otherOpt.id()))
                        .andExpect(status().isOk())
        );

        assertAll(
                "After deleting other option, only default remains",
                () -> assertTrue(fieldAfterDelete.options().stream().anyMatch(o -> o.id() == defaultOpt.id())),
                () -> assertFalse(fieldAfterDelete.options().stream().anyMatch(o -> o.id() == otherOpt.id()))
        );
    }

    private ResultActions postOption(int customFieldId, String optionName) throws Exception {
        final String json = "{\"custom_field_option\": {\"name\": \"" + optionName + "\"}}";
        return mockMvc.perform(
                post(baseUrlSlash + customFieldId + "/options")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json)
        );
    }

    // ------------------ Private Helper Functions ----------------

    /**
     * postOption now returns the full CustomField; this helper extracts the last option in the
     * options list, which is the one most recently added.
     */
    private CustomFieldOption resultToOptionDto(ResultActions result) throws Exception {
        final CustomField field = resultToResponseDto(result);
        return field.options().getLast();
    }

    private CustomField resultToResponseDto(ResultActions result) throws Exception {
        final MvcResult mvcResult = result.andReturn();
        final String responseString = mvcResult.getResponse().getContentAsString();
        final ObjectMapper objectMapper = new ObjectMapper();
        return objectMapper.treeToValue(objectMapper.readTree(responseString).get("data"), CustomField.class);
    }

    private void validateCustomFieldResponseBody(ResultActions result, String expectedName, String expectedType, String expectedEntityKey) throws Exception {
        result.andExpectAll(
                jsonPath("$.data.id").isNotEmpty(),
                jsonPath("$.data.name").value(expectedName),
                jsonPath("$.data.type").value(expectedType),
                jsonPath("$.data.entityKey").value(expectedEntityKey),
                jsonPath("$.errors").isEmpty(),
                jsonPath("$.roundTripMs").isNotEmpty()
        );
    }

    private void validateCustomFieldResponseBody(ResultActions result, CustomField customField) throws Exception {
        result.andExpectAll(
                jsonPath("$.data.id").value(customField.id()),
                jsonPath("$.data.name").value(customField.name()),
                jsonPath("$.data.type").value(customField.type()),
                jsonPath("$.data.entityKey").value(customField.entityKey()),
                jsonPath("$.errors").isEmpty(),
                jsonPath("$.roundTripMs").isNotEmpty()
        );
    }
}

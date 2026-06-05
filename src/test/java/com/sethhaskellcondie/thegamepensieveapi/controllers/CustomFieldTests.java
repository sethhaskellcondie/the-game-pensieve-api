package com.sethhaskellcondie.thegamepensieveapi.controllers;

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

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Custom fields are not entities they belong to entities.
 * The custom field endpoints are mostly used for set up work, making sure that the custom fields are set up as intended before entering in other entity's data.
 * Custom fields must contain a valid type, and a unique name.
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

        validateCustomFieldResponseBody(result, expectedName, expectedType, expectedEntityKey, 0);
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
    void putCustomField_HappyPath_NameAndOrderUpdated() throws Exception {
        final CustomField existingCustomField = resultToResponseDto(factory.postCustomFieldReturnResult());
        final String newName = "updated name!";
        final int newOrder = 5;

        final String json = """
                {
                    "custom_field": {
                        "name": "%s",
                        "order": %d
                    }
                }
                """;
        final String formattedJson = String.format(json, newName, newOrder);
        final ResultActions result = mockMvc.perform(
                put(baseUrlSlash + existingCustomField.id())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(formattedJson)
        );

        result.andExpectAll(
                status().isOk(),
                jsonPath("$.data.id").value(existingCustomField.id()),
                jsonPath("$.data.name").value(newName),
                jsonPath("$.data.order").value(newOrder)
        );
    }

    @Test
    void putCustomField_ProgressBarWithOptions_OptionOrderUpdated() throws Exception {
        final int fieldId = factory.postCustomFieldReturnId("ProgressBar Order Test", CustomField.TYPE_PROGRESS_BAR, "toy",
                List.of("Option A", "Option B", "Option C"));
        final CustomField field = resultToResponseDto(mockMvc.perform(get(baseUrlSlash + fieldId)));
        final int optionAId = field.options().get(0).id();
        final int optionBId = field.options().get(1).id();
        final int optionCId = field.options().get(2).id();

        final String json = """
                {
                    "custom_field": {
                        "name": "%s",
                        "order": 0,
                        "options": [
                            {"id": %d, "name": "Option A", "order": 2, "isDefault": true},
                            {"id": %d, "name": "Option B", "order": 1, "isDefault": false},
                            {"id": %d, "name": "Option C", "order": 0, "isDefault": false}
                        ]
                    }
                }
                """;
        final ResultActions result = mockMvc.perform(
                put(baseUrlSlash + fieldId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format(json, field.name(), optionAId, optionBId, optionCId))
        );

        result.andExpectAll(
                status().isOk(),
                jsonPath("$.data.options[0].name").value("Option C"),
                jsonPath("$.data.options[0].order").value(0),
                jsonPath("$.data.options[1].name").value("Option B"),
                jsonPath("$.data.options[1].order").value(1),
                jsonPath("$.data.options[2].name").value("Option A"),
                jsonPath("$.data.options[2].order").value(2)
        );
    }

    @Test
    void putCustomField_InvalidId_ReturnError() throws Exception {
        final String json = """
                {
                    "custom_field": {
                        "name": "validName",
                        "order": 1
                    }
                }
                """;
        final ResultActions result = mockMvc.perform(
                put(baseUrlSlash + "-1")
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

        validateCustomFieldResponseBody(result, existingCustomField.name(), existingCustomField.type(), existingCustomField.entityKey(), 0);
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
    void postEnumCustomField_WithOptions_ExplicitDefaultAndOrderReturned() throws Exception {
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
                    jsonPath("$.data.options[0].order").value(0),
                    jsonPath("$.data.options[1].name").value("Option B"),
                    jsonPath("$.data.options[1].isDefault").value(false),
                    jsonPath("$.data.options[1].order").value(1)
            );
        }
    }

    @Test
    void postEnumCustomField_NoDefaultInOptions_ReturnError() throws Exception {
        for (String type : CustomField.getEnumCustomFieldTypes()) {
            final String json = """
                    {
                        "custom_field": {
                            "name": "NoDefault-%s",
                            "type": "%s",
                            "entityKey": "toy",
                            "options": [
                                {"id": null, "name": "Option A", "order": 0, "isDefault": false}
                            ]
                        }
                    }
                    """.formatted(type, type);
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
    void putCustomField_AddNewOptionInline_OptionAppearsInResult() throws Exception {
        final int fieldId = factory.postCustomFieldReturnId("DropdownField", CustomField.TYPE_DROPDOWN, "toy", List.of("Initial Option"));
        final CustomField field = resultToResponseDto(mockMvc.perform(get(baseUrlSlash + fieldId)));
        final int existingOptionId = field.options().get(0).id();

        final String json = """
                {
                    "custom_field": {
                        "name": "%s",
                        "order": 0,
                        "options": [
                            {"id": %d, "name": "Initial Option", "order": 0, "isDefault": true},
                            {"id": null, "name": "New Option", "order": 1, "isDefault": false}
                        ]
                    }
                }
                """;
        final ResultActions result = mockMvc.perform(
                put(baseUrlSlash + fieldId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format(json, field.name(), existingOptionId))
        );

        result.andExpectAll(
                status().isOk(),
                jsonPath("$.data.options.length()").value(2),
                jsonPath("$.data.options[0].name").value("Initial Option"),
                jsonPath("$.data.options[0].isDefault").value(true),
                jsonPath("$.data.options[0].order").value(0),
                jsonPath("$.data.options[1].name").value("New Option"),
                jsonPath("$.data.options[1].isDefault").value(false),
                jsonPath("$.data.options[1].order").value(1)
        );
    }

    @Test
    void putCustomField_AddOptionToNonEnumType_ReturnError() throws Exception {
        final int fieldId = factory.postCustomFieldReturnId("TextField", "text", "toy");
        final CustomField field = resultToResponseDto(mockMvc.perform(get(baseUrlSlash + fieldId)));

        final String json = """
                {
                    "custom_field": {
                        "name": "%s",
                        "order": 0,
                        "options": [
                            {"id": null, "name": "Bad Option", "order": 0, "isDefault": true}
                        ]
                    }
                }
                """;
        final ResultActions result = mockMvc.perform(
                put(baseUrlSlash + fieldId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format(json, field.name()))
        );

        result.andExpectAll(
                status().isBadRequest(),
                jsonPath("$.errors.length()").value(1)
        );
    }

    @Test
    void putCustomField_ChangeDefaultOption_DefaultUpdated() throws Exception {
        final int fieldId = factory.postCustomFieldReturnId("DropdownDefault", CustomField.TYPE_DROPDOWN, "toy", List.of("Option A", "Option B"));
        final CustomField field = resultToResponseDto(mockMvc.perform(get(baseUrlSlash + fieldId)));
        final int optionAId = field.options().get(0).id();
        final int optionBId = field.options().get(1).id();

        final String json = """
                {
                    "custom_field": {
                        "name": "%s",
                        "order": 0,
                        "options": [
                            {"id": %d, "name": "Option A", "order": 0, "isDefault": false},
                            {"id": %d, "name": "Option B", "order": 1, "isDefault": true}
                        ]
                    }
                }
                """;
        final ResultActions result = mockMvc.perform(
                put(baseUrlSlash + fieldId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format(json, field.name(), optionAId, optionBId))
        );

        result.andExpectAll(
                status().isOk(),
                jsonPath("$.data.options[?(@.id == " + optionAId + ")].isDefault").value(false),
                jsonPath("$.data.options[?(@.id == " + optionBId + ")].isDefault").value(true)
        );
    }

    @Test
    void putCustomField_RemoveNonDefaultOption_OptionDeleted() throws Exception {
        final int fieldId = factory.postCustomFieldReturnId("DeleteDropdown", CustomField.TYPE_DROPDOWN, "toy", List.of("Option A", "Option B"));
        final CustomField field = resultToResponseDto(mockMvc.perform(get(baseUrlSlash + fieldId)));
        final int optionAId = field.options().get(0).id();
        final int optionBId = field.options().get(1).id();

        final String json = """
                {
                    "custom_field": {
                        "name": "%s",
                        "order": 0,
                        "options": [
                            {"id": %d, "name": "Option A", "order": 0, "isDefault": true}
                        ]
                    }
                }
                """;
        final CustomField result = resultToResponseDto(mockMvc.perform(
                put(baseUrlSlash + fieldId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format(json, field.name(), optionAId))
        ).andExpect(status().isOk()));

        assertAll(
                "Option B should be gone; Option A should remain",
                () -> assertTrue(result.options().stream().anyMatch(o -> o.id() == optionAId && o.order() == 0)),
                () -> assertFalse(result.options().stream().anyMatch(o -> o.id() == optionBId))
        );
    }

    @Test
    void putCustomField_RemoveOptionCascadesValuesToDefault() throws Exception {
        final int fieldId = factory.postCustomFieldReturnId("CascadeDropdown", CustomField.TYPE_DROPDOWN, "toy", List.of("Default Option", "Other Option"));
        final CustomField field = resultToResponseDto(mockMvc.perform(get(baseUrlSlash + fieldId)));
        final int defaultOptId = field.options().get(0).id();
        final int otherOptId = field.options().get(1).id();

        final String json = """
                {
                    "custom_field": {
                        "name": "%s",
                        "order": 0,
                        "options": [
                            {"id": %d, "name": "Default Option", "order": 0, "isDefault": true}
                        ]
                    }
                }
                """;
        final CustomField result = resultToResponseDto(mockMvc.perform(
                put(baseUrlSlash + fieldId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format(json, field.name(), defaultOptId))
        ).andExpect(status().isOk()));

        assertAll(
                "After removing Other Option, only Default Option remains",
                () -> assertTrue(result.options().stream().anyMatch(o -> o.id() == defaultOptId && o.order() == 0)),
                () -> assertFalse(result.options().stream().anyMatch(o -> o.id() == otherOptId))
        );
    }

    @Test
    void putCustomField_NoDefaultInOptions_ReturnError() throws Exception {
        final int fieldId = factory.postCustomFieldReturnId("NoDefault", CustomField.TYPE_DROPDOWN, "toy", List.of("Option A"));
        final CustomField field = resultToResponseDto(mockMvc.perform(get(baseUrlSlash + fieldId)));
        final int optionAId = field.options().get(0).id();

        final String json = """
                {
                    "custom_field": {
                        "name": "%s",
                        "order": 0,
                        "options": [
                            {"id": %d, "name": "Option A", "order": 0, "isDefault": false}
                        ]
                    }
                }
                """;
        final ResultActions result = mockMvc.perform(
                put(baseUrlSlash + fieldId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format(json, field.name(), optionAId))
        );

        result.andExpectAll(
                status().isBadRequest(),
                jsonPath("$.errors.length()").value(1)
        );
    }

    // ------------------ Private Helper Functions ----------------

    private CustomField resultToResponseDto(ResultActions result) throws Exception {
        final MvcResult mvcResult = result.andReturn();
        final String responseString = mvcResult.getResponse().getContentAsString();
        final ObjectMapper objectMapper = new ObjectMapper();
        return objectMapper.treeToValue(objectMapper.readTree(responseString).get("data"), CustomField.class);
    }

    private void validateCustomFieldResponseBody(ResultActions result, String expectedName, String expectedType, String expectedEntityKey, int expectedOrder) throws Exception {
        result.andExpectAll(
                jsonPath("$.data.id").isNotEmpty(),
                jsonPath("$.data.name").value(expectedName),
                jsonPath("$.data.type").value(expectedType),
                jsonPath("$.data.entityKey").value(expectedEntityKey),
                jsonPath("$.data.order").value(expectedOrder),
                jsonPath("$.errors").isEmpty(),
                jsonPath("$.roundTripMs").isNotEmpty()
        );
    }
}

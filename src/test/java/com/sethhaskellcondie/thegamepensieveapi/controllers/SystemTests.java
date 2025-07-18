package com.sethhaskellcondie.thegamepensieveapi.controllers;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sethhaskellcondie.thegamepensieveapi.domain.Keychain;
import com.sethhaskellcondie.thegamepensieveapi.TestFactory;
import com.sethhaskellcondie.thegamepensieveapi.domain.customfield.CustomFieldValue;
import com.sethhaskellcondie.thegamepensieveapi.domain.entity.system.SystemResponseDto;
import com.sethhaskellcondie.thegamepensieveapi.domain.filter.Filter;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * I've done a bunch of different tests and for this project I feel like the best option is to
 * run with MockMvc integration tests for the rest of the tests now. It is the easiest to use
 * I was able to get everything done with MockMvc tests.
 * <p>
 * Systems are another basic entity, they implement all entity CRUD functions, custom fields, and filters.
 * Systems must have a unique name.
 * Systems have a "belongs-to" relationship with video games and video games boxes.
 * <p>
 * Systems have all the basic CRUD functionality
 * The System name is required and the name must be unique.
 */
@SpringBootTest
@ActiveProfiles("test-container")
@AutoConfigureMockMvc
public class SystemTests {

    @Autowired
    private MockMvc mockMvc;
    private TestFactory factory;
    private final String baseUrl = "/v1/systems";

    @BeforeEach
    void setUp() {
        factory = new TestFactory(mockMvc);
    }

    @Test
    void postAndPatchSystem_ValidInput_ReturnSuccess() throws Exception {
        //test 1 - when valid post sent, then 201 (created) returned
        final String expectedName = "NES 2";
        final int expectedGeneration = 3;
        final boolean expectedHandheld = false;
        final List<CustomFieldValue> expectedCustomFieldValues = List.of(
                new CustomFieldValue(0, "Owned", "boolean", "true"),
                new CustomFieldValue(0, "Release Date", "number", "1992"),
                new CustomFieldValue(0, "Publisher", "text", "Nintendo")
        );

        final ResultActions postResult = factory.postSystemReturnResult(expectedName, expectedGeneration, expectedHandheld, expectedCustomFieldValues);

        validateSystemResponseBody(postResult, expectedName, expectedGeneration, expectedHandheld, expectedCustomFieldValues);


        //test 2 - when valid patch sent, then ok (200) returned
        final SystemResponseDto responseDto = resultToResponseDto(postResult); //use the response from the previous post
        final String updatedName = "New NES 3";
        final int updatedGeneration = 6;
        final boolean updatedHandheld = true;
        final List<CustomFieldValue> existingCustomFieldValues = responseDto.customFieldValues();
        final CustomFieldValue customFieldValueToUpdate = responseDto.customFieldValues().get(0);
        existingCustomFieldValues.remove(0);
        final CustomFieldValue updatedValue = new CustomFieldValue(
                customFieldValueToUpdate.getCustomFieldId(),
                "Updated" + customFieldValueToUpdate.getCustomFieldName(),
                customFieldValueToUpdate.getCustomFieldType(),
                "false"
        );
        existingCustomFieldValues.add(updatedValue);

        final String jsonContent = factory.formatSystemPayload(updatedName, updatedGeneration, updatedHandheld, existingCustomFieldValues);
        final ResultActions patchResult = mockMvc.perform(
                put(baseUrl + "/" + responseDto.id())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonContent)
        );

        patchResult.andExpect(status().isOk());
        validateSystemResponseBody(patchResult, updatedName, updatedGeneration, updatedHandheld, existingCustomFieldValues);
    }

    @Test
    void getOneSystem_SystemExists_SystemSerializedCorrectly() throws Exception {
        final String name = "Genesis 2";
        final int generation = 4;
        final boolean handheld = true;
        final List<CustomFieldValue> customFieldValues = List.of(new CustomFieldValue(0, "name of custom field", "text", "value for custom field"));
        final ResultActions postResult = factory.postSystemReturnResult(name, generation, handheld, customFieldValues);
        final SystemResponseDto expectedDto = resultToResponseDto(postResult);

        final ResultActions result = mockMvc.perform(get(baseUrl + "/" + expectedDto.id()));

        result.andExpectAll(
                status().isOk(),
                content().contentType(MediaType.APPLICATION_JSON)
        );
        validateSystemResponseBody(result, name, generation, handheld, customFieldValues);
    }

    @Test
    void getOneSystem_SystemMissing_NotFoundReturned() throws Exception {
        final ResultActions result = mockMvc.perform(get(baseUrl + "/-1"));

        result.andExpectAll(
                status().isNotFound(),
                jsonPath("$.data").isEmpty(),
                jsonPath("$.errors").isNotEmpty()
        );
    }

    @Test
    void getAllSystems_WithFilters_SubsetOfSystemsReturned() throws Exception {
        //test 1 - when getting all systems with a filter, only a subset of the systems are returned
        final String customFieldName = "Custom";
        final String customFieldType = "number";
        final String customFieldKey = Keychain.SYSTEM_KEY;
        final int customFieldId = factory.postCustomFieldReturnId(customFieldName, customFieldType, customFieldKey);

        final String name1 = "Mega Super Nintendo";
        final int generation1 = 3;
        final boolean handheld1 = false;
        final List<CustomFieldValue> customFieldValues1 = List.of(new CustomFieldValue(customFieldId, customFieldName, customFieldType, "1"));
        final ResultActions result1 = factory.postSystemReturnResult(name1, generation1, handheld1, customFieldValues1);
        final SystemResponseDto responseDto1 = resultToResponseDto(result1);

        final String name2 = "Mega Sony Playstation";
        final int generation2 = 4;
        final boolean handheld2 = false;
        final List<CustomFieldValue> customFieldValues2 = List.of(new CustomFieldValue(customFieldId, customFieldName, customFieldType, "2"));
        final ResultActions result2 = factory.postSystemReturnResult(name2, generation2, handheld2, customFieldValues2);
        final SystemResponseDto responseDto2 = resultToResponseDto(result2);

        final String name3 = "Regular Sony Playstation";
        final int generation3 = 5;
        final boolean handheld3 = true;
        final List<CustomFieldValue> customFieldValues3 = List.of(new CustomFieldValue(customFieldId, customFieldName, customFieldType, "3"));
        final ResultActions result3 = factory.postSystemReturnResult(name3, generation3, handheld3, customFieldValues3);
        final SystemResponseDto responseDto3 = resultToResponseDto(result3);

        final Filter filter = new Filter("system", "text", "name", Filter.OPERATOR_STARTS_WITH, "Mega ", false);
        final String jsonContent = factory.formatFiltersPayload(filter);

        final ResultActions result = mockMvc.perform(post(baseUrl + "/function/search")
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonContent)
        );

        result.andExpectAll(
                status().isOk(),
                content().contentType(MediaType.APPLICATION_JSON)
        );
        validateSystemResponseBody(result, List.of(responseDto1, responseDto2));


        //test 2 - when getting all systems with a custom field filters, only a subset of the systems are returned
        final Filter customFilter = new Filter(customFieldKey, customFieldType, customFieldName, Filter.OPERATOR_GREATER_THAN, "2", true);

        final String jsonContent2 = factory.formatFiltersPayload(customFilter);

        final ResultActions resultActions = mockMvc.perform(post(baseUrl + "/function/search")
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonContent2)
        );

        result.andExpectAll(
                status().isOk(),
                content().contentType(MediaType.APPLICATION_JSON)
        );
        validateSystemResponseBody(resultActions, List.of(responseDto3));
    }

    @Test
    void getAllSystems_NoSystemsPresent_EmptyArrayReturned() throws Exception {
        final Filter filter = new Filter("system", "text", "name", Filter.OPERATOR_STARTS_WITH, "noResults", false);
        final String jsonContent = factory.formatFiltersPayload(filter);

        final ResultActions result = mockMvc.perform(post(baseUrl + "/function/search")
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonContent)
        );

        result.andExpectAll(
                status().isOk(),
                content().contentType(MediaType.APPLICATION_JSON),
                jsonPath("$.data").value(new ArrayList<>()),
                jsonPath("$.errors").isEmpty()
        );
    }

    @Test
    void updateExistingSystem_InvalidId_ReturnNotFound() throws Exception {
        final String jsonContent = factory.formatSystemPayload("ValidButMissing", 3, false, null);
        final ResultActions result = mockMvc.perform(
                put(baseUrl + "/-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonContent)
        );

        result.andExpectAll(
                status().isNotFound(),
                jsonPath("$.data").isEmpty(),
                jsonPath("$.errors").isNotEmpty()
        );
    }

    @Test
    void deleteExistingSystem_SystemExists_ReturnNoContent() throws Exception {
        final ResultActions existingResult = factory.postSystemReturnResult();
        final SystemResponseDto responseDto = resultToResponseDto(existingResult);

        final ResultActions result = mockMvc.perform(
                delete(baseUrl + "/" + responseDto.id())
        );

        result.andExpectAll(
                status().isNoContent(),
                jsonPath("$.data").isEmpty(),
                jsonPath("$.errors").isEmpty()
        );
    }

    @Test
    void deleteExistingSystem_InvalidId_ReturnNotFound() throws Exception {
        final ResultActions result = mockMvc.perform(
                delete(baseUrl + "/-1")
        );

        result.andExpectAll(
                status().isNotFound(),
                jsonPath("$.data").isEmpty(),
                jsonPath("$.errors").isNotEmpty()
        );
    }

    // ------------------------- System Unique Tests ------------------------------

    @Test
    void postSystem_FailedValidation_ReturnArrayOfErrors() throws Exception {
        final String jsonContent = factory.formatSystemPayload(
                "", //the name cannot be blank
                -1, //the generation must be positive
                null,
                null
        );

        final ResultActions result = mockMvc.perform(
                post(baseUrl)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonContent)
        );

        result.andExpectAll(
                status().isBadRequest(),
                jsonPath("$.data").isEmpty(),
                jsonPath("$.errors.length()").value(2)
        );
    }

    @Test
    void postSystem_SystemNameDuplicate_ReturnBadRequest() throws Exception {
        final String duplicateName = "Game Boy Pocket";
        final int generation = 3;
        final boolean handheld = true;
        factory.postSystemReturnResult(duplicateName, generation, handheld, new ArrayList<>());

        final String formattedJson = factory.formatSystemPayload(duplicateName, generation, handheld, new ArrayList<>());
        final ResultActions result = mockMvc.perform(
                post(baseUrl)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(formattedJson)
        );

        result.andExpectAll(
                status().isBadRequest(),
                jsonPath("$.data").isEmpty(),
                jsonPath("$.errors.length()").value(1)
        );
    }

    // ------------------------- Private Helper Methods ------------------------------

    private SystemResponseDto resultToResponseDto(ResultActions result) throws Exception {
        return factory.resultToSystemResponseDto(result);
    }

    private void validateSystemResponseBody(ResultActions result, String expectedName, int expectedGeneration, boolean expectedHandheld, List<CustomFieldValue> customFieldValues) throws Exception {
        result.andExpectAll(
                jsonPath("$.data.key").value(Keychain.SYSTEM_KEY),
                jsonPath("$.data.id").isNotEmpty(),
                jsonPath("$.data.name").value(expectedName),
                jsonPath("$.data.generation").value(expectedGeneration),
                jsonPath("$.data.handheld").value(expectedHandheld),
                jsonPath("$.errors").isEmpty()
        );
        SystemResponseDto responseDto = resultToResponseDto(result);
        factory.validateCustomFieldValues(responseDto.customFieldValues(), customFieldValues);
    }

    private void validateSystemResponseBody(ResultActions result, List<SystemResponseDto> expectedSystems) throws Exception {
        result.andExpectAll(
                jsonPath("$.data").exists(),
                jsonPath("$.errors").isEmpty()
        );

        final MvcResult mvcResult = result.andReturn();
        final String responseString = mvcResult.getResponse().getContentAsString();
        final Map<String, List<SystemResponseDto>> body = new ObjectMapper().readValue(responseString, new TypeReference<>() { });
        final List<SystemResponseDto> returnedSystems = body.get("data");
        //testing order as well as each member being deserialized correctly
        for (int i = 0; i < returnedSystems.size(); i++) {
            SystemResponseDto expectedSystem = expectedSystems.get(i);
            SystemResponseDto returnedSystem = returnedSystems.get(i);
            assertAll(
                    "The response body is not formatted correctly",
                    () -> assertEquals(Keychain.SYSTEM_KEY, returnedSystem.key()),
                    () -> assertEquals(expectedSystem.id(), returnedSystem.id()),
                    () -> assertEquals(expectedSystem.name(), returnedSystem.name()),
                    () -> assertEquals(expectedSystem.generation(), returnedSystem.generation()),
                    () -> assertEquals(expectedSystem.handheld(), returnedSystem.handheld())
            );
            factory.validateCustomFieldValues(expectedSystem.customFieldValues(), returnedSystem.customFieldValues());
        }
    }
}

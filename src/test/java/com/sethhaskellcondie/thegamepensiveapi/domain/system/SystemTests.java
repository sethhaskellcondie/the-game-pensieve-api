package com.sethhaskellcondie.thegamepensiveapi.domain.system;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sethhaskellcondie.thegamepensiveapi.domain.Keychain;
import com.sethhaskellcondie.thegamepensiveapi.domain.TestFactory;
import com.sethhaskellcondie.thegamepensiveapi.domain.customfield.CustomFieldValue;
import com.sethhaskellcondie.thegamepensiveapi.domain.filter.Filter;
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

    //TODO refactor this

    @Test
    void postSystem_ValidPayload_SystemCreatedAndReturned() throws Exception {
        final String expectedName = "NES 2";
        final int expectedGeneration = 3;
        final boolean expectedHandheld = false;
        final List<CustomFieldValue> expectedCustomFieldValues = List.of(
                new CustomFieldValue(0, "Release Date", "number", "1992"),
                new CustomFieldValue(0, "Publisher", "text", "Nintendo"),
                new CustomFieldValue(0, "Owned", "boolean", "true")
        );

        final ResultActions result = factory.postCustomSystem(expectedName, expectedGeneration, expectedHandheld, expectedCustomFieldValues);

        result.andExpect(status().isCreated());
        validateSystemResponseBody(result, expectedName, expectedGeneration, expectedHandheld, expectedCustomFieldValues);
    }

    @Test
    void postSystem_FailedValidation_ReturnArrayOfErrors() throws Exception {
        final String jsonContent = factory.formatSystemPayload("", -1, null, null);

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

        factory.postCustomSystem(duplicateName, generation, handheld, null);
        final String formattedJson = factory.formatSystemPayload(duplicateName, generation, handheld, null);
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

    @Test
    void getOneSystem_SystemExists_SystemSerializedCorrectly() throws Exception {
        final String name = "Genesis 2";
        final int generation = 4;
        final boolean handheld = true;
        final List<CustomFieldValue> customFieldValues = List.of(new CustomFieldValue(0, "name of custom field", "text", "value for custom field"));
        final ResultActions postResult = factory.postCustomSystem(name, generation, handheld, customFieldValues);
        final SystemResponseDto expectedDto = resultToResponseDto(postResult);

        final ResultActions result = mockMvc.perform(get(baseUrl + "/" + expectedDto.id()));

        result.andExpectAll(
                status().isOk(),
                content().contentType(MediaType.APPLICATION_JSON)
        );
        validateSystemResponseBody(result, expectedDto, customFieldValues);
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
    void getWithFilters_StartsWithFilter_TwoSystemsReturnedInArray() throws Exception {
        final String customFieldName = "Custom";
        final String customFieldType = "text";
        final String customFieldKey = "system";
        final int customFieldId = factory.postCustomFieldReturnId(customFieldName, customFieldType, customFieldKey);

        final String name1 = "Mega Super Nintendo";
        final int generation1 = 4;
        final boolean handheld1 = false;
        final List<CustomFieldValue> customFieldValues1 = List.of(new CustomFieldValue(customFieldId, customFieldName, customFieldType, "value1"));
        final ResultActions result1 = factory.postCustomSystem(name1, generation1, handheld1, customFieldValues1);
        final SystemResponseDto responseDto1 = resultToResponseDto(result1);

        final String name2 = "Mega Sony Playstation";
        final int generation2 = 4;
        final boolean handheld2 = false;
        final List<CustomFieldValue> customFieldValues2 = List.of(new CustomFieldValue(customFieldId, customFieldName, customFieldType, "value2"));
        final ResultActions result2 = factory.postCustomSystem(name2, generation2, handheld2, customFieldValues2);
        final SystemResponseDto responseDto2 = resultToResponseDto(result2);

        final String name3 = "Regular Sony Playstation (not returned)";
        final int generation3 = 4;
        final boolean handheld3 = false;
        final List<CustomFieldValue> customFieldValues3 = List.of(new CustomFieldValue(customFieldId, customFieldName, customFieldType, "value3"));
        factory.postCustomSystem(name3, generation3, handheld3, customFieldValues3);

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
    void updateExistingSystem_ValidUpdate_ReturnOk() throws Exception {
        //TODO update this so that the custom field value is updated with the system update
        final ResultActions existingResult = factory.postSystem();
        final SystemResponseDto responseDto = resultToResponseDto(existingResult);

        final String newName = "Playstation 2 Slim";
        final int newGeneration = 6;
        final boolean newBoolean = false;

        final String jsonContent = factory.formatSystemPayload(newName, newGeneration, newBoolean, null);
        final ResultActions result = mockMvc.perform(
                put(baseUrl + "/" + responseDto.id())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonContent)
        );

        result.andExpect(status().isOk());
        validateSystemResponseBody(result, resultToResponseDto(result), List.of());
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
        final ResultActions existingResult = factory.postSystem();
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

    void testCustomFields() {
        postSystemWithCustomFields_NewCustomFieldsAndValues_SystemCustomFieldsAndValuesCreatedAndReturned();
        putSystemWithCustomFields_UpdateCustomFieldNameExistingValue_SystemCustomFieldAndValueUpdated();
        postSystemWithCustomFields_ExistingCustomFieldsNewValues_SystemAndValuesCreatedAndReturned();
    }

    void postSystemWithCustomFields_NewCustomFieldsAndValues_SystemCustomFieldsAndValuesCreatedAndReturned() {
        //TODO finish this
        //Only test something this specific once, if all of this logic is refactored into an EntityRepository then we won't need it for Toy or other entities.
    }

    void postSystemWithCustomFields_ExistingCustomFieldsNewValues_SystemAndValuesCreatedAndReturned() {
        //TODO finish this
    }

    void putSystemWithCustomFields_UpdateCustomFieldNameExistingValue_SystemCustomFieldAndValueUpdated() {
        //TODO finish this
    }

    void testFilteringOnCustomFields() {
        //TODO finish this after filtering on custom fields has been implemented
    }

    private SystemResponseDto resultToResponseDto(ResultActions result) throws UnsupportedEncodingException, JsonProcessingException {
        final MvcResult mvcResult = result.andReturn();
        final String responseString = mvcResult.getResponse().getContentAsString();
        final Map<String, SystemResponseDto> body = new ObjectMapper().readValue(responseString, new TypeReference<>() { });
        return body.get("data");
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
        factory.validateCustomFieldValues(result, customFieldValues);
    }

    private void validateSystemResponseBody(ResultActions result, SystemResponseDto responseDto, List<CustomFieldValue> customFieldValues) throws Exception {
        result.andExpectAll(
                jsonPath("$.data.key").value("system"),
                jsonPath("$.data.id").value(responseDto.id()),
                jsonPath("$.data.name").value(responseDto.name()),
                jsonPath("$.data.generation").value(responseDto.generation()),
                jsonPath("$.data.handheld").value(responseDto.handheld()),
                jsonPath("$.errors").isEmpty()
        );
        factory.validateCustomFieldValues(result, customFieldValues);
    }

    private void validateSystemResponseBody(ResultActions result, List<SystemResponseDto> expectedSystems) throws Exception {
        //TODO update this to also test the returned custom field values
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
        }
    }
}

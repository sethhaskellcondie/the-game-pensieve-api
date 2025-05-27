package com.sethhaskellcondie.thegamepensieveapi.controllers;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sethhaskellcondie.thegamepensieveapi.TestFactory;
import com.sethhaskellcondie.thegamepensieveapi.domain.Keychain;
import com.sethhaskellcondie.thegamepensieveapi.domain.customfield.CustomFieldValue;
import com.sethhaskellcondie.thegamepensieveapi.domain.entity.toy.ToyResponseDto;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Toys are the most basic of the entities, they implement all the basic CRUD functions along with custom fields and filters.
 * Toys must include a name (not unique)
 */

@SpringBootTest
@ActiveProfiles("test-container")
@AutoConfigureMockMvc
public class ToyTests {

    @Autowired
    private MockMvc mockMvc;
    private TestFactory factory;
    private final String baseUrl = "/v1/toys";
    private final String baseUrlSlash = "/v1/toys/";

    @BeforeEach
    void setUp() {
        factory = new TestFactory(mockMvc);
    }

    @Test
    void postToyWithCustomFieldValues_ValidPayload_ToyCreatedAndReturned() throws Exception {
        final String expectedName = "Sora";
        final String expectedSet = "Disney Infinity";
        final List<CustomFieldValue> expectedCustomFieldValues = List.of(
                new CustomFieldValue(0, "Owned", "boolean", "true"),
                new CustomFieldValue(0, "Release Year", "number", "1997"),
                new CustomFieldValue(0, "ToySet", "text", "Kingdom Hearts")
        );

        final ResultActions result = factory.postToyReturnResult(expectedName, expectedSet, expectedCustomFieldValues);

        final ToyResponseDto responseDto = resultToResponseDto(result);
        validateToyResponseBody(result, expectedName, expectedSet, expectedCustomFieldValues);
        updateExistingToy_UpdateToyAndCustomFieldValue_ReturnOk(responseDto, responseDto.customFieldValues());
    }

    void updateExistingToy_UpdateToyAndCustomFieldValue_ReturnOk(ToyResponseDto existingToy, List<CustomFieldValue> existingCustomFieldValue) throws Exception {
        final String updatedName = "Donald Duck";
        final String updatedSet = "Updated Disney Infinity";
        final CustomFieldValue customFieldValueToUpdate = existingCustomFieldValue.get(0);
        existingCustomFieldValue.remove(0);
        final CustomFieldValue updatedValue = new CustomFieldValue(
            customFieldValueToUpdate.getCustomFieldId(),
            "Updated" + customFieldValueToUpdate.getCustomFieldName(),
            customFieldValueToUpdate.getCustomFieldType(),
            "false"
        );
        existingCustomFieldValue.add(updatedValue);

        final String jsonContent = factory.formatToyPayload(updatedName, updatedSet, existingCustomFieldValue);
        final ResultActions result = mockMvc.perform(
                put(baseUrlSlash + existingToy.id())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonContent)
        );

        result.andExpect(status().isOk());
        validateToyResponseBody(result, updatedName, updatedSet, existingCustomFieldValue);
    }

    @Test
    void postToy_NameBlank_ReturnBadRequest() throws Exception {
        final String jsonContent = factory.formatToyPayload("", "set", null);

        final ResultActions result = mockMvc.perform(
                post(baseUrl)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonContent)
        );

        result.andExpectAll(
                status().isBadRequest(),
                jsonPath("$.data").isEmpty(),
                jsonPath("$.errors").isNotEmpty()
        );
    }

    @Test
    void getOneToy_ToyExists_ToySerializedCorrectly() throws Exception {
        final String name = "Mario";
        final String set = "Amiibo";
        final List<CustomFieldValue> customFieldValues = List.of(new CustomFieldValue(0, "customFieldName", "text", "value"));
        ResultActions postResult = factory.postToyReturnResult(name, set, customFieldValues);
        final ToyResponseDto expectedDto = resultToResponseDto(postResult);

        final ResultActions result = mockMvc.perform(get(baseUrlSlash + expectedDto.id()));

        result.andExpectAll(
                status().isOk(),
                content().contentType(MediaType.APPLICATION_JSON)
        );
        validateToyResponseBody(result, expectedDto.name(), expectedDto.set(), customFieldValues);
    }

    @Test
    void getOneToy_ToyMissing_NotFoundReturned() throws Exception {
        final ResultActions result = mockMvc.perform(get(baseUrl + "/-1"));

        result.andExpectAll(
                status().isNotFound(),
                jsonPath("$.data").isEmpty(),
                jsonPath("$.errors").isNotEmpty()
        );
    }

    @Test
    void getAllToys_StartsWithFilter_ToyListReturned() throws Exception {
        final String customFieldName = "Custom";
        final String customFieldType = "number";
        final String customFieldKey = Keychain.TOY_KEY;
        final int customFieldId = factory.postCustomFieldReturnId(customFieldName, customFieldType, customFieldKey);

        final String name1 = "Something MegaMan";
        final String set1 = "Amiibo";
        final List<CustomFieldValue> CustomFieldValues1 = List.of(new CustomFieldValue(customFieldId, customFieldName, customFieldType, "1"));
        final ResultActions result1 = factory.postToyReturnResult(name1, set1, CustomFieldValues1);
        final ToyResponseDto toyDto1 = resultToResponseDto(result1);

        final String name2 = "Something Goofy";
        final String set2 = "Disney Infinity";
        final List<CustomFieldValue> CustomFieldValues2 = List.of(new CustomFieldValue(customFieldId, customFieldName, customFieldType, "2"));
        final ResultActions result2 = factory.postToyReturnResult(name2, set2, CustomFieldValues2);
        final ToyResponseDto toyDto2 = resultToResponseDto(result2);

        final String name3 = "Regular Goofy";
        final String set3 = "Disney Infinity";
        final List<CustomFieldValue> CustomFieldValues3 = List.of(new CustomFieldValue(customFieldId, customFieldName, customFieldType, "3"));
        final ResultActions result3 = factory.postToyReturnResult(name3, set3, CustomFieldValues3);
        final ToyResponseDto toyDto3 = resultToResponseDto(result3);

        final Filter filter = new Filter("toy", "text", "name", Filter.OPERATOR_STARTS_WITH, "Something ", false);
        final String formattedJson = factory.formatFiltersPayload(filter);

        final ResultActions result = mockMvc.perform(post(baseUrl + "/function/search")
                .contentType(MediaType.APPLICATION_JSON)
                .content(formattedJson)
        );

        result.andExpectAll(
                status().isOk(),
                content().contentType(MediaType.APPLICATION_JSON)
        );
        validateToyResponseBody(result, List.of(toyDto1, toyDto2));

        final Filter customFilter = new Filter(customFieldKey, customFieldType, customFieldName, Filter.OPERATOR_GREATER_THAN, "2", true);
        getAllToys_GreaterThanCustomFilter_ToyListReturned(customFilter, List.of(toyDto3));
    }

    void getAllToys_GreaterThanCustomFilter_ToyListReturned(Filter filter, List<ToyResponseDto> expectedToys) throws Exception {

        final String jsonContent = factory.formatFiltersPayload(filter);

        final ResultActions result = mockMvc.perform(post(baseUrl + "/function/search")
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonContent)
        );

        result.andExpectAll(
                status().isOk(),
                content().contentType(MediaType.APPLICATION_JSON)
        );
        validateToyResponseBody(result, expectedToys);
    }

    @Test
    void getAllToys_NoResultFilter_EmptyArrayReturned() throws Exception {
        final Filter filter = new Filter("toy", "text", "name", Filter.OPERATOR_STARTS_WITH, "NoResults", false);
        final String formattedJson = factory.formatFiltersPayload(filter);
        final ResultActions result = mockMvc.perform(post(baseUrl + "/function/search")
                .contentType(MediaType.APPLICATION_JSON)
                .content(formattedJson)
        );

        result.andExpectAll(
                status().isOk(),
                content().contentType(MediaType.APPLICATION_JSON),
                jsonPath("$.data").value(new ArrayList<>()),
                jsonPath("$.errors").isEmpty()
        );
    }

    @Test
    void updateExistingToy_InvalidId_ReturnNotFound() throws Exception {

        final String jsonContent = factory.formatToyPayload("invalidId", "", null);
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
    void deleteExistingToy_ToyExists_ReturnNoContent() throws Exception {
        ResultActions postResult = factory.postToyReturnResult();
        final ToyResponseDto expectedDto = resultToResponseDto(postResult);

        final ResultActions result = mockMvc.perform(
                delete(baseUrlSlash + expectedDto.id())
        );

        result.andExpectAll(
                status().isNoContent(),
                jsonPath("$.data").isEmpty(),
                jsonPath("$.errors").isEmpty()
        );
    }

    @Test
    void deleteExistingToy_InvalidId_ReturnNotFound() throws Exception {
        final ResultActions result = mockMvc.perform(
                delete(baseUrl + "/-1")
        );

        result.andExpectAll(
                status().isNotFound(),
                jsonPath("$.data").isEmpty(),
                jsonPath("$.errors").isNotEmpty()
        );
    }

    private ToyResponseDto resultToResponseDto(ResultActions result) throws Exception {
        return factory.resultToToyResponseDto(result);
    }

    private void validateToyResponseBody(ResultActions result, String expectedName, String expectedSet, List<CustomFieldValue> customFieldValues) throws Exception {
        result.andExpectAll(
                jsonPath("$.data.key").value("toy"),
                jsonPath("$.data.id").isNotEmpty(),
                jsonPath("$.data.name").value(expectedName),
                jsonPath("$.data.set").value(expectedSet),
                jsonPath("$.errors").isEmpty()
        );
        ToyResponseDto responseDto = resultToResponseDto(result);
        factory.validateCustomFieldValues(responseDto.customFieldValues(), customFieldValues);
    }

    private void validateToyResponseBody(ResultActions result, List<ToyResponseDto> expectedToys) throws Exception {
        result.andExpectAll(
                jsonPath("$.data").exists(),
                jsonPath("$.errors").isEmpty()
        );

        final MvcResult mvcResult = result.andReturn();
        final String responseString = mvcResult.getResponse().getContentAsString();
        final Map<String, List<ToyResponseDto>> body = new ObjectMapper().readValue(responseString, new TypeReference<>() { });
        final List<ToyResponseDto> returnedToys = body.get("data");
        //test the order, and the deserialization
        for (int i = 0; i < returnedToys.size(); i++) {
            ToyResponseDto expectedToy = expectedToys.get(i);
            ToyResponseDto returnedToy = returnedToys.get(i);
            assertAll(
                    "The response body for Toys is not formatted correctly",
                    () -> assertEquals("toy", returnedToy.key()),
                    () -> assertEquals(expectedToy.id(), returnedToy.id()),
                    () -> assertEquals(expectedToy.name(), returnedToy.name()),
                    () -> assertEquals(expectedToy.set(), returnedToy.set())
            );
            factory.validateCustomFieldValues(expectedToy.customFieldValues(), returnedToy.customFieldValues());
        }
    }
}

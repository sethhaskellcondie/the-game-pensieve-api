package com.sethhaskellcondie.thegamepensiveapi;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import com.sethhaskellcondie.thegamepensiveapi.domain.entity.toy.ToyResponseDto;
import com.sethhaskellcondie.thegamepensiveapi.domain.entity.videogamebox.VideoGameBoxResponseDto;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.shaded.org.apache.commons.lang3.RandomStringUtils;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sethhaskellcondie.thegamepensiveapi.domain.customfield.CustomField;
import com.sethhaskellcondie.thegamepensiveapi.domain.customfield.CustomFieldValue;
import com.sethhaskellcondie.thegamepensiveapi.domain.entity.system.SystemResponseDto;
import com.sethhaskellcondie.thegamepensiveapi.domain.entity.videogame.VideoGameResponseDto;
import com.sethhaskellcondie.thegamepensiveapi.domain.filter.Filter;

public class TestFactory {

    private final MockMvc mockMvc;

    public TestFactory(MockMvc mockMvc) {
        this.mockMvc = mockMvc;
    }

    private String randomString(int length) {
        return RandomStringUtils.random(length, true, true);
    }

    public void validateCustomFieldValues(List<CustomFieldValue> returnedValues, List<CustomFieldValue> expectedValues) {
        assertEquals(expectedValues.size(), returnedValues.size(), "The number of returned custom field values did not matched the number of expected custom field values.");
        for (int i = 0; i < returnedValues.size(); i++) {
            CustomFieldValue returnedValue = returnedValues.get(i);
            CustomFieldValue expectedValue = expectedValues.get(i);
            if (expectedValue.getCustomFieldId() == 0) {
                assertAll(
                    "The returned custom field values didn't match the expected custom field values.",
                    () -> assertEquals(expectedValue.getCustomFieldName(), returnedValue.getCustomFieldName()),
                    () -> assertEquals(expectedValue.getCustomFieldType(), returnedValue.getCustomFieldType()),
                    () -> assertEquals(expectedValue.getValue(), returnedValue.getValue())
                );
            } else {
                assertAll(
                    "The returned custom field values didn't match the expected custom field values.",
                    () -> assertEquals(expectedValue.getCustomFieldId(), returnedValue.getCustomFieldId()),
                    () -> assertEquals(expectedValue.getCustomFieldName(), returnedValue.getCustomFieldName()),
                    () -> assertEquals(expectedValue.getCustomFieldType(), returnedValue.getCustomFieldType()),
                    () -> assertEquals(expectedValue.getValue(), returnedValue.getValue())
                );
            }
        }
    }

    public String formatFiltersPayload(Filter filter) {
        final String json = """
                {
                  "filters": [
                    {
                      "key": "%s",
                      "field": "%s",
                      "operator": "%s",
                      "operand": "%s"
                    }
                  ]
                }
                """;
        return String.format(json, filter.getKey(), filter.getField(), filter.getOperator(), filter.getOperand());
    }

    public int postCustomFieldReturnId(String name, String type, String entityKey) throws Exception {
        ResultActions result = postCustomFieldReturnResult(name, type, entityKey);
        final MvcResult mvcResult = result.andReturn();
        final String responseString = mvcResult.getResponse().getContentAsString();
        final Map<String, CustomField> body = new ObjectMapper().readValue(responseString, new TypeReference<>() { });
        return body.get("data").id();
    }

    public ResultActions postCustomFieldReturnResult() throws Exception {
        final String name = "TestCustomField-" + randomString(6);
        final String type = "text";
        final String entityKey = "toy";
        return postCustomFieldReturnResult(name, type, entityKey);
    }

    public ResultActions postCustomFieldReturnResult(String name, String type, String entityKey) throws Exception {
        final String formattedJson = formatCustomFieldPayload(name, type, entityKey);

        final ResultActions result = mockMvc.perform(
                post("/v1/custom_fields")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(formattedJson)
        );

        result.andExpect(status().isCreated());
        return result;
    }

    public String formatCustomFieldPayload(String name, String type, String entityKey) {
        final String json = """
                {
                	"custom_field": {
                	    "name": "%s",
                	    "type": "%s",
                	    "entityKey": "%s"
                	    }
                }
                """;
        return String.format(json, name, type, entityKey);
    }

    public String formatCustomFieldValues(List<CustomFieldValue> customFieldValues) {
        if (null == customFieldValues || customFieldValues.isEmpty()) {
            return "[]";
        }
        String customFieldValuesArray = """
                [
                    %s
                ]
                """;
        List<String> customFieldsStrings = new ArrayList<>();
        for (int i = 0; i < customFieldValues.size(); i++) {
            customFieldsStrings.add(formatCustomField(customFieldValues.get(i), i == (customFieldValues.size() - 1)));
        }
        return String.format(customFieldValuesArray, String.join("\n", customFieldsStrings));
    }

    private String formatCustomField(CustomFieldValue value, boolean last) {
        String customFieldString;
        if (last) {
            customFieldString = """
                    {
                        "customFieldId": %d,
                        "customFieldName": "%s",
                        "customFieldType": "%s",
                        "value": "%s"
                    }
                """;
        } else {
            customFieldString = """
                    {
                        "customFieldId": %d,
                        "customFieldName": "%s",
                        "customFieldType": "%s",
                        "value": "%s"
                    },
                """;
        }
        return String.format(customFieldString, value.getCustomFieldId(), value.getCustomFieldName(), value.getCustomFieldType(), value.getValue());
    }

    public SystemResponseDto postSystem() throws Exception {
        final ResultActions result = postSystemReturnResult();
        final MvcResult mvcResult = result.andReturn();
        final String responseString = mvcResult.getResponse().getContentAsString();
        final Map<String, SystemResponseDto> body = new ObjectMapper().readValue(responseString, new TypeReference<>() { });
        return body.get("data");
    }

    public ResultActions postSystemReturnResult() throws Exception {
        final String name = "TestSystem-" + randomString(8);
        final int generation = 1;
        final boolean handheld = false;

        return postSystemReturnResult(name, generation, handheld, null);
    }

    public ResultActions postSystemReturnResult(String name, int generation, boolean handheld, List<CustomFieldValue> customFieldValues) throws Exception {
        final String customFieldValuesString = formatCustomFieldValues(customFieldValues);
        final String json = """
                {
                  "system": {
                    "name": "%s",
                    "generation": %d,
                    "handheld": %b,
                    "customFieldValues": %s
                  }
                }
                """;
        final String formattedJson = String.format(json, name, generation, handheld, customFieldValuesString);

        final ResultActions result = mockMvc.perform(
                post("/v1/systems")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(formattedJson)
        );

        result.andExpect(status().isCreated());
        return result;
    }

    public SystemResponseDto resultToSystemResponseDto(ResultActions result) throws Exception {
        final MvcResult mvcResult = result.andReturn();
        final String responseString = mvcResult.getResponse().getContentAsString();
        final Map<String, SystemResponseDto> body = new ObjectMapper().readValue(responseString, new TypeReference<>() { });
        return body.get("data");
    }

    public String formatSystemPayload(String name, Integer generation, Boolean handheld, List<CustomFieldValue> customFieldValues) {
        final String customFieldValuesString = formatCustomFieldValues(customFieldValues);
        final String json = """
                {
                    "system": {
                        "name": "%s",
                        "generation": %d,
                        "handheld": %b,
                        "customFieldValues": %s
                    }
                }
                """;
        return String.format(json, name, generation, handheld, customFieldValuesString);
    }

    public ResultActions postToyReturnResult() throws Exception {
        final String name = "TestToy-" + randomString(4);
        final String set = "TestSet-" + randomString(4);
        return postToyReturnResult(name, set, null);
    }

    public ResultActions postToyReturnResult(String name, String set, List<CustomFieldValue> customFieldValues) throws Exception {
        final String formattedJson = formatToyPayload(name, set, customFieldValues);

        final ResultActions result = mockMvc.perform(
                post("/v1/toys")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(formattedJson)
        );

        result.andExpect(status().isCreated());
        return result;
    }

    public ToyResponseDto resultToToyResponseDto(ResultActions result) throws Exception {
        final MvcResult mvcResult = result.andReturn();
        final String responseString = mvcResult.getResponse().getContentAsString();
        final Map<String, ToyResponseDto> body = new ObjectMapper().readValue(responseString, new TypeReference<>() { });
        return body.get("data");
    }

    public String formatToyPayload(String name, String set, List<CustomFieldValue> customFieldValues) {
        final String customFieldValuesString = formatCustomFieldValues(customFieldValues);
        final String json = """
                {
                	"toy": {
                	    "name": "%s",
                	    "set": "%s",
                        "customFieldValues": %s
                	    }
                }
                """;
        return String.format(json, name, set, customFieldValuesString);
    }

    public VideoGameResponseDto postVideoGame() throws Exception {
        final String title = "TestVideoGame-" + randomString(4);
        final int systemId = postSystem().id();
        final ResultActions result = postVideoGameReturnResult(title, systemId, null);
        return resultToVideoGameResponseDto(result);
    }

    public ResultActions postVideoGameReturnResult(String title, int systemId, List<CustomFieldValue> customFieldValues) throws Exception {
        final String formattedJson = formatVideoGamePayload(title, systemId, customFieldValues);

        final ResultActions result = mockMvc.perform(
                post("/v1/videoGames")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(formattedJson)
        );

        result.andExpect(status().isCreated());
        return result;
    }

    public VideoGameResponseDto resultToVideoGameResponseDto(ResultActions result) throws Exception {
        final MvcResult mvcResult = result.andReturn();
        final String responseString = mvcResult.getResponse().getContentAsString();
        final Map<String, VideoGameResponseDto> body = new ObjectMapper().readValue(responseString, new TypeReference<>() { });
        return body.get("data");
    }

    public String formatVideoGamePayload(String title, int systemId, List<CustomFieldValue> customFieldValues) {
        final String customFieldValuesString = formatCustomFieldValues(customFieldValues);
        final String json = """
                {
                	"videoGame": {
                	    "title": "%s",
                	    "systemId": "%d",
                        "customFieldValues": %s
                	    }
                }
                """;
        return String.format(json, title, systemId, customFieldValuesString);
    }

    public VideoGameBoxResponseDto postVideoGameBox() throws Exception {
        final String title = "TestVideoGameBox-" + randomString(4);
        final int systemId = postSystem().id();
        final int videoGameId = postVideoGame().id();
        final ResultActions result = postVideoGameBoxReturnResult(title, systemId, List.of(videoGameId), false, false, null);
        return resultToVideoGameBoxResponseDto(result);
    }

    public ResultActions postVideoGameBoxReturnResult(String title, int systemId, List<Integer> videoGameIds, boolean isPhysical, boolean isCollection, List<CustomFieldValue> customFieldValues)
            throws Exception {
        final String formattedJson = formatVideoGameBoxPayload(title, systemId, videoGameIds, isPhysical, isCollection, customFieldValues);

        final ResultActions result = mockMvc.perform(
                post("/v1/videoGameBoxes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(formattedJson)
        );

        result.andExpect(status().isCreated());
        return result;
    }

    public VideoGameBoxResponseDto resultToVideoGameBoxResponseDto(ResultActions result) throws Exception {
        final MvcResult mvcResult = result.andReturn();
        final String responseString = mvcResult.getResponse().getContentAsString();
        final Map<String, VideoGameBoxResponseDto> body = new ObjectMapper().readValue(responseString, new TypeReference<>() { });
        return body.get("data");
    }

    public String formatVideoGameBoxPayload(String title, int systemId, List<Integer> videoGameIds, boolean isPhysical, boolean isCollection, List<CustomFieldValue> customFieldValues) {
        final String customFieldValuesString = formatCustomFieldValues(customFieldValues);
        final String videoGameIdsString;
        if (null == videoGameIds || videoGameIds.isEmpty()) {
            videoGameIdsString = "[]";
        } else {
            videoGameIdsString = Arrays.toString(videoGameIds.toArray());
        }
        final String json = """
                {
                	"videoGame": {
                	    "title": "%s",
                	    "systemId": "%d",
                	    "videoGameIds": %s,
                        "isPhysical": %b,
                        "isCollection": %b,
                        "customFieldValues": %s
                	    }
                }
                """;
        return String.format(json, title, systemId, videoGameIdsString, isPhysical, isCollection, customFieldValuesString);
    }
}

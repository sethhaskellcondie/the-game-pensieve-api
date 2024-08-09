package com.sethhaskellcondie.thegamepensiveapi;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import com.sethhaskellcondie.thegamepensiveapi.domain.Keychain;
import com.sethhaskellcondie.thegamepensiveapi.domain.entity.boardgame.BoardGameResponseDto;
import com.sethhaskellcondie.thegamepensiveapi.domain.entity.boardgamebox.BoardGameBoxResponseDto;
import com.sethhaskellcondie.thegamepensiveapi.domain.entity.boardgamebox.SlimBoardGameBox;
import com.sethhaskellcondie.thegamepensiveapi.domain.entity.toy.ToyResponseDto;
import com.sethhaskellcondie.thegamepensiveapi.domain.entity.videogame.SlimVideoGame;
import com.sethhaskellcondie.thegamepensiveapi.domain.entity.videogame.VideoGameRequestDto;
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
            if (expectedValue.getCustomFieldId() == 0 || returnedValue.getCustomFieldId() == 0) {
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

    public void validateSystem(SystemResponseDto expectedSystem, SystemResponseDto actualSystem) {
        if (expectedSystem.id() == 0) {
            assertAll(
                    "The returned system response didn't match the expected system response.",
                    () -> assertEquals(expectedSystem.key(), actualSystem.key()),
                    () -> assertEquals(expectedSystem.name(), actualSystem.name()),
                    () -> assertEquals(expectedSystem.generation(), actualSystem.generation()),
                    () -> assertEquals(expectedSystem.handheld(), actualSystem.handheld()),
                    () -> validateCustomFieldValues(expectedSystem.customFieldValues(), actualSystem.customFieldValues())
            );
        } else {
            assertAll(
                    "The returned system response didn't match the expected system response.",
                    () -> assertEquals(expectedSystem.key(), actualSystem.key()),
                    () -> assertEquals(expectedSystem.id(), actualSystem.id()),
                    () -> assertEquals(expectedSystem.name(), actualSystem.name()),
                    () -> assertEquals(expectedSystem.generation(), actualSystem.generation()),
                    () -> assertEquals(expectedSystem.handheld(), actualSystem.handheld()),
                    () -> validateCustomFieldValues(expectedSystem.customFieldValues(), actualSystem.customFieldValues())
            );
        }
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

    public VideoGameResponseDto resultToVideoGameResponseDto(ResultActions result) throws Exception {
        final MvcResult mvcResult = result.andReturn();
        final String responseString = mvcResult.getResponse().getContentAsString();
        final Map<String, VideoGameResponseDto> body = new ObjectMapper().readValue(responseString, new TypeReference<>() { });
        return body.get("data");
    }

    public String formatVideoGamePayload(String title, int systemId, List<CustomFieldValue> customFieldValues) {
        final String json = """
                {
                	"videoGame": {
                	    "title": "%s",
                	    "systemId": %d,
                        "customFieldValues": %s
                	    }
                }
                """;
        return String.format(json, title, systemId, formatCustomFieldValues(customFieldValues));
    }

    public void validateSlimVideoGames(List<SlimVideoGame> expectedVideoGames, List<SlimVideoGame> actualVideoGames) {
        assertEquals(expectedVideoGames.size(), actualVideoGames.size(), "The number of returned slim video games did not matched the number of expected slim video games.");
        for (int i = 0; i < actualVideoGames.size(); i++) {
            SlimVideoGame returnedGame = actualVideoGames.get(i);
            SlimVideoGame expectedGame = expectedVideoGames.get(i);
            if (expectedGame.id() == 0) {
                assertAll(
                        "The returned slim video games didn't match the expected slim video games.",
                        () -> assertEquals(expectedGame.title(), returnedGame.title()),
                        () -> validateSystem(expectedGame.system(), returnedGame.system()),
                        () -> validateCustomFieldValues(expectedGame.customFieldValues(), returnedGame.customFieldValues())
                );
            } else {
                assertAll(
                        "The returned slim video games didn't match the expected slim video games.",
                        () -> assertEquals(expectedGame.id(), returnedGame.id()),
                        () -> assertEquals(expectedGame.title(), returnedGame.title()),
                        () -> validateSystem(expectedGame.system(), returnedGame.system()),
                        () -> validateCustomFieldValues(expectedGame.customFieldValues(), returnedGame.customFieldValues())
                );
            }
        }
    }

    public VideoGameBoxResponseDto postVideoGameBox() throws Exception {
        final String title = "TestVideoGameBox-" + randomString(4);
        final int systemId = postSystem().id();
        final VideoGameRequestDto newVideoGame = new VideoGameRequestDto(title, systemId, new ArrayList<>());
        final ResultActions result = postVideoGameBoxReturnResult(title, systemId, new ArrayList<Integer>(), List.of(newVideoGame), false, null);
        return resultToVideoGameBoxResponseDto(result);
    }

    public ResultActions postVideoGameBoxReturnResult(String title, int systemId, List<Integer> existingGameIds, List<VideoGameRequestDto> newVideoGames, boolean isPhysical,
                                                      List<CustomFieldValue> customFieldValues)
            throws Exception {
        final String formattedJson = formatVideoGameBoxPayload(title, systemId, existingGameIds, newVideoGames, isPhysical, customFieldValues);

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

    public String formatVideoGameBoxPayload(String title, int systemId, List<Integer> videoGameIds, List<VideoGameRequestDto> newVideoGames, boolean isPhysical,
                                            List<CustomFieldValue> customFieldValues) {

        final String videoGameIdsString;
        if (null == videoGameIds || videoGameIds.isEmpty()) {
            videoGameIdsString = "[]";
        } else {
            videoGameIdsString = Arrays.toString(videoGameIds.toArray());
        }

        final String json = """
                {
                	"videoGameBox": {
                	    "title": "%s",
                	    "systemId": %d,
                	    "existingVideoGameIds": %s,
                	    "newVideoGames": %s,
                        "isPhysical": %b,
                        "customFieldValues": %s
                	    }
                }
                """;
        return String.format(json, title, systemId, videoGameIdsString, formatVideoGameRequestDtoArray(newVideoGames), isPhysical, formatCustomFieldValues(customFieldValues));
    }

    private String formatVideoGameRequestDtoArray(List<VideoGameRequestDto> videoGames) {

        if (null == videoGames || videoGames.isEmpty()) {
            return "[]";
        }
        String videoGameRequestDtoArray = """
                [
                    %s
                ]
                """;
        List<String> customFieldsStrings = new ArrayList<>();
        for (int i = 0; i < videoGames.size(); i++) {
            customFieldsStrings.add(formatVideoGameRequestDto(videoGames.get(i), i == (videoGames.size() - 1)));
        }
        return String.format(videoGameRequestDtoArray, String.join("\n", customFieldsStrings));
    }

    private String formatVideoGameRequestDto(VideoGameRequestDto requestDto, boolean last) {
        String videoGameRequestDtoString;
        if (last) {
            videoGameRequestDtoString = """
                    {
                        "title": "%s",
                        "systemId": %d,
                        "customFieldValues": %s
                    }
                """;
        } else {
            videoGameRequestDtoString = """
                    {
                        "title": "%s",
                        "systemId": "%d",
                        "customFieldValues": %s
                    },
                """;
        }
        return String.format(videoGameRequestDtoString, requestDto.title(), requestDto.systemId(), formatCustomFieldValues(requestDto.customFieldValues()));
    }

    public void validateVideoGameBoxResponseBody(ResultActions result, String expectedTitle, SystemResponseDto expectedSystem, List<SlimVideoGame> expectedVideoGames, boolean expectedPhysical,
                                                 boolean expectedCollection, List<CustomFieldValue> customFieldValues) throws Exception {
        result.andExpectAll(
                jsonPath("$.data.key").value(Keychain.VIDEO_GAME_BOX_KEY),
                jsonPath("$.data.id").isNotEmpty(),
                jsonPath("$.data.title").value(expectedTitle),
                jsonPath("$.data.videoGames.size()").value(expectedVideoGames.size()),
                jsonPath("$.data.isPhysical").value(expectedPhysical),
                jsonPath("$.data.isCollection").value(expectedCollection),
                jsonPath("$.errors").isEmpty()
        );
        VideoGameBoxResponseDto responseDto = resultToVideoGameBoxResponseDto(result);
        validateSystem(expectedSystem, responseDto.system());
        validateSlimVideoGames(expectedVideoGames, responseDto.videoGames());
        validateCustomFieldValues(responseDto.customFieldValues(), customFieldValues);
    }

    public void validateVideoGameBoxResponseBody(ResultActions result, List<VideoGameBoxResponseDto> expectedGameBoxes) throws Exception {
        result.andExpectAll(
                jsonPath("$.data").exists(),
                jsonPath("$.errors").isEmpty()
        );

        final MvcResult mvcResult = result.andReturn();
        final String responseString = mvcResult.getResponse().getContentAsString();
        final Map<String, List<VideoGameBoxResponseDto>> body = new ObjectMapper().readValue(responseString, new TypeReference<>() { });
        final List<VideoGameBoxResponseDto> returnedGameBoxes = body.get("data");
        //test the order, and the deserialization
        for (int i = 0; i < returnedGameBoxes.size(); i++) {
            VideoGameBoxResponseDto expectedGameBox = expectedGameBoxes.get(i);
            VideoGameBoxResponseDto returnedGameBox = returnedGameBoxes.get(i);
            assertAll(
                    "The response body for videoGameBoxes is not formatted correctly",
                    () -> assertEquals(Keychain.VIDEO_GAME_BOX_KEY, returnedGameBox.key()),
                    () -> assertEquals(expectedGameBox.id(), returnedGameBox.id()),
                    () -> assertEquals(expectedGameBox.title(), returnedGameBox.title()),
                    () -> validateSlimVideoGames(expectedGameBox.videoGames(), returnedGameBox.videoGames()),
                    () -> assertEquals(expectedGameBox.isPhysical(), returnedGameBox.isPhysical()),
                    () -> assertEquals(expectedGameBox.isCollection(), returnedGameBox.isCollection())
            );
            validateCustomFieldValues(expectedGameBox.customFieldValues(), returnedGameBox.customFieldValues());
        }
    }

    public BoardGameBoxResponseDto postBoardGameBox() throws Exception {
        final String title = "TestBoardGameBox-" + randomString(4);
        final ResultActions result = postBoardGameBoxReturnResult(title, false, false, null, null, null);
        return resultToBoardGameBoxResponseDto(result);
    }

    public BoardGameBoxResponseDto postBoardGameBox(int boardGameId) throws Exception {
        final String title = "TestBoardGameBox-" + randomString(4);
        final ResultActions result = postBoardGameBoxReturnResult(title, false, false, null, boardGameId, null);
        return resultToBoardGameBoxResponseDto(result);
    }

    public SlimBoardGameBox convertBoardGameBoxResponseToSlimBoardGameBox(BoardGameBoxResponseDto responseDto) {
        return new SlimBoardGameBox(responseDto.id(), responseDto.title(), responseDto.isExpansion(), responseDto.isStandAlone(), responseDto.baseSetId(),
                responseDto.createdAt(), responseDto.updatedAt(), responseDto.deletedAt(), responseDto.customFieldValues());
    }

    public ResultActions postBoardGameBoxReturnResult(String title, boolean isExpansion, boolean isStandAlone, Integer baseSetId, Integer boardGameId, List<CustomFieldValue> customFieldValues)
            throws Exception {
        final String formattedJson = formatBoardGameBoxPayload(title, isExpansion, isStandAlone, baseSetId, boardGameId, customFieldValues);

        final ResultActions result = mockMvc.perform(
                post("/v1/boardGameBoxes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(formattedJson)
        );

        result.andExpect(status().isCreated());
        return result;
    }

    public BoardGameBoxResponseDto resultToBoardGameBoxResponseDto(ResultActions result) throws Exception {
        final MvcResult mvcResult = result.andReturn();
        final String responseString = mvcResult.getResponse().getContentAsString();
        final Map<String, BoardGameBoxResponseDto> body = new ObjectMapper().readValue(responseString, new TypeReference<>() { });
        return body.get("data");
    }

    public String formatBoardGameBoxPayload(String title, boolean isExpansion, boolean isStandAlone, Integer baseSetId, Integer boardGameId, List<CustomFieldValue> customFieldValues) {
        final String customFieldValuesString = formatCustomFieldValues(customFieldValues);
        final String json = """
                {
                	"boardGameBox": {
                	    "title": "%s",
                	    "isExpansion": "%b",
                	    "isStandAlone": %b,
                        "baseSetId": %d,
                        "boardGameId": %d,
                        "customFieldValues": %s
                	    }
                }
                """;
        return String.format(json, title, isExpansion, isStandAlone, baseSetId, boardGameId, customFieldValuesString);
    }

    public BoardGameResponseDto postBoardGame() throws Exception {
        final String title = "TestBoardGame-" + randomString(4);
        final ResultActions result = postBoardGameReturnResult(title, null);
        return resultToBoardGameResponseDto(result);
    }

    public ResultActions postBoardGameReturnResult(String title, List<CustomFieldValue> customFieldValues) throws Exception {
        final String formattedJson = formatBoardGamePayload(title, customFieldValues);

        final ResultActions result = mockMvc.perform(
                post("/v1/boardGames")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(formattedJson)
        );

        result.andExpect(status().isCreated());
        return result;
    }

    public BoardGameResponseDto resultToBoardGameResponseDto(ResultActions result) throws Exception {
        final MvcResult mvcResult = result.andReturn();
        final String responseString = mvcResult.getResponse().getContentAsString();
        final Map<String, BoardGameResponseDto> body = new ObjectMapper().readValue(responseString, new TypeReference<>() { });
        return body.get("data");
    }

    public String formatBoardGamePayload(String title, List<CustomFieldValue> customFieldValues) {
        final String customFieldValuesString = formatCustomFieldValues(customFieldValues);
        final String json = """
                {
                	"boardGame": {
                	    "title": "%s",
                        "customFieldValues": %s
                	    }
                }
                """;
        return String.format(json, title, customFieldValuesString);
    }
}

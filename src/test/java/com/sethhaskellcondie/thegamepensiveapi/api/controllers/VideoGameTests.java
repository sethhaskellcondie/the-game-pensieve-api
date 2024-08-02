package com.sethhaskellcondie.thegamepensiveapi.api.controllers;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.sethhaskellcondie.thegamepensiveapi.domain.entity.videogame.SlimVideoGame;
import com.sethhaskellcondie.thegamepensiveapi.domain.entity.videogame.VideoGameRequestDto;
import com.sethhaskellcondie.thegamepensiveapi.domain.entity.videogamebox.SlimVideoGameBox;
import com.sethhaskellcondie.thegamepensiveapi.domain.entity.videogamebox.VideoGameBoxRequestDto;
import com.sethhaskellcondie.thegamepensiveapi.domain.entity.videogamebox.VideoGameBoxResponseDto;
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

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sethhaskellcondie.thegamepensiveapi.TestFactory;
import com.sethhaskellcondie.thegamepensiveapi.domain.Keychain;
import com.sethhaskellcondie.thegamepensiveapi.domain.customfield.CustomFieldValue;
import com.sethhaskellcondie.thegamepensiveapi.domain.entity.system.SystemResponseDto;
import com.sethhaskellcondie.thegamepensiveapi.domain.entity.videogame.VideoGameResponseDto;
import com.sethhaskellcondie.thegamepensiveapi.domain.filter.Filter;

/**
 * Video games represent the games that are owned but not how they appear on the shelf in a collection.
 * If a video game appears in a collection multiple times it will only appear once in the system as a video game.
 * Because of this video games cannot be created or deleted through the videoGames endpoints instead this is done through the videoGameBox endpoints.
 * Video games must include a title, a system, and include at least one video game box.
 */

@SpringBootTest
@ActiveProfiles("test-container")
@AutoConfigureMockMvc
public class VideoGameTests {

    @Autowired
    private MockMvc mockMvc;
    private TestFactory factory;
    private final String baseUrl = "/v1/videoGames";
    private final String baseUrlSlash = "/v1/videoGames/";
    private final String videoGameBoxUrl = "/v1/videoGameBoxes";
    private final String videoGameBoxUrlSlash = "/v1/videoGameBoxes/";


    @BeforeEach
    void setUp() {
        factory = new TestFactory(mockMvc);
    }

    @Test
    void postVideoGameWithCustomFieldValuesInVideoGameBox_ValidPayload_VideoGameCreatedAndReturned() throws Exception {
        final String boxTitle = "Mega Man Legacy Collection";
        final SystemResponseDto boxSystem = factory.postSystem();
        final SystemResponseDto gameSystem = factory.postSystem();
        final boolean isPhysical = true;
        final boolean expectedCollection = true;
        final List<CustomFieldValue> expectedCustomFieldValues = List.of(
                new CustomFieldValue(0, "Owned", "boolean", "true"),
                new CustomFieldValue(0, "Players", "number", "1"),
                new CustomFieldValue(0, "Publisher", "text", "Capcom")
        );
        final VideoGameRequestDto newGame1 = new VideoGameRequestDto("Mega Man", gameSystem.id(), expectedCustomFieldValues);
        final VideoGameRequestDto newGame2 = new VideoGameRequestDto("Mega Man 2", gameSystem.id(), new ArrayList<>());
        final List<SlimVideoGame> expectedVideoGameResults = List.of(
                convertToExpectedSlimVideoGameResponse(newGame1, gameSystem),
                convertToExpectedSlimVideoGameResponse(newGame2, gameSystem)
        );

        final ResultActions result = factory.postVideoGameBoxReturnResult(boxTitle, boxSystem.id(), new ArrayList<>(), List.of(newGame1, newGame2), isPhysical, new ArrayList<>());

        factory.validateVideoGameBoxResponseBody(result, boxTitle, boxSystem, expectedVideoGameResults, isPhysical, expectedCollection, new ArrayList<>());

        final VideoGameBoxResponseDto responseDto = factory.resultToVideoGameBoxResponseDto(result);
        final SlimVideoGame existingVideoGame = responseDto.videoGames().get(0);
        updateExistingVideoGame_UpdateVideoGameAndCustomFieldValue_ReturnOk(responseDto, existingVideoGame, existingVideoGame.customFieldValues());
    }

    void updateExistingVideoGame_UpdateVideoGameAndCustomFieldValue_ReturnOk(VideoGameBoxResponseDto existingVideoGameBox, SlimVideoGame existingVideoGame, List<CustomFieldValue> existingCustomFieldValue) throws Exception {
        final String updatedTitle = "Mega Man 3";
        final SystemResponseDto newRelatedSystem = factory.postSystem();
        final CustomFieldValue customFieldValueToUpdate = existingCustomFieldValue.get(0);
        existingCustomFieldValue.remove(0);
        final CustomFieldValue updatedValue = new CustomFieldValue(
                customFieldValueToUpdate.getCustomFieldId(),
                "Updated" + customFieldValueToUpdate.getCustomFieldName(),
                customFieldValueToUpdate.getCustomFieldType(),
                "false"
        );
        existingCustomFieldValue.add(updatedValue);

        final String jsonContent = factory.formatVideoGamePayload(updatedTitle, newRelatedSystem.id(), existingCustomFieldValue);
        final ResultActions result = mockMvc.perform(
                put(baseUrlSlash + existingVideoGame.id())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonContent)
        );

        result.andExpect(status().isOk());
        validateVideoGameResponseBody(result, updatedTitle, newRelatedSystem, List.of(convertToExpectedSlimVideoGameBox(existingVideoGameBox, existingVideoGameBox.system())), existingCustomFieldValue);
    }

    @Test
    void postVideoGameInVideoGameBox_TitleBlankInvalidSystemId_ReturnBadRequest() throws Exception {
        final String boxTitle = "Valid Title";
        final SystemResponseDto boxSystem = factory.postSystem();
        //Note: This should be returned as two errors
        //Two problems returned as one error: the title cannot be blank, the systemId must be a valid int
        final VideoGameRequestDto newGame = new VideoGameRequestDto("", -1, new ArrayList<>());

        final String jsonContent = factory.formatVideoGameBoxPayload(boxTitle, boxSystem.id(), null, List.of(newGame), false, new ArrayList<>());
        final ResultActions result = mockMvc.perform(
                post(videoGameBoxUrl)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonContent)
        );

        result.andExpectAll(
                status().isBadRequest(),
                jsonPath("$.data").isEmpty(),
                jsonPath("$.errors.size()").value(1)
        );
    }

    @Test
    void postVideoGame_EndpointNotImplemented_ReturnNotFound() throws Exception {
        //Note: perhaps this should return a message that it is not implemented like the gateway does?
        final ResultActions result = mockMvc.perform(
                post(baseUrl)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}")
        );

        result.andExpectAll(
                status().isNotFound()
        );
    }

    @Test
    void postVideoGameInVideoGameBox_SystemIdInvalid_ReturnBadRequest() throws Exception {
        final String boxTitle = "Valid Title";
        final SystemResponseDto boxSystem = factory.postSystem();
        //The system id is a valid int, but there is not a system with that id in the database
        final VideoGameRequestDto newGame = new VideoGameRequestDto("Valid Title", Integer.MAX_VALUE, new ArrayList<>());

        final String jsonContent = factory.formatVideoGameBoxPayload(boxTitle, boxSystem.id(), null, List.of(newGame), false, new ArrayList<>());
        final ResultActions result = mockMvc.perform(
                post(videoGameBoxUrl)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonContent)
        );

        result.andExpectAll(
                status().isBadRequest(),
                jsonPath("$.data").isEmpty(),
                jsonPath("$.errors.size()").value(1)
        );
    }

    @Test
    void getOneVideoGame_GameExistsWithTwoBoxes_VideoGameSerializedCorrectly() throws Exception {
        final String gameTitle = "Super Mario Bros. 3";
        final SystemResponseDto relatedSystem = factory.postSystem();

        //create a box with Super Mario Bros. 3
        final String boxTitle = "Super Mario All Stars";
        final boolean isPhysical = true;
        final boolean expectedCollection = false;
        final VideoGameRequestDto newGame1 = new VideoGameRequestDto(gameTitle, relatedSystem.id(), new ArrayList<>());
        final List<SlimVideoGame> expectedVideoGameResults = List.of(convertToExpectedSlimVideoGameResponse(newGame1, relatedSystem));
        final ResultActions result = factory.postVideoGameBoxReturnResult(boxTitle, relatedSystem.id(), new ArrayList<>(), List.of(newGame1), isPhysical, new ArrayList<>());
        factory.validateVideoGameBoxResponseBody(result, boxTitle, relatedSystem, expectedVideoGameResults, isPhysical, expectedCollection, new ArrayList<>());
        final VideoGameBoxResponseDto responseDto = factory.resultToVideoGameBoxResponseDto(result);
        final SlimVideoGame slimVideoGame = responseDto.videoGames().get(0);

        //create another box with Super Mario Bros. 3
        final String boxTitle2 = "Super Mario All Stars Again";
        final ResultActions result2 = factory.postVideoGameBoxReturnResult(boxTitle2, relatedSystem.id(),
                List.of(slimVideoGame.id()), new ArrayList<>(), isPhysical, new ArrayList<>());
        factory.validateVideoGameBoxResponseBody(result2, boxTitle2, relatedSystem, expectedVideoGameResults, isPhysical, expectedCollection, new ArrayList<>());

        //get Super Mario Bros. 3 make sure it has two boxes associated with it
        final ResultActions result3 = mockMvc.perform(get(baseUrlSlash + slimVideoGame.id()));
        List<SlimVideoGameBox> expectedBoxes = List.of(
                convertToExpectedSlimVideoGameBox(responseDto, relatedSystem),
                convertToExpectedSlimVideoGameBox(factory.resultToVideoGameBoxResponseDto(result2), relatedSystem)
        );

        validateVideoGameResponseBody(result3, gameTitle, relatedSystem, expectedBoxes, new ArrayList<>());
    }

    @Test
    void getOneVideoGame_VideoGameMissing_NotFoundReturned() throws Exception {
        final ResultActions result = mockMvc.perform(get(baseUrl + "/-1"));

        result.andExpectAll(
                status().isNotFound(),
                jsonPath("$.data").isEmpty(),
                jsonPath("$.errors.size()").value(1)
        );
    }

    @Test
    void getAllVideoGames_StartsWithFilter_VideoGameListReturned() throws Exception {
        //This is used in the following test
        final String customFieldName = "Custom";
        final String customFieldType = "number";
        final String customFieldKey = Keychain.VIDEO_GAME_KEY;
        final int customFieldId = factory.postCustomFieldReturnId(customFieldName, customFieldType, customFieldKey);

        final String boxTitle1 = "Video Game Box 1";
        final String gameTitle1 = "Unique Video Game 1";
        final boolean isPhysical = true;
        final SystemResponseDto relatedSystem1 = factory.postSystem();
        final List<CustomFieldValue> customFieldValues1 = List.of(new CustomFieldValue(customFieldId, customFieldName, customFieldType, "1"));
        final VideoGameRequestDto newGame1 = new VideoGameRequestDto(gameTitle1, relatedSystem1.id(), customFieldValues1);
        final ResultActions result1 = factory.postVideoGameBoxReturnResult(boxTitle1, relatedSystem1.id(), new ArrayList<>(), List.of(newGame1), isPhysical, new ArrayList<>());
        final VideoGameBoxResponseDto responseDto1 = factory.resultToVideoGameBoxResponseDto(result1);
        final SlimVideoGame slimVideoGame1 = responseDto1.videoGames().get(0);

        final String boxTitle2 = "Video Game Box 2";
        final String gameTitle2 = "Unique Video Game 2";
        final SystemResponseDto relatedSystem2 = factory.postSystem();
        final List<CustomFieldValue> customFieldValues2 = List.of(new CustomFieldValue(customFieldId, customFieldName, customFieldType, "2"));
        final VideoGameRequestDto newGame2 = new VideoGameRequestDto(gameTitle2, relatedSystem2.id(), customFieldValues2);
        final ResultActions result2 = factory.postVideoGameBoxReturnResult(boxTitle2, relatedSystem2.id(), new ArrayList<>(), List.of(newGame2), isPhysical, new ArrayList<>());
        final VideoGameBoxResponseDto responseDto2 = factory.resultToVideoGameBoxResponseDto(result2);
        final SlimVideoGame slimVideoGame2 = responseDto2.videoGames().get(0);

        final String boxTitle3 = "Video Game Box 3";
        final String gameTitle3 = "Mega Video Game 3";
        final SystemResponseDto relatedSystem3 = factory.postSystem();
        final List<CustomFieldValue> customFieldValues3 = List.of(new CustomFieldValue(customFieldId, customFieldName, customFieldType, "3"));
        final VideoGameRequestDto newGame3 = new VideoGameRequestDto(gameTitle3, relatedSystem3.id(), customFieldValues3);
        final ResultActions result3 = factory.postVideoGameBoxReturnResult(boxTitle3, relatedSystem3.id(), new ArrayList<>(), List.of(newGame3), isPhysical, new ArrayList<>());
        final VideoGameBoxResponseDto responseDto3 = factory.resultToVideoGameBoxResponseDto(result3);
        final SlimVideoGame slimVideoGame3 = responseDto2.videoGames().get(0);

        final Filter filter = new Filter(Keychain.VIDEO_GAME_KEY, "text", "title", Filter.OPERATOR_STARTS_WITH, "Unique", false);
        final String formattedJson = factory.formatFiltersPayload(filter);

        final ResultActions result = mockMvc.perform(post(baseUrl + "/function/search")
                .contentType(MediaType.APPLICATION_JSON)
                .content(formattedJson)
        );

        result.andExpectAll(
                status().isOk(),
                content().contentType(MediaType.APPLICATION_JSON)
        );
        List<VideoGameResponseDto> expectedResponse = List.of(
                convertToVideoGameResponseDto(slimVideoGame1, List.of(responseDto1)),
                convertToVideoGameResponseDto(slimVideoGame2, List.of(responseDto2))
        );
        validateVideoGameResponseBody(result, expectedResponse);

        final Filter customFilter = new Filter(customFieldKey, customFieldType, customFieldName, Filter.OPERATOR_GREATER_THAN, "2", true);
        getWithFilters_GreaterThanCustomFilter_VideoGameListReturned(customFilter, List.of(convertToVideoGameResponseDto(slimVideoGame3, List.of(responseDto3))));
    }

    void getWithFilters_GreaterThanCustomFilter_VideoGameListReturned(Filter filter, List<VideoGameResponseDto> expectedGames) throws Exception {

        final String jsonContent = factory.formatFiltersPayload(filter);

        final ResultActions result = mockMvc.perform(post(baseUrl + "/function/search")
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonContent)
        );

        result.andExpectAll(
                status().isOk(),
                content().contentType(MediaType.APPLICATION_JSON)
        );
        validateVideoGameResponseBody(result, expectedGames);
    }

    @Test
    void deleteExistingVideoGameBox_GameBelongsToOtherBoxes_OnlyDeletedThisBox() throws Exception {
        final String gameTitle = "Super Mario Bros.";
        final SystemResponseDto relatedSystem = factory.postSystem();

        //create a box with Super Mario Bros.
        final String boxTitle = "Super Mario Collection";
        final boolean isPhysical = true;
        final VideoGameRequestDto newGame1 = new VideoGameRequestDto(gameTitle, relatedSystem.id(), new ArrayList<>());
        final ResultActions result = factory.postVideoGameBoxReturnResult(boxTitle, relatedSystem.id(), new ArrayList<>(), List.of(newGame1), isPhysical, new ArrayList<>());
        final VideoGameBoxResponseDto responseDto = factory.resultToVideoGameBoxResponseDto(result);
        final SlimVideoGame slimVideoGame = responseDto.videoGames().get(0);

        //create another box with Super Mario Bros.
        final String boxTitle2 = "Super Mario Collection Again";
        final ResultActions result2 = factory.postVideoGameBoxReturnResult(boxTitle2, relatedSystem.id(), List.of(slimVideoGame.id()), new ArrayList<>(), isPhysical, new ArrayList<>());
        final VideoGameBoxResponseDto expectedBox = factory.resultToVideoGameBoxResponseDto(result2);
        //delete Super Mario Collection
        final ResultActions deleteResult = mockMvc.perform(
                delete(videoGameBoxUrlSlash + responseDto.id())
        );
        deleteResult.andExpectAll(
                status().isNoContent(),
                jsonPath("$.data").isEmpty(),
                jsonPath("$.errors").isEmpty()
        );

        //The Super Mario Bros. Video Game should still exist because it also belongs to Super Mario Collection Again
        final ResultActions getVideoGameResult = mockMvc.perform( get(baseUrlSlash + slimVideoGame.id()));

        getVideoGameResult.andExpectAll(
                status().isOk()
        );
        validateVideoGameResponseBody(getVideoGameResult, gameTitle, relatedSystem, List.of(convertToExpectedSlimVideoGameBox(expectedBox, relatedSystem)), new ArrayList<>());
    }

    @Test
    void getAllVideoGames_NoResultFilter_EmptyArrayReturned() throws Exception {
        final Filter filter = new Filter(Keychain.VIDEO_GAME_KEY, "text", "title", Filter.OPERATOR_STARTS_WITH, "NoResults", false);
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
    void updateExistingVideoGame_InvalidId_ReturnNotFound() throws Exception {

        final String jsonContent = factory.formatVideoGamePayload("invalidId", 1, null);
        final ResultActions result = mockMvc.perform(
                put(baseUrl + "/-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonContent)
        );

        result.andExpectAll(
                status().isNotFound(),
                jsonPath("$.data").isEmpty(),
                jsonPath("$.errors.size()").value(1)
        );
    }

    @Test
    void deleteExistingVideoGameBox_GameOnlyBelongsToThisBox_DeleteBoxAndGame() throws Exception {
        final String boxTitle = "Marked For Deletion";
        final SystemResponseDto relatedSystem = factory.postSystem();
        final boolean isPhysical = true;
        final boolean expectedCollection = false;
        final VideoGameRequestDto newGame1 = new VideoGameRequestDto("To Be Deleted", relatedSystem.id(), new ArrayList<>());
        final List<SlimVideoGame> expectedVideoGameResults = List.of(convertToExpectedSlimVideoGameResponse(newGame1, relatedSystem));
        final ResultActions postBoxResult = factory.postVideoGameBoxReturnResult(boxTitle, relatedSystem.id(), new ArrayList<>(), List.of(newGame1), isPhysical, new ArrayList<>());
        factory.validateVideoGameBoxResponseBody(postBoxResult, boxTitle, relatedSystem, expectedVideoGameResults, isPhysical, expectedCollection, new ArrayList<>());
        final VideoGameBoxResponseDto existingVideoGameBox = factory.resultToVideoGameBoxResponseDto(postBoxResult);
        final SlimVideoGame slimVideoGame = existingVideoGameBox.videoGames().get(0);

        final ResultActions result = mockMvc.perform(
                delete(videoGameBoxUrlSlash + existingVideoGameBox.id())
        );

        result.andExpectAll(
                status().isNoContent(),
                jsonPath("$.data").isEmpty(),
                jsonPath("$.errors").isEmpty()
        );

        final ResultActions getVideoGameResult = mockMvc.perform(get(baseUrlSlash + slimVideoGame.id()));

        getVideoGameResult.andExpectAll(
                status().isNotFound(),
                jsonPath("$.data").isEmpty(),
                jsonPath("$.errors.size()").value(1)
        );
    }

    @Test
    void removeVideoGameFromBox_HappyPath_ReturnUpdatedGameList() throws Exception {
        //create a video game box with two video games
        final String boxTitle = "Box Collection";
        final SystemResponseDto boxSystem = factory.postSystem();
        final SystemResponseDto gameSystem = factory.postSystem();
        final boolean isPhysical = true;
        final boolean expectedCollection = true;
        final VideoGameRequestDto newGame1 = new VideoGameRequestDto("Game 1", gameSystem.id(), new ArrayList<>());
        final VideoGameRequestDto newGame2 = new VideoGameRequestDto("Game 2", gameSystem.id(), new ArrayList<>());
        final List<SlimVideoGame> expectedVideoGameResults1 = List.of(
                convertToExpectedSlimVideoGameResponse(newGame1, gameSystem),
                convertToExpectedSlimVideoGameResponse(newGame2, gameSystem)
        );

        final ResultActions result = factory.postVideoGameBoxReturnResult(boxTitle, boxSystem.id(), new ArrayList<>(), List.of(newGame1, newGame2), isPhysical, new ArrayList<>());
        factory.validateVideoGameBoxResponseBody(result, boxTitle, boxSystem, expectedVideoGameResults1, isPhysical, expectedCollection, new ArrayList<>());
        final VideoGameBoxResponseDto responseDto1 = factory.resultToVideoGameBoxResponseDto(result);
        final int game2Id = responseDto1.videoGames().get(1).id();
        //any validation here?

        //update that video game box to have different games
        final VideoGameRequestDto newGame3 = new VideoGameRequestDto("Game 3", gameSystem.id(), new ArrayList<>());
        final VideoGameRequestDto newGame4 = new VideoGameRequestDto("Game 4", gameSystem.id(), new ArrayList<>());
        final List<SlimVideoGame> expectedVideoGameResults2 = List.of(
                convertToExpectedSlimVideoGameResponse(newGame3, gameSystem),
                convertToExpectedSlimVideoGameResponse(newGame4, gameSystem)
        );

        final String jsonContent = factory.formatVideoGameBoxPayload(boxTitle, boxSystem.id(), List.of(game2Id), List.of(newGame3, newGame4), isPhysical, new ArrayList<>());
        final ResultActions result2 = mockMvc.perform(
                put(videoGameBoxUrlSlash + responseDto1.id())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonContent)
        );
        factory.validateVideoGameBoxResponseBody(result2, boxTitle, boxSystem, expectedVideoGameResults2, isPhysical, expectedCollection, new ArrayList<>());

        //the game2 has been removed
        //make sure the returned object has the different games and has deleted the old video games
        //TODO finish this
    }

    private SlimVideoGame convertToExpectedSlimVideoGameResponse(VideoGameRequestDto requestDto, SystemResponseDto expectedSystem) {
        return new SlimVideoGame(0, requestDto.title(), expectedSystem, null, null, null, requestDto.customFieldValues());
    }

    private SlimVideoGameBox convertToExpectedSlimVideoGameBox(VideoGameBoxResponseDto responseDto, SystemResponseDto expectedSystem) {
        return new SlimVideoGameBox(0, responseDto.title(), expectedSystem, responseDto.isPhysical(), responseDto.isCollection(), null, null, null, responseDto.customFieldValues());
    }

    private VideoGameResponseDto convertToVideoGameResponseDto(SlimVideoGame slimVideoGame, List<VideoGameBoxResponseDto> boxResponseDtos) {
        List<SlimVideoGameBox> relatedVideoGameBoxes = new ArrayList<>();
        for (VideoGameBoxResponseDto boxResponseDto : boxResponseDtos) {
            relatedVideoGameBoxes.add(convertToExpectedSlimVideoGameBox(boxResponseDto, boxResponseDto.system()));
        }
        return new VideoGameResponseDto(
                Keychain.VIDEO_GAME_KEY,
                slimVideoGame.id(),
                slimVideoGame.title(),
                slimVideoGame.system(),
                relatedVideoGameBoxes,
                null, null, null, //timestamps
                slimVideoGame.customFieldValues()
        );
    }

    private void validateVideoGameResponseBody(ResultActions result, String expectedTitle, SystemResponseDto expectedSystem, List<SlimVideoGameBox> expectedVideoGameBoxes,
                                               List<CustomFieldValue> customFieldValues) throws Exception {
        result.andExpectAll(
                jsonPath("$.data.key").value(Keychain.VIDEO_GAME_KEY),
                jsonPath("$.data.id").isNotEmpty(),
                jsonPath("$.data.title").value(expectedTitle),
                jsonPath("$.data.videoGameBoxes.size()").value(expectedVideoGameBoxes.size()),
                jsonPath("$.errors").isEmpty()
        );
        VideoGameResponseDto responseDto = factory.resultToVideoGameResponseDto(result);
        factory.validateSystem(expectedSystem, responseDto.system());
        validateSlimVideoGameBoxes(expectedVideoGameBoxes, responseDto.videoGameBoxes());
        factory.validateCustomFieldValues(customFieldValues, responseDto.customFieldValues());
    }

    private void validateVideoGameResponseBody(ResultActions result, List<VideoGameResponseDto> expectedVideoGames) throws Exception {
        result.andExpectAll(
                jsonPath("$.data").exists(),
                jsonPath("$.errors").isEmpty()
        );

        final MvcResult mvcResult = result.andReturn();
        final String responseString = mvcResult.getResponse().getContentAsString();
        final Map<String, List<VideoGameResponseDto>> body = new ObjectMapper().readValue(responseString, new TypeReference<>() {
        });
        final List<VideoGameResponseDto> returnedVideoGames = body.get("data");
        //test the order, and the deserialization
        for (int i = 0; i < returnedVideoGames.size(); i++) {
            VideoGameResponseDto expectedGame = expectedVideoGames.get(i);
            VideoGameResponseDto returnedGame = returnedVideoGames.get(i);
            assertAll(
                    "The response body for videoGames is not formatted correctly when returned in a list format",
                    () -> assertEquals(Keychain.VIDEO_GAME_KEY, returnedGame.key()),
                    () -> assertEquals(expectedGame.id(), returnedGame.id()),
                    () -> assertEquals(expectedGame.title(), returnedGame.title()),
                    () -> factory.validateSystem(expectedGame.system(), returnedGame.system()),
                    () -> validateSlimVideoGameBoxes(expectedGame.videoGameBoxes(), returnedGame.videoGameBoxes())
            );
            factory.validateCustomFieldValues(expectedGame.customFieldValues(), returnedGame.customFieldValues());
        }
    }

    private void validateSlimVideoGameBoxes(List<SlimVideoGameBox> expectedVideoGameBoxes, List<SlimVideoGameBox> actualVideoGameBoxes) {
        assertEquals(expectedVideoGameBoxes.size(), actualVideoGameBoxes.size(), "The number of returned slim video games did not matched the number of expected slim video games.");
        for (int i = 0; i < actualVideoGameBoxes.size(); i++) {
            SlimVideoGameBox returnedGame = actualVideoGameBoxes.get(i);
            SlimVideoGameBox expectedGame = expectedVideoGameBoxes.get(i);
            if (expectedGame.id() == 0) {
                assertAll(
                        "The returned slim video games didn't match the expected slim video games.",
                        () -> assertEquals(expectedGame.title(), returnedGame.title()),
                        () -> factory.validateSystem(expectedGame.system(), returnedGame.system()),
                        () -> assertEquals(expectedGame.physical(), returnedGame.physical()),
                        () -> assertEquals(expectedGame.collection(), returnedGame.collection()),
                        () -> factory.validateCustomFieldValues(expectedGame.customFieldValues(), returnedGame.customFieldValues())
                );
            } else {
                assertAll(
                        "The returned slim video games didn't match the expected slim video games.",
                        () -> assertEquals(expectedGame.id(), returnedGame.id()),
                        () -> assertEquals(expectedGame.title(), returnedGame.title()),
                        () -> factory.validateSystem(expectedGame.system(), returnedGame.system()),
                        () -> assertEquals(expectedGame.physical(), returnedGame.physical()),
                        () -> assertEquals(expectedGame.collection(), returnedGame.collection()),
                        () -> factory.validateCustomFieldValues(expectedGame.customFieldValues(), returnedGame.customFieldValues())
                );
            }
        }
    }

}

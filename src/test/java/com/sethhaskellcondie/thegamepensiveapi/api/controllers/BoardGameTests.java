package com.sethhaskellcondie.thegamepensiveapi.api.controllers;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sethhaskellcondie.thegamepensiveapi.TestFactory;
import com.sethhaskellcondie.thegamepensiveapi.domain.Keychain;
import com.sethhaskellcondie.thegamepensiveapi.domain.customfield.CustomFieldValue;
import com.sethhaskellcondie.thegamepensiveapi.domain.entity.boardgame.BoardGameResponseDto;
import com.sethhaskellcondie.thegamepensiveapi.domain.entity.boardgamebox.BoardGameBoxResponseDto;
import com.sethhaskellcondie.thegamepensiveapi.domain.entity.boardgamebox.SlimBoardGameBox;
import com.sethhaskellcondie.thegamepensiveapi.domain.entity.system.SystemResponseDto;
import com.sethhaskellcondie.thegamepensiveapi.domain.entity.videogame.VideoGameResponseDto;
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

@SpringBootTest
@ActiveProfiles("test-container")
@AutoConfigureMockMvc
public class BoardGameTests {

    @Autowired
    private MockMvc mockMvc;
    private TestFactory factory;
    private final String baseUrl = "/v1/boardGames";
    private final String baseUrlSlash = "/v1/boardGames/";

    @BeforeEach
    void setUp() {
        factory = new TestFactory(mockMvc);
    }


    @Test
    void postBoardGameWithCustomFieldValues_ValidPayload_BoardGameCreatedAndReturned() throws Exception {
        final String expectedTitle = "Mega Man The Board Game";
        final List<CustomFieldValue> expectedCustomFieldValues = List.of(
                new CustomFieldValue(0, "Owned", "boolean", "true"),
                new CustomFieldValue(0, "Best Player Count", "number", "3"),
                new CustomFieldValue(0, "Publisher", "text", "Jasco")
        );

        final ResultActions result = factory.postBoardGameReturnResult(expectedTitle, expectedCustomFieldValues);

        validateBoardGameResponseBody(result, expectedTitle, new ArrayList<>(), expectedCustomFieldValues);

        final BoardGameResponseDto responseDto = factory.resultToBoardGameResponseDto(result);
        updateExistingBoardGame_UpdateBoardGameAndCustomFieldValue_ReturnOk(responseDto, responseDto.customFieldValues());
    }

    void updateExistingBoardGame_UpdateBoardGameAndCustomFieldValue_ReturnOk(BoardGameResponseDto existingVideoGame, List<CustomFieldValue> existingCustomFieldValue) throws Exception {
        final String updatedTitle = "Power Rangers The Deckbuilding Game";
        final CustomFieldValue customFieldValueToUpdate = existingCustomFieldValue.get(0);
        existingCustomFieldValue.remove(0);
        final CustomFieldValue updatedValue = new CustomFieldValue(
                customFieldValueToUpdate.getCustomFieldId(),
                "Updated" + customFieldValueToUpdate.getCustomFieldName(),
                customFieldValueToUpdate.getCustomFieldType(),
                "false"
        );
        existingCustomFieldValue.add(updatedValue);

        final String jsonContent = factory.formatBoardGamePayload(updatedTitle, existingCustomFieldValue);
        final ResultActions result = mockMvc.perform(
                put(baseUrlSlash + existingVideoGame.id())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonContent)
        );

        result.andExpect(status().isOk());
        validateBoardGameResponseBody(result, updatedTitle, new ArrayList<>(), existingCustomFieldValue);
    }

    @Test
    void postBoardGame_TitleBlank_ReturnBadRequest() throws Exception {
        //Two errors the title cannot be blank
        final String jsonContent = factory.formatBoardGamePayload("", null);

        final ResultActions result = mockMvc.perform(
                post(baseUrl)
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
    void getOneBoardGame_GameExists_BoardGameSerializedCorrectly() throws Exception {
        final String title = "Pandemic";
        final List<CustomFieldValue> customFieldValues = List.of(new CustomFieldValue(0, "customFieldName", "text", "value"));
        ResultActions postResult = factory.postBoardGameReturnResult(title, customFieldValues);
        final BoardGameResponseDto expectedDto = factory.resultToBoardGameResponseDto(postResult);

        final ResultActions result = mockMvc.perform(get(baseUrlSlash + expectedDto.id()));

        result.andExpectAll(
                status().isOk(),
                content().contentType(MediaType.APPLICATION_JSON)
        );
        validateBoardGameResponseBody(result, expectedDto.title(), expectedDto.boardGameBoxes(), customFieldValues);

        final BoardGameResponseDto responseDto = factory.resultToBoardGameResponseDto(result);
        getOneBoardGame_AfterAddingOneBoardGameBox_BoardGameSerializedCorrectly(responseDto);
    }

    void getOneBoardGame_AfterAddingOneBoardGameBox_BoardGameSerializedCorrectly(BoardGameResponseDto existingBoardGame) throws Exception {
        BoardGameBoxResponseDto boardGameBoxResponse = factory.postBoardGameBox(existingBoardGame.id());
        SlimBoardGameBox slimBoardGameBox = factory.convertBoardGameBoxResponseToSlimBoardGameBox(boardGameBoxResponse);

        final ResultActions result = mockMvc.perform(get(baseUrlSlash + existingBoardGame.id()));

        result.andExpectAll(
                status().isOk(),
                content().contentType(MediaType.APPLICATION_JSON)
        );
        validateBoardGameResponseBody(result, existingBoardGame.title(), List.of(slimBoardGameBox), existingBoardGame.customFieldValues());
    }

//    @Test
//    void getOneVideoGame_VideoGameMissing_NotFoundReturned() throws Exception {
//        final ResultActions result = mockMvc.perform(get(baseUrl + "/-1"));
//
//        result.andExpectAll(
//                status().isNotFound(),
//                jsonPath("$.data").isEmpty(),
//                jsonPath("$.errors.size()").value(1)
//        );
//    }
//
//    @Test
//    void getAllVideoGames_StartsWithFilter_VideoGameListReturned() throws Exception {
//        //This is used in the following test
//        final String customFieldName = "Custom";
//        final String customFieldType = "number";
//        final String customFieldKey = Keychain.VIDEO_GAME_KEY;
//        final int customFieldId = factory.postCustomFieldReturnId(customFieldName, customFieldType, customFieldKey);
//
//        final String title1 = "NES Mega Man";
//        final SystemResponseDto relatedSystem1 = factory.postSystem();
//        final List<CustomFieldValue> CustomFieldValues1 = List.of(new CustomFieldValue(customFieldId, customFieldName, customFieldType, "1"));
//        final ResultActions result1 = factory.postVideoGameReturnResult(title1, relatedSystem1.id(), CustomFieldValues1);
//        final VideoGameResponseDto gameDto1 = factory.resultToVideoGameResponseDto(result1);
//
//        final String title2 = "NES Mega Man 2";
//        final SystemResponseDto relatedSystem2 = factory.postSystem();
//        final List<CustomFieldValue> CustomFieldValues2 = List.of(new CustomFieldValue(customFieldId, customFieldName, customFieldType, "2"));
//        final ResultActions result2 = factory.postVideoGameReturnResult(title2, relatedSystem2.id(), CustomFieldValues2);
//        final VideoGameResponseDto gameDto2 = factory.resultToVideoGameResponseDto(result2);
//
//        final String title3 = "SNES Mega Man 7";
//        final SystemResponseDto relatedSystem3 = factory.postSystem();
//        final List<CustomFieldValue> CustomFieldValues3 = List.of(new CustomFieldValue(customFieldId, customFieldName, customFieldType, "3"));
//        final ResultActions result3 = factory.postVideoGameReturnResult(title3, relatedSystem3.id(), CustomFieldValues3);
//        final VideoGameResponseDto gameDto3 = factory.resultToVideoGameResponseDto(result3);
//
//        final Filter filter = new Filter(Keychain.VIDEO_GAME_KEY, "text", "title", Filter.OPERATOR_STARTS_WITH, "NES ", false);
//        final String formattedJson = factory.formatFiltersPayload(filter);
//
//        final ResultActions result = mockMvc.perform(post(baseUrl + "/function/search")
//                .contentType(MediaType.APPLICATION_JSON)
//                .content(formattedJson)
//        );
//
//        result.andExpectAll(
//                status().isOk(),
//                content().contentType(MediaType.APPLICATION_JSON)
//        );
//        validateVideoGameResponseBody(result, List.of(gameDto1, gameDto2));
//
//        final Filter customFilter = new Filter(customFieldKey, customFieldType, customFieldName, Filter.OPERATOR_GREATER_THAN, "2", true);
//        getWithFilters_GreaterThanCustomFilter_VideoGameListReturned(customFilter, List.of(gameDto3));
//    }
//
//    void getWithFilters_GreaterThanCustomFilter_VideoGameListReturned(Filter filter, List<VideoGameResponseDto> expectedGames) throws Exception {
//
//        final String jsonContent = factory.formatFiltersPayload(filter);
//
//        final ResultActions result = mockMvc.perform(post(baseUrl + "/function/search")
//                .contentType(MediaType.APPLICATION_JSON)
//                .content(jsonContent)
//        );
//
//        result.andExpectAll(
//                status().isOk(),
//                content().contentType(MediaType.APPLICATION_JSON)
//        );
//        validateVideoGameResponseBody(result, expectedGames);
//    }
//
//    @Test
//    void getAllVideoGames_NoResultFilter_EmptyArrayReturned() throws Exception {
//        final Filter filter = new Filter(Keychain.VIDEO_GAME_KEY, "text", "title", Filter.OPERATOR_STARTS_WITH, "NoResults", false);
//        final String formattedJson = factory.formatFiltersPayload(filter);
//        final ResultActions result = mockMvc.perform(post(baseUrl + "/function/search")
//                .contentType(MediaType.APPLICATION_JSON)
//                .content(formattedJson)
//        );
//
//        result.andExpectAll(
//                status().isOk(),
//                content().contentType(MediaType.APPLICATION_JSON),
//                jsonPath("$.data").value(new ArrayList<>()),
//                jsonPath("$.errors").isEmpty()
//        );
//    }
//
//    @Test
//    void updateExistingVideoGame_InvalidId_ReturnNotFound() throws Exception {
//
//        final String jsonContent = factory.formatVideoGamePayload("invalidId", 1, null);
//        final ResultActions result = mockMvc.perform(
//                put(baseUrl + "/-1")
//                        .contentType(MediaType.APPLICATION_JSON)
//                        .content(jsonContent)
//        );
//
//        result.andExpectAll(
//                status().isNotFound(),
//                jsonPath("$.data").isEmpty(),
//                jsonPath("$.errors.size()").value(1)
//        );
//    }
//
//    @Test
//    void deleteExistingVideoGame_GameExists_ReturnNoContent() throws Exception {
//        VideoGameResponseDto existingVideoGame = factory.postVideoGame();
//
//        final ResultActions result = mockMvc.perform(
//                delete(baseUrlSlash + existingVideoGame.id())
//        );
//
//        result.andExpectAll(
//                status().isNoContent(),
//                jsonPath("$.data").isEmpty(),
//                jsonPath("$.errors").isEmpty()
//        );
//    }
//
//    @Test
//    void deleteExistingToy_InvalidId_ReturnNotFound() throws Exception {
//        final ResultActions result = mockMvc.perform(
//                delete(baseUrl + "/-1")
//        );
//
//        result.andExpectAll(
//                status().isNotFound(),
//                jsonPath("$.data").isEmpty(),
//                jsonPath("$.errors.size()").value(1)
//        );
//    }

    private void validateBoardGameResponseBody(ResultActions result, String expectedTitle, List<SlimBoardGameBox> expectedBoardGameBoxes, List<CustomFieldValue> customFieldValues) throws Exception {
        result.andExpectAll(
                jsonPath("$.data.key").value(Keychain.BOARD_GAME_KEY),
                jsonPath("$.data.id").isNotEmpty(),
                jsonPath("$.data.title").value(expectedTitle),
                jsonPath("$.data.boardGameBoxes").value(expectedBoardGameBoxes),
                jsonPath("$.errors").isEmpty()
        );
        BoardGameResponseDto responseDto = factory.resultToBoardGameResponseDto(result);
        factory.validateCustomFieldValues(responseDto.customFieldValues(), customFieldValues);
    }

//    private void validateVideoGameResponseBody(ResultActions result, List<VideoGameResponseDto> expectedGames) throws Exception {
//        result.andExpectAll(
//                jsonPath("$.data").exists(),
//                jsonPath("$.errors").isEmpty()
//        );
//
//        final MvcResult mvcResult = result.andReturn();
//        final String responseString = mvcResult.getResponse().getContentAsString();
//        final Map<String, List<VideoGameResponseDto>> body = new ObjectMapper().readValue(responseString, new TypeReference<>() {
//        });
//        final List<VideoGameResponseDto> returnedToys = body.get("data");
//        //test the order, and the deserialization
//        for (int i = 0; i < returnedToys.size(); i++) {
//            VideoGameResponseDto expectedGame = expectedGames.get(i);
//            VideoGameResponseDto returnedGame = returnedToys.get(i);
//            assertAll(
//                    "The response body for videoGames is not formatted correctly",
//                    () -> assertEquals(Keychain.VIDEO_GAME_KEY, returnedGame.key()),
//                    () -> assertEquals(expectedGame.id(), returnedGame.id()),
//                    () -> assertEquals(expectedGame.title(), returnedGame.title()),
//                    () -> assertEquals(expectedGame.systemId(), returnedGame.systemId()),
//                    () -> assertEquals(expectedGame.systemName(), returnedGame.systemName())
//            );
//            factory.validateCustomFieldValues(expectedGame.customFieldValues(), returnedGame.customFieldValues());
//        }
//    }
}

package com.sethhaskellcondie.thegamepensieveapi.api.controllers;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sethhaskellcondie.thegamepensieveapi.TestFactory;
import com.sethhaskellcondie.thegamepensieveapi.domain.Keychain;
import com.sethhaskellcondie.thegamepensieveapi.domain.customfield.CustomFieldValue;
import com.sethhaskellcondie.thegamepensieveapi.domain.entity.boardgame.BoardGameResponseDto;
import com.sethhaskellcondie.thegamepensieveapi.domain.entity.boardgamebox.BoardGameBoxResponseDto;
import com.sethhaskellcondie.thegamepensieveapi.domain.entity.boardgamebox.SlimBoardGameBox;
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
        getOneBoardGame_AfterAddingOTwoBoardGameBoxes_BoardGameSerializedCorrectly(responseDto);
    }

    void getOneBoardGame_AfterAddingOTwoBoardGameBoxes_BoardGameSerializedCorrectly(BoardGameResponseDto existingBoardGame) throws Exception {
        BoardGameBoxResponseDto boardGameBoxResponse1 = factory.postBoardGameBox(existingBoardGame.id());
        SlimBoardGameBox slimBoardGameBox1 = factory.convertBoardGameBoxResponseToSlimBoardGameBox(boardGameBoxResponse1);
        BoardGameBoxResponseDto boardGameBoxResponse2 = factory.postBoardGameBox(existingBoardGame.id());
        SlimBoardGameBox slimBoardGameBox2 = factory.convertBoardGameBoxResponseToSlimBoardGameBox(boardGameBoxResponse2);

        final ResultActions result = mockMvc.perform(get(baseUrlSlash + existingBoardGame.id()));

        result.andExpectAll(
                status().isOk(),
                content().contentType(MediaType.APPLICATION_JSON)
        );
        validateBoardGameResponseBody(result, existingBoardGame.title(), List.of(slimBoardGameBox1, slimBoardGameBox2), existingBoardGame.customFieldValues());
    }

    @Test
    void getOneBoardGame_BoardGameMissing_NotFoundReturned() throws Exception {
        final ResultActions result = mockMvc.perform(get(baseUrl + "/-1"));

        result.andExpectAll(
                status().isNotFound(),
                jsonPath("$.data").isEmpty(),
                jsonPath("$.errors.size()").value(1)
        );
    }

    @Test
    void getAllBoardGames_StartsWithFilter_BoardGameListReturned() throws Exception {
        //This is used in the following test
        final String customFieldName = "Custom";
        final String customFieldType = "number";
        final String customFieldKey = Keychain.BOARD_GAME_KEY;
        final int customFieldId = factory.postCustomFieldReturnId(customFieldName, customFieldType, customFieldKey);

        final String title1 = "Mega Man the Board Game";
        final List<CustomFieldValue> CustomFieldValues1 = List.of(new CustomFieldValue(customFieldId, customFieldName, customFieldType, "1"));
        final ResultActions result1 = factory.postBoardGameReturnResult(title1, CustomFieldValues1);
        final BoardGameResponseDto gameDto1 = factory.resultToBoardGameResponseDto(result1);

        final String title2 = "Mega Man the Deckbuilding Game";
        final List<CustomFieldValue> CustomFieldValues2 = List.of(new CustomFieldValue(customFieldId, customFieldName, customFieldType, "2"));
        final ResultActions result2 = factory.postBoardGameReturnResult(title2, CustomFieldValues2);
        final BoardGameResponseDto gameDto2 = factory.resultToBoardGameResponseDto(result2);

        final String title3 = "Power Rangers the Deckbuilding Game";
        final List<CustomFieldValue> CustomFieldValues3 = List.of(new CustomFieldValue(customFieldId, customFieldName, customFieldType, "3"));
        final ResultActions result3 = factory.postBoardGameReturnResult(title3, CustomFieldValues3);
        final BoardGameResponseDto gameDto3 = factory.resultToBoardGameResponseDto(result3);

        final Filter filter = new Filter(Keychain.BOARD_GAME_KEY, "text", "title", Filter.OPERATOR_STARTS_WITH, "Mega Man", false);
        final String formattedJson = factory.formatFiltersPayload(filter);

        final ResultActions result = mockMvc.perform(post(baseUrl + "/function/search")
                .contentType(MediaType.APPLICATION_JSON)
                .content(formattedJson)
        );

        result.andExpectAll(
                status().isOk(),
                content().contentType(MediaType.APPLICATION_JSON)
        );
        validateBoardGameResponseBody(result, List.of(gameDto1, gameDto2));

        final Filter customFilter = new Filter(customFieldKey, customFieldType, customFieldName, Filter.OPERATOR_GREATER_THAN, "2", true);
        getWithFilters_GreaterThanCustomFilter_BoardGameListReturned(customFilter, List.of(gameDto3));
    }

    void getWithFilters_GreaterThanCustomFilter_BoardGameListReturned(Filter filter, List<BoardGameResponseDto> expectedGames) throws Exception {

        final String jsonContent = factory.formatFiltersPayload(filter);

        final ResultActions result = mockMvc.perform(post(baseUrl + "/function/search")
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonContent)
        );

        result.andExpectAll(
                status().isOk(),
                content().contentType(MediaType.APPLICATION_JSON)
        );
        validateBoardGameResponseBody(result, expectedGames);
    }

    @Test
    void getAllBoardGames_NoResultFilter_EmptyArrayReturned() throws Exception {
        final Filter filter = new Filter(Keychain.BOARD_GAME_KEY, "text", "title", Filter.OPERATOR_STARTS_WITH, "NoResults", false);
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
    void updateExistingBoardGame_InvalidId_ReturnNotFound() throws Exception {

        final String jsonContent = factory.formatBoardGamePayload("invalidId", null);
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
    void deleteExistingBoardGame_GameExists_ReturnNoContent() throws Exception {
        BoardGameResponseDto existingVideoGame = factory.postBoardGame();

        final ResultActions result = mockMvc.perform(
                delete(baseUrlSlash + existingVideoGame.id())
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
                jsonPath("$.errors.size()").value(1)
        );
    }

    private void validateBoardGameResponseBody(ResultActions result, String expectedTitle, List<SlimBoardGameBox> expectedBoardGameBoxes, List<CustomFieldValue> customFieldValues) throws Exception {
        result.andExpectAll(
                jsonPath("$.data.key").value(Keychain.BOARD_GAME_KEY),
                jsonPath("$.data.id").isNotEmpty(),
                jsonPath("$.data.title").value(expectedTitle),
                //jsonPath("$.data.boardGameBoxes").value(expectedBoardGameBoxes), Future Update: update this to test the board game boxes as well
                jsonPath("$.errors").isEmpty()
        );
        BoardGameResponseDto responseDto = factory.resultToBoardGameResponseDto(result);
        factory.validateCustomFieldValues(responseDto.customFieldValues(), customFieldValues);
    }

    private void validateBoardGameResponseBody(ResultActions result, List<BoardGameResponseDto> expectedGames) throws Exception {
        result.andExpectAll(
                jsonPath("$.data").exists(),
                jsonPath("$.errors").isEmpty()
        );

        final MvcResult mvcResult = result.andReturn();
        final String responseString = mvcResult.getResponse().getContentAsString();
        final Map<String, List<BoardGameResponseDto>> body = new ObjectMapper().readValue(responseString, new TypeReference<>() {
        });
        final List<BoardGameResponseDto> returnedToys = body.get("data");
        //test the order, and the deserialization
        for (int i = 0; i < returnedToys.size(); i++) {
            BoardGameResponseDto expectedGame = expectedGames.get(i);
            BoardGameResponseDto returnedGame = returnedToys.get(i);
            assertAll(
                    "The response body for boardGames is not formatted correctly",
                    () -> assertEquals(Keychain.BOARD_GAME_KEY, returnedGame.key()),
                    () -> assertEquals(expectedGame.id(), returnedGame.id()),
                    () -> assertEquals(expectedGame.title(), returnedGame.title())
                    //jsonPath("$.data.boardGameBoxes").value(expectedBoardGameBoxes), Future Update: update this to test the board game boxes as well
            );
            factory.validateCustomFieldValues(expectedGame.customFieldValues(), returnedGame.customFieldValues());
        }
    }
}

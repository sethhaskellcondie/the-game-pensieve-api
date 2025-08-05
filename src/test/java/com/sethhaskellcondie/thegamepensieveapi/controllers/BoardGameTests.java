package com.sethhaskellcondie.thegamepensieveapi.controllers;

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

/**
 * Board games represent a list of games, and are similar to video games boxes this is how the board games appear on a shelf.
 * The relationship is different from video games and video game boxes. While video games and video game boxes are many to many,
 * board games and board game boxes are one to many with a parent board game.
 * Board games contain one or more board game boxes. For example Villainous is a single board game but there are many different boxes for that single game.
 * Because of this board game boxes must be created and deleted through the parent board game.
 * Board games must contain a title. They can be expansions, and they can also be stand-alone games.
 * This test suite will focus on teh board games, the board game boxes will be tested in the BoardGameTests suite.
 */

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
        //test 1 - when valid post send, then 201 (created) returned
        final String expectedTitle = "Mega Man The Board Game";
        final List<CustomFieldValue> expectedCustomFieldValues = List.of(
                new CustomFieldValue(0, "Owned", "boolean", "true"),
                new CustomFieldValue(0, "Best Player Count", "number", "3"),
                new CustomFieldValue(0, "Publisher", "text", "Jasco")
        );

        final ResultActions result = factory.postBoardGameReturnResult(expectedTitle, expectedCustomFieldValues);

        validateBoardGameResponseBody(result, expectedTitle, new ArrayList<>(), expectedCustomFieldValues);

        final BoardGameResponseDto existingVideoGame = factory.resultToBoardGameResponseDto(result);


        //test 2 - when valid patch send, then ok (200) returned
        final List<CustomFieldValue> existingCustomFieldValue = existingVideoGame.customFieldValues();
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
        final ResultActions result2 = mockMvc.perform(
                put(baseUrlSlash + existingVideoGame.id())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonContent)
        );

        result2.andExpect(status().isOk());
        validateBoardGameResponseBody(result2, updatedTitle, new ArrayList<>(), existingCustomFieldValue);
    }

    @Test
    void postBoardGame_TitleBlank_ReturnBadRequest() throws Exception {
        //One error: the title cannot be blank
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
    void postBoardGameBox_InvalidBoardGameId_ReturnBadRequest() throws Exception {
        // Error: board game box must have a valid parent board game ID
        final String title = "Test Board Game Box";
        final String jsonContent = factory.formatBoardGameBoxPayload(title, false, false, null, Integer.MAX_VALUE, null);

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
        // test 1: get one board game, happy path response shape correctly
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

        final BoardGameResponseDto existingBoardGame = factory.resultToBoardGameResponseDto(result);

        // test 2: get one board game after adding two boxes, happy path response shaped correctly
        BoardGameBoxResponseDto boardGameBoxResponse1 = factory.postBoardGameBox(existingBoardGame.id());
        SlimBoardGameBox slimBoardGameBox1 = factory.convertBoardGameBoxResponseToSlimBoardGameBox(boardGameBoxResponse1);
        BoardGameBoxResponseDto boardGameBoxResponse2 = factory.postBoardGameBox(existingBoardGame.id());
        SlimBoardGameBox slimBoardGameBox2 = factory.convertBoardGameBoxResponseToSlimBoardGameBox(boardGameBoxResponse2);

        final ResultActions result2 = mockMvc.perform(get(baseUrlSlash + existingBoardGame.id()));

        result2.andExpectAll(
                status().isOk(),
                content().contentType(MediaType.APPLICATION_JSON)
        );
        validateBoardGameResponseBody(result2, existingBoardGame.title(), List.of(slimBoardGameBox1, slimBoardGameBox2), existingBoardGame.customFieldValues());
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
        final String customFieldName = "Custom";
        final String customFieldType = "number";
        final String customFieldKey = Keychain.BOARD_GAME_KEY;
        final int customFieldId = factory.postCustomFieldReturnId(customFieldName, customFieldType, customFieldKey);

        final String title1 = "Mega Man the Board Game";
        final List<CustomFieldValue> customFieldValues1 = List.of(new CustomFieldValue(customFieldId, customFieldName, customFieldType, "1"));
        final ResultActions result1 = factory.postBoardGameReturnResult(title1, customFieldValues1);
        final BoardGameResponseDto gameDto1 = factory.resultToBoardGameResponseDto(result1);

        final String title2 = "Mega Man the Deckbuilding Game";
        final List<CustomFieldValue> customFieldValues2 = List.of(new CustomFieldValue(customFieldId, customFieldName, customFieldType, "2"));
        final ResultActions result2 = factory.postBoardGameReturnResult(title2, customFieldValues2);
        final BoardGameResponseDto gameDto2 = factory.resultToBoardGameResponseDto(result2);

        final String title3 = "Power Rangers the Deckbuilding Game";
        final List<CustomFieldValue> customFieldValues3 = List.of(new CustomFieldValue(customFieldId, customFieldName, customFieldType, "3"));
        final ResultActions result3 = factory.postBoardGameReturnResult(title3, customFieldValues3);
        final BoardGameResponseDto gameDto3 = factory.resultToBoardGameResponseDto(result3);

        final Filter filter = new Filter(Keychain.BOARD_GAME_KEY, "text", "title", Filter.OPERATOR_STARTS_WITH, "Mega Man", false);
        final String formattedJson = factory.formatFiltersPayload(filter);

        final ResultActions result4 = mockMvc.perform(post(baseUrl + "/function/search")
                .contentType(MediaType.APPLICATION_JSON)
                .content(formattedJson)
        );

        result4.andExpectAll(
                status().isOk(),
                content().contentType(MediaType.APPLICATION_JSON)
        );
        validateBoardGameResponseBody(result4, List.of(gameDto1, gameDto2));


        //test 2: when getting all board game boxes with a custom field filter, only a subset of the games are returned
        final Filter customFilter = new Filter(customFieldKey, customFieldType, customFieldName, Filter.OPERATOR_GREATER_THAN, "2", true);
        final String jsonContent = factory.formatFiltersPayload(customFilter);

        final ResultActions result5 = mockMvc.perform(post(baseUrl + "/function/search")
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonContent)
        );

        result5.andExpectAll(
                status().isOk(),
                content().contentType(MediaType.APPLICATION_JSON)
        );
        validateBoardGameResponseBody(result5, List.of(gameDto3));
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
    void putBoardGameBox_InvalidBoardGameId_ReturnBadRequest() throws Exception {
        BoardGameResponseDto boardGame = factory.postBoardGame();
        BoardGameBoxResponseDto boardGameBox = factory.postBoardGameBox(boardGame.id());

        final String jsonContent = factory.formatBoardGameBoxPayload(
                boardGameBox.title(), 
                boardGameBox.isExpansion(), 
                boardGameBox.isStandAlone(), 
                boardGameBox.baseSetId(), 
                Integer.MAX_VALUE, // Invalid board game ID
                new ArrayList<>()
        );
        
        final ResultActions result = mockMvc.perform(
                put(baseUrlSlash + boardGame.id() + "/boxes/" + boardGameBox.id())
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
    void deleteExistingBoardGame_InvalidId_ReturnNotFound() throws Exception {
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
                //jsonPath("$.data.boardGameBoxes").value(expectedBoardGameBoxes), TODO Future Update: update this to test the board game boxes as well
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
                    //jsonPath("$.data.boardGameBoxes").value(expectedBoardGameBoxes), TODO Future Update: update this to test the board game boxes as well
            );
            factory.validateCustomFieldValues(expectedGame.customFieldValues(), returnedGame.customFieldValues());
        }
    }
}

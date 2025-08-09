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
 * Board games are similar to video games they represent games in the collection but not how they appear on the shelf.
 * The relationship is different from video games and video game boxes. While video games and video game boxes are many to many,
 * board games and board game boxes are one to many with a parent board game.
 * Board games contain one or more board game boxes. For example Villainous is a single board game but there are many different boxes for that single game.
 * <p>
 * Board games do not have a POST (create) or DELETE endpoints, only PUT and GET endpoints.
 * Board games must include a title, and will be included as a parent game of a board game box.
 * Because of this, board games cannot be created or deleted through the boardGames endpoints instead this is done through the boardGameBox endpoints.
 * This test suite will focus on the board games, but some tests must interact with board game boxes to test the board game functionality.
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
    void postAndPatchBoardGameWithCustomFieldValuesInBoardGameBox_ValidPayload_BoardGameCreatedAndReturned() throws Exception {
        //test 1 - when valid post send, then 201 (created) returned
        final String expectedTitle = "Mega Man The Board Game";
        //TODO update the custom fields to be applied to the board game not the board game box
        final List<CustomFieldValue> expectedCustomFieldValues = List.of(
                new CustomFieldValue(0, "Owned", "boolean", "true"),
                new CustomFieldValue(0, "Best Player Count", "number", "3"),
                new CustomFieldValue(0, "Publisher", "text", "Jasco")
        );

        final ResultActions result = factory.postBoardGameBoxReturnResult(expectedTitle, false, false, null, null, expectedCustomFieldValues);
        final BoardGameBoxResponseDto boardGameBoxDto = factory.resultToBoardGameBoxResponseDto(result);
        final BoardGameResponseDto boardGameDto = boardGameBoxDto.boardGame();

        final ResultActions getBoardGameResult = mockMvc.perform(get(baseUrlSlash + boardGameDto.id()));
        getBoardGameResult.andExpect(status().isOk());
        validateBoardGameResponseBody(getBoardGameResult, expectedTitle, new ArrayList<>(), expectedCustomFieldValues);

        final BoardGameResponseDto existingBoardGame = boardGameBoxDto.boardGame();


        //test 2 - when valid patch send, then ok (200) returned
        final List<CustomFieldValue> existingCustomFieldValue = existingBoardGame.customFieldValues();
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
                put(baseUrlSlash + existingBoardGame.id())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonContent)
        );

        result2.andExpect(status().isOk());
        validateBoardGameResponseBody(result2, updatedTitle, new ArrayList<>(), existingCustomFieldValue);
    }

    @Test
    void postBoardGameBoxWithBlankTitle_ReturnBadRequest() throws Exception {
        //One error: the title cannot be blank
        final String jsonContent = factory.formatBoardGameBoxPayload("", false, false, null, null, null);

        final ResultActions result = mockMvc.perform(
                post("/v1/boardGameBoxes")
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
                post("/v1/boardGameBoxes")
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
        ResultActions postResult = factory.postBoardGameBoxReturnResult(title, false, false, null, null, customFieldValues);
        final BoardGameBoxResponseDto boardGameBoxDto = factory.resultToBoardGameBoxResponseDto(postResult);
        final BoardGameResponseDto expectedDto = boardGameBoxDto.boardGame();

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
        final ResultActions result1 = factory.postBoardGameBoxReturnResult(title1, false, false, null, null, customFieldValues1);
        final BoardGameResponseDto gameDto1 = factory.resultToBoardGameBoxResponseDto(result1).boardGame();

        final String title2 = "Mega Man the Deckbuilding Game";
        final List<CustomFieldValue> customFieldValues2 = List.of(new CustomFieldValue(customFieldId, customFieldName, customFieldType, "2"));
        final ResultActions result2 = factory.postBoardGameBoxReturnResult(title2, false, false, null, null, customFieldValues2);
        final BoardGameResponseDto gameDto2 = factory.resultToBoardGameBoxResponseDto(result2).boardGame();

        final String title3 = "Power Rangers the Deckbuilding Game";
        final List<CustomFieldValue> customFieldValues3 = List.of(new CustomFieldValue(customFieldId, customFieldName, customFieldType, "3"));
        final ResultActions result3 = factory.postBoardGameBoxReturnResult(title3, false, false, null, null, customFieldValues3);
        final BoardGameResponseDto gameDto3 = factory.resultToBoardGameBoxResponseDto(result3).boardGame();

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
        BoardGameBoxResponseDto boardGameBox = factory.postBoardGameBox();

        final String jsonContent = factory.formatBoardGameBoxPayload(
                boardGameBox.title(), 
                boardGameBox.isExpansion(), 
                boardGameBox.isStandAlone(), 
                boardGameBox.baseSetId(), 
                Integer.MAX_VALUE, // Invalid board game ID
                new ArrayList<>()
        );
        
        final ResultActions result = mockMvc.perform(
                put("/v1/boardGameBoxes/" + boardGameBox.id())
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
    void updateExistingBoardGameBox_ParentBoardGameHasNoOtherChildren_ParentBoardGameDeleted() throws Exception {
        final BoardGameBoxResponseDto existingBoardGameBox = factory.postBoardGameBox();
        final int parentBoardGameId = existingBoardGameBox.boardGame().id();
        final BoardGameBoxResponseDto existingBoardGameBox2 = factory.postBoardGameBox();
        final int parentBoardGameId2 = existingBoardGameBox2.boardGame().id();
        final String jsonContent = factory.formatBoardGameBoxPayload(
                existingBoardGameBox.title(),
                existingBoardGameBox.isExpansion(),
                existingBoardGameBox.isStandAlone(),
                existingBoardGameBox.baseSetId(),
                parentBoardGameId2, //Update the parentBoardGame to a different parentBoardGame (2) this should delete parentBoardGame
                new ArrayList<>()
        );

        final ResultActions result = mockMvc.perform(
                put("/v1/boardGameBoxes/" + existingBoardGameBox.id())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonContent)
        );
        result.andExpectAll(
                status().isOk(),
                jsonPath("$.data").exists(),
                jsonPath("$.errors").isEmpty()
        );

        mockMvc.perform(get(baseUrlSlash + parentBoardGameId))
                .andExpectAll(
                        status().isNotFound(),
                        jsonPath("$.data").isEmpty(),
                        jsonPath("$.errors.size()").value(1)
                );
    }

    @Test
    void deleteBoardGameBox_ParentBoardGameHasNoOtherChildren_ParentBoardGameAlsoDeleted() throws Exception {
        final BoardGameBoxResponseDto existingBoardGameBox = factory.postBoardGameBox();
        final int parentBoardGameId = existingBoardGameBox.boardGame().id();

        final ResultActions result = mockMvc.perform(
                delete("/v1/boardGameBoxes/" + existingBoardGameBox.id())
        );

        result.andExpectAll(
                status().isNoContent(),
                jsonPath("$.data").isEmpty(),
                jsonPath("$.errors").isEmpty()
        );

        mockMvc.perform(get(baseUrlSlash + parentBoardGameId))
                .andExpectAll(
                        status().isNotFound(),
                        jsonPath("$.data").isEmpty(),
                        jsonPath("$.errors.size()").value(1)
                );
    }

    @Test
    void deleteExistingBoardGameBox_GameExists_ReturnNoContent() throws Exception {
        BoardGameBoxResponseDto existingBoardGameBox = factory.postBoardGameBox();

        final ResultActions result = mockMvc.perform(
                delete("/v1/boardGameBoxes/" + existingBoardGameBox.id())
        );

        result.andExpectAll(
                status().isNoContent(),
                jsonPath("$.data").isEmpty(),
                jsonPath("$.errors").isEmpty()
        );
    }

    @Test
    void deleteExistingBoardGameBox_InvalidId_ReturnNotFound() throws Exception {
        final ResultActions result = mockMvc.perform(
                delete("/v1/boardGameBoxes/-1")
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

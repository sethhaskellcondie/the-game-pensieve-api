package com.sethhaskellcondie.thegamepensieveapi.controllers;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sethhaskellcondie.thegamepensieveapi.TestFactory;
import com.sethhaskellcondie.thegamepensieveapi.domain.Keychain;
import com.sethhaskellcondie.thegamepensieveapi.domain.customfield.CustomFieldValue;
import com.sethhaskellcondie.thegamepensieveapi.domain.entity.boardgame.BoardGameResponseDto;
import com.sethhaskellcondie.thegamepensieveapi.domain.entity.boardgamebox.BoardGameBoxResponseDto;
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
 * Board game boxes are like video game boxes they represent how the board games would be displayed on a shelf.
 * Board games boxes must contain a title. They can be expansions, and they can also be stand-alone games.
 * While the relationship is similar to the relationships between video games and video game boxes it is different,
 * video games and video game boxes have a many-to-many relationship. Board games have a one-to-many relationship with
 * board game boxes. All board game boxes will have a parent board game.
 */

@SpringBootTest
@ActiveProfiles("test-container")
@AutoConfigureMockMvc
public class BoardGameBoxTests {

    @Autowired
    private MockMvc mockMvc;
    private TestFactory factory;
    private final String baseUrl = "/v1/boardGameBoxes";
    private final String baseUrlSlash = "/v1/boardGameBoxes/";

    @BeforeEach
    void setUp() {
        factory = new TestFactory(mockMvc);
    }


    @Test
    void postBoardGameBoxWithCustomFieldValues_ValidPayload_BoardGameBoxCreatedAndReturned() throws Exception {
        //test 1 - when valid post sent, then 201 (created) returned
        final String expectedTitle = "Disney Villainous";
        final boolean expectedExpansion = false;
        final boolean expectedStandAlone = false;
        final BoardGameResponseDto relatedBoardGame = factory.postBoardGameBox().boardGame();
        final List<CustomFieldValue> expectedCustomFieldValues = List.of(
                new CustomFieldValue(0, "Has Solo Mode", "boolean", "true"),
                new CustomFieldValue(0, "Best Player Count", "number", "3"),
                new CustomFieldValue(0, "Distributed by", "text", "Ravensburger")
        );

        final ResultActions result = factory.postBoardGameBoxReturnResult(expectedTitle, expectedExpansion, expectedStandAlone, null, relatedBoardGame.id(), expectedCustomFieldValues);

        validateBoardGameBoxResponseBody(result, expectedTitle, expectedExpansion, expectedStandAlone, null, relatedBoardGame, expectedCustomFieldValues);

        //test 2 - when valid patch sent, then return 200 (ok) returned
        final BoardGameBoxResponseDto existingBoardGameBox = factory.resultToBoardGameBoxResponseDto(result);
        List<CustomFieldValue> existingCustomFieldValue = existingBoardGameBox.customFieldValues();
        final String updatedTitle = "Marvel Villainous";
        final CustomFieldValue customFieldValueToUpdate = existingCustomFieldValue.get(0);
        existingCustomFieldValue.remove(0);
        final CustomFieldValue updatedValue = new CustomFieldValue(
                customFieldValueToUpdate.getCustomFieldId(),
                "Updated " + customFieldValueToUpdate.getCustomFieldName(),
                customFieldValueToUpdate.getCustomFieldType(),
                "false"
        );
        existingCustomFieldValue.add(updatedValue);

        final String jsonContent = factory.formatBoardGameBoxPayload(updatedTitle, existingBoardGameBox.isExpansion(), existingBoardGameBox.isStandAlone(), existingBoardGameBox.baseSetId(),
                existingBoardGameBox.boardGame().id(), existingCustomFieldValue);
        final ResultActions result2 = mockMvc.perform(
                put(baseUrlSlash + existingBoardGameBox.id())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonContent)
        );

        result2.andExpect(status().isOk());
        validateBoardGameBoxResponseBody(result2, updatedTitle, existingBoardGameBox.isExpansion(), existingBoardGameBox.isStandAlone(), existingBoardGameBox.baseSetId(),
                existingBoardGameBox.boardGame(), existingCustomFieldValue);
    }

    @Test
    void postBoardGameBox_TitleBlank_ReturnBadRequest() throws Exception {
        // The title cannot be blank
        final String jsonContent = factory.formatBoardGameBoxPayload("", false, false, 0, null, null);

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
        //One error the board game ID passed in has no board game
        //can't pass in null or 0 otherwise a board game will automatically be created.
        final String jsonContent = factory.formatBoardGameBoxPayload("ValidTitle", false, false, 0, Integer.MAX_VALUE, null);

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
    void postBoardGameBox_MissingBoardGameId_BoardGameCreated() throws Exception {
        final String expectedTitle = "New Box and Game";
        final boolean expectedExpansion = true;
        final boolean expectedStandAlone = true;

        final ResultActions result = factory.postBoardGameBoxReturnResult(expectedTitle, expectedExpansion, expectedStandAlone, null, null, null);
        final BoardGameBoxResponseDto boxResponseDto = factory.resultToBoardGameBoxResponseDto(result);
        //This is only used as the expected result only testing the key, id, and title
        final BoardGameResponseDto boardGameResponseDto = new BoardGameResponseDto(Keychain.BOARD_GAME_BOX_KEY, boxResponseDto.boardGame().id(), expectedTitle, null, null, null, null, null);

        validateBoardGameBoxResponseBody(result, expectedTitle, expectedExpansion, expectedStandAlone, null, boardGameResponseDto, new ArrayList<>());
    }

    @Test
    void getOneBoardGameBox_BoxExists_BoardGameBoxSerializedCorrectly() throws Exception {
        final String title = "Freelancers";
        final boolean isExpansion = false;
        final boolean isStandAlone = true;
        final BoardGameResponseDto relatedBoardGame = factory.postBoardGameBox().boardGame();
        final List<CustomFieldValue> customFieldValues = List.of(new CustomFieldValue(0, "customFieldName", "text", "value"));
        ResultActions postResult = factory.postBoardGameBoxReturnResult(title, isExpansion, isStandAlone, null, relatedBoardGame.id(), customFieldValues);
        final BoardGameBoxResponseDto expectedDto = factory.resultToBoardGameBoxResponseDto(postResult);

        final ResultActions result = mockMvc.perform(get(baseUrlSlash + expectedDto.id()));

        result.andExpectAll(
                status().isOk(),
                content().contentType(MediaType.APPLICATION_JSON)
        );
        validateBoardGameBoxResponseBody(result, title, isExpansion, isStandAlone, null, expectedDto.boardGame(), customFieldValues);
    }

    @Test
    void getOneBoardGameBox_BoardGameBoxMissing_NotFoundReturned() throws Exception {
        final ResultActions result = mockMvc.perform(get(baseUrl + "/-1"));

        result.andExpectAll(
                status().isNotFound(),
                jsonPath("$.data").isEmpty(),
                jsonPath("$.errors.size()").value(1)
        );
    }

    @Test
    void getAllVideoGames_StartsWithFilter_VideoGameListReturned() throws Exception {
        // test 1 - when getting all board game boxes with a filter, only a subset of board game boxes are returned
        final String customFieldName = "Custom";
        final String customFieldType = "number";
        final String customFieldKey = Keychain.BOARD_GAME_BOX_KEY;
        final int customFieldId = factory.postCustomFieldReturnId(customFieldName, customFieldType, customFieldKey);

        final String title1 = "King of Tokyo";
        final List<CustomFieldValue> customFieldValues1 = List.of(new CustomFieldValue(customFieldId, customFieldName, customFieldType, "1"));
        final ResultActions result1 = factory.postBoardGameBoxReturnResult(title1, false, false, null, null, customFieldValues1);
        final BoardGameBoxResponseDto boardGameBoxDto1 = factory.resultToBoardGameBoxResponseDto(result1);

        final String title2 = "King of New York";
        final List<CustomFieldValue> customFieldValues2 = List.of(new CustomFieldValue(customFieldId, customFieldName, customFieldType, "2"));
        final ResultActions result2 = factory.postBoardGameBoxReturnResult(title2, false, false, null, null, customFieldValues2);
        final BoardGameBoxResponseDto boardGameBoxDto2 = factory.resultToBoardGameBoxResponseDto(result2);

        final String title3 = "Forgotten Waters";
        final List<CustomFieldValue> customFieldValues3 = List.of(new CustomFieldValue(customFieldId, customFieldName, customFieldType, "3"));
        final ResultActions result3 = factory.postBoardGameBoxReturnResult(title3, false, false, null, null, customFieldValues3);
        final BoardGameBoxResponseDto boardGameBoxDto3 = factory.resultToBoardGameBoxResponseDto(result3);

        final Filter filter = new Filter(Keychain.BOARD_GAME_BOX_KEY, "text", "title", Filter.OPERATOR_STARTS_WITH, "King of ", false);
        final String formattedJson = factory.formatFiltersPayload(filter);

        final ResultActions result = mockMvc.perform(post(baseUrl + "/function/search")
                .contentType(MediaType.APPLICATION_JSON)
                .content(formattedJson)
        );

        result.andExpectAll(
                status().isOk(),
                content().contentType(MediaType.APPLICATION_JSON)
        );
        validateBoardGameBoxResponseBody(result, List.of(boardGameBoxDto1, boardGameBoxDto2));

        final Filter customFilter = new Filter(customFieldKey, customFieldType, customFieldName, Filter.OPERATOR_GREATER_THAN, "2", true);


        // test 2 - when getting all board game boxes with a custom field filter, only a subset of the board game boxes is returned
        final String jsonContent = factory.formatFiltersPayload(customFilter);

        final ResultActions resultActions = mockMvc.perform(post(baseUrl + "/function/search")
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonContent)
        );

        resultActions.andExpectAll(
                status().isOk(),
                content().contentType(MediaType.APPLICATION_JSON)
        );
        validateBoardGameBoxResponseBody(resultActions, List.of(boardGameBoxDto3));
    }

    @Test
    void getAllBoardGameBoxes_NoResultFilter_EmptyArrayReturned() throws Exception {
        final Filter filter = new Filter(Keychain.BOARD_GAME_BOX_KEY, "text", "title", Filter.OPERATOR_STARTS_WITH, "NoResults", false);
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
    void updateExistingBoardGameBox_InvalidId_ReturnNotFound() throws Exception {
        final String jsonContent = factory.formatBoardGameBoxPayload("invalidId", false, false, 1, null, null);
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
    void deleteExistingBoardGameBox_InvalidId_ReturnNotFound() throws Exception {
        final ResultActions result = mockMvc.perform(
                delete(baseUrl + "/-1")
        );

        result.andExpectAll(
                status().isNotFound(),
                jsonPath("$.data").isEmpty(),
                jsonPath("$.errors.size()").value(1)
        );
    }

    // ------------------------- Private Helper Methods ------------------------------

    private void validateBoardGameBoxResponseBody(ResultActions result, String expectedTitle, boolean expectedExpansion, boolean expectedStandAlone, Integer expectedBaseSetId,
                                                  BoardGameResponseDto expectedBoardGameResponse, List<CustomFieldValue> customFieldValues) throws Exception {
        result.andExpectAll(
                jsonPath("$.data.key").value(Keychain.BOARD_GAME_BOX_KEY),
                jsonPath("$.data.id").isNotEmpty(),
                jsonPath("$.data.title").value(expectedTitle),
                jsonPath("$.data.isExpansion").value(expectedExpansion),
                jsonPath("$.data.isStandAlone").value(expectedStandAlone),
                jsonPath("$.data.baseSetId").value(expectedBaseSetId),
                jsonPath("$.data.boardGame.key").value(Keychain.BOARD_GAME_KEY),
                jsonPath("$.data.boardGame.id").value(expectedBoardGameResponse.id()),
                jsonPath("$.data.boardGame.title").value(expectedBoardGameResponse.title()),
                //not testing the boardGame.customFieldValues those are tested in the BoardGameTests
                jsonPath("$.errors").isEmpty()
        );
        BoardGameBoxResponseDto responseDto = factory.resultToBoardGameBoxResponseDto(result);
        factory.validateCustomFieldValues(responseDto.customFieldValues(), customFieldValues);
    }

    private void validateBoardGameBoxResponseBody(ResultActions result, List<BoardGameBoxResponseDto> expectedBoardGameBoxes) throws Exception {
        result.andExpectAll(
                jsonPath("$.data").exists(),
                jsonPath("$.errors").isEmpty()
        );

        final MvcResult mvcResult = result.andReturn();
        final String responseString = mvcResult.getResponse().getContentAsString();
        final Map<String, List<BoardGameBoxResponseDto>> body = new ObjectMapper().readValue(responseString, new TypeReference<>() {
        });
        final List<BoardGameBoxResponseDto> returnedToys = body.get("data");
        //test the order, and the deserialization
        for (int i = 0; i < returnedToys.size(); i++) {
            BoardGameBoxResponseDto expectedBoardGameBox = expectedBoardGameBoxes.get(i);
            BoardGameBoxResponseDto returnedBoardGameBox = returnedToys.get(i);
            assertAll(
                    "The response body for videoGames is not formatted correctly",
                    () -> assertEquals(Keychain.BOARD_GAME_BOX_KEY, returnedBoardGameBox.key()),
                    () -> assertEquals(expectedBoardGameBox.id(), returnedBoardGameBox.id()),
                    () -> assertEquals(expectedBoardGameBox.title(), returnedBoardGameBox.title()),
                    () -> assertEquals(expectedBoardGameBox.isExpansion(), returnedBoardGameBox.isExpansion()),
                    () -> assertEquals(expectedBoardGameBox.isStandAlone(), returnedBoardGameBox.isStandAlone()),
                    () -> assertEquals(expectedBoardGameBox.baseSetId(), returnedBoardGameBox.baseSetId()),
                    () -> assertEquals(expectedBoardGameBox.boardGame().id(), returnedBoardGameBox.boardGame().id()),
                    () -> assertEquals(expectedBoardGameBox.boardGame().title(), returnedBoardGameBox.boardGame().title())
            );
            factory.validateCustomFieldValues(expectedBoardGameBox.customFieldValues(), returnedBoardGameBox.customFieldValues());
        }
    }
}

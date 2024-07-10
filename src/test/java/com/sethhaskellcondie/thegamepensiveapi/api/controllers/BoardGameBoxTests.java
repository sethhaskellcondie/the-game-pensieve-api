package com.sethhaskellcondie.thegamepensiveapi.api.controllers;

import com.sethhaskellcondie.thegamepensiveapi.TestFactory;
import com.sethhaskellcondie.thegamepensiveapi.domain.Keychain;
import com.sethhaskellcondie.thegamepensiveapi.domain.customfield.CustomFieldValue;
import com.sethhaskellcondie.thegamepensiveapi.domain.entity.boardgame.BoardGameResponseDto;
import com.sethhaskellcondie.thegamepensiveapi.domain.entity.boardgamebox.BoardGameBoxResponseDto;
import com.sethhaskellcondie.thegamepensiveapi.domain.entity.system.SystemResponseDto;
import com.sethhaskellcondie.thegamepensiveapi.domain.entity.videogame.VideoGameResponseDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
        final String expectedTitle = "Disney Villainous";
        final boolean expectedExpansion = false;
        final boolean expectedStandAlone = false;
        final BoardGameResponseDto relatedBoardGame = factory.postBoardGame();
        final List<CustomFieldValue> expectedCustomFieldValues = List.of(
                new CustomFieldValue(0, "Has Solo Mode", "boolean", "true"),
                new CustomFieldValue(0, "Best Player Count", "number", "3"),
                new CustomFieldValue(0, "Distributed by", "text", "Ravensburger")
        );

        final ResultActions result = factory.postBoardGameBoxReturnResult(expectedTitle, expectedExpansion, expectedStandAlone, null, relatedBoardGame.id(), expectedCustomFieldValues);

        validateBoardGameBoxResponseBody(result, expectedTitle, expectedExpansion, expectedStandAlone, null, relatedBoardGame, expectedCustomFieldValues);

//        final VideoGameResponseDto responseDto = factory.resultToVideoGameResponseDto(result);
//        updateExistingVideoGame_UpdateVideoGameAndCustomFieldValue_ReturnOk(responseDto, responseDto.customFieldValues());
    }

    //TODO finish this

    void updateExistingVideoGame_UpdateVideoGameAndCustomFieldValue_ReturnOk(VideoGameResponseDto existingVideoGame, List<CustomFieldValue> existingCustomFieldValue) throws Exception {
//        final String updatedTitle = "Donald Duck";
//        final SystemResponseDto newRelatedSystem = factory.postSystem();
//        final CustomFieldValue customFieldValueToUpdate = existingCustomFieldValue.get(0);
//        existingCustomFieldValue.remove(0);
//        final CustomFieldValue updatedValue = new CustomFieldValue(
//                customFieldValueToUpdate.getCustomFieldId(),
//                "Updated" + customFieldValueToUpdate.getCustomFieldName(),
//                customFieldValueToUpdate.getCustomFieldType(),
//                "false"
//        );
//        existingCustomFieldValue.add(updatedValue);
//
//        final String jsonContent = factory.formatVideoGamePayload(updatedTitle, newRelatedSystem.id(), existingCustomFieldValue);
//        final ResultActions result = mockMvc.perform(
//                put(baseUrlSlash + existingVideoGame.id())
//                        .contentType(MediaType.APPLICATION_JSON)
//                        .content(jsonContent)
//        );
//
//        result.andExpect(status().isOk());
//        validateVideoGameResponseBody(result, updatedTitle, newRelatedSystem.id(), newRelatedSystem.name(), existingCustomFieldValue);
    }

    private void validateBoardGameBoxResponseBody(ResultActions result, String expectedTitle, boolean expectedExpansion, boolean expectedStandAlone, Integer expectedBaseSetId,
                                                  BoardGameResponseDto expectedBoardGameResponse, List<CustomFieldValue> customFieldValues) throws Exception {
        result.andExpectAll(
                jsonPath("$.data.key").value(Keychain.BOARD_GAME_BOX_KEY),
                jsonPath("$.data.id").isNotEmpty(),
                jsonPath("$.data.title").value(expectedTitle),
                jsonPath("$.data.isExpansion").value(expectedExpansion),
                jsonPath("$.data.isStandAlone").value(expectedStandAlone),
                jsonPath("$.data.baseSetId").value(expectedBaseSetId),
                jsonPath("$.data.boardGame.id").value(expectedBoardGameResponse.id()),
                jsonPath("$.data.boardGame.title").value(expectedBoardGameResponse.title()),
                //not testing the boardGame.customFieldValues those are tested in the BoardGameTests
                jsonPath("$.errors").isEmpty()
        );
        BoardGameBoxResponseDto responseDto = factory.resultToBoardGameBoxResponseDto(result);
        factory.validateCustomFieldValues(responseDto.customFieldValues(), customFieldValues);
    }
}

package com.sethhaskellcondie.thegamepensieveapi.controllers;

import com.sethhaskellcondie.thegamepensieveapi.TestFactory;
import com.sethhaskellcondie.thegamepensieveapi.domain.Keychain;
import com.sethhaskellcondie.thegamepensieveapi.domain.customfield.CustomFieldValue;
import com.sethhaskellcondie.thegamepensieveapi.domain.entity.system.SystemResponseDto;
import com.sethhaskellcondie.thegamepensieveapi.domain.entity.videogame.SlimVideoGame;
import com.sethhaskellcondie.thegamepensieveapi.domain.entity.videogame.VideoGameRequestDto;
import com.sethhaskellcondie.thegamepensieveapi.domain.entity.videogamebox.VideoGameBoxResponseDto;
import com.sethhaskellcondie.thegamepensieveapi.domain.filter.Filter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.ArrayList;
import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Video game boxes represent how the games appear in a collection, either on a shelf physically or in a launcher on a digital space.
 * Boxes can be physical, or digital, they can also be a collection of other games.
 * Because of this video games are created and deleted through the video game boxes endpoints.
 * Video game boxes have all the basic CRUD functions.
 * Video game boxes must have a title, system, and at least one video game.
 * This test suite will focus on the video game boxes, the video game functionality will be tested in the VideoGameTests suite.
 */

@SpringBootTest
@ActiveProfiles("test-container")
@AutoConfigureMockMvc
public class VideoGameBoxTests {

    @Autowired
    private MockMvc mockMvc;
    private TestFactory factory;
    private final String baseUrl = "/v1/videoGameBoxes";
    private final String baseUrlSlash = "/v1/videoGameBoxes/";


    @BeforeEach
    void setUp() {
        factory = new TestFactory(mockMvc);
    }

    @Test
    void postVideoGameBoxWithCustomFieldValues_ValidPayload_VideoGameBoxCreatedAndReturned() throws Exception {
        //test 1 - when valid post send, then 201 (created) returned
        final String expectedTitle = "Princess Peach Showtime";
        final SystemResponseDto relatedSystem = factory.postSystem();
        final VideoGameRequestDto relatedVideoGame = new VideoGameRequestDto(expectedTitle, relatedSystem.id(), new ArrayList<>());
        final List<SlimVideoGame> expectedVideoGameResults = List.of(convertToExpectedSlimVideoGameResponse(relatedVideoGame, relatedSystem));
        final boolean isPhysical = true;
        final boolean isCollection = false;
        final List<CustomFieldValue> expectedCustomFieldValues = List.of(
                new CustomFieldValue(0, "Includes Manual", "boolean", "true"),
                new CustomFieldValue(0, "Max Players", "number", "1"),
                new CustomFieldValue(0, "Developer", "text", "Nintendo")
        );

        final ResultActions result = factory.postVideoGameBoxReturnResult(expectedTitle, relatedSystem.id(), new ArrayList<>(), List.of(relatedVideoGame), isPhysical, expectedCustomFieldValues);

        factory.validateVideoGameBoxResponseBody(result, expectedTitle, relatedSystem, expectedVideoGameResults, isPhysical, isCollection, expectedCustomFieldValues);

        final VideoGameBoxResponseDto responseDto = factory.resultToVideoGameBoxResponseDto(result);


        //test 2 - when valid patch sent, then ok (200) returned
        List<CustomFieldValue> existingCustomFieldValues = responseDto.customFieldValues();

        final String updatedTitle = "Super Princess Peach";
        final SystemResponseDto newRelatedSystem = factory.postSystem();
        List<Integer> existingVideoGameIds = new ArrayList<>();
        for (SlimVideoGame existingVideoGame : responseDto.videoGames()) {
            existingVideoGameIds.add(existingVideoGame.id());
        }
        final boolean newPhysical = false;
        final boolean newCollection = false;
        final CustomFieldValue customFieldValueToUpdate = existingCustomFieldValues.get(0);
        existingCustomFieldValues.remove(0);
        final CustomFieldValue updatedValue = new CustomFieldValue(
                customFieldValueToUpdate.getCustomFieldId(),
                "Updated" + customFieldValueToUpdate.getCustomFieldName(),
                customFieldValueToUpdate.getCustomFieldType(),
                "false"
        );
        existingCustomFieldValues.add(updatedValue);

        final String jsonContent = factory.formatVideoGameBoxPayload(updatedTitle, newRelatedSystem.id(), existingVideoGameIds, new ArrayList<>(), newPhysical, existingCustomFieldValues);
        final ResultActions resultActions = mockMvc.perform(
                put(baseUrlSlash + responseDto.id())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonContent)
        );

        resultActions.andExpect(status().isOk());
        factory.validateVideoGameBoxResponseBody(resultActions, updatedTitle, newRelatedSystem, responseDto.videoGames(), newPhysical, newCollection, existingCustomFieldValues);
    }


    @Test
    void postVideoGameBox_TitleBlankInvalidSystemIdMissingVideoGames_ReturnBadRequest() throws Exception {
        // Two errors total:
        // 1) The title cannot be blank.
        // 2) The systemId must be a valid int greater than zero.
        final String jsonContent = factory.formatVideoGameBoxPayload("", -1, List.of(), List.of(), false, null);

        final ResultActions result = mockMvc.perform(
                post(baseUrl)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonContent)
        );

        result.andExpectAll(
                status().isBadRequest(),
                jsonPath("$.data").isEmpty(),
                jsonPath("$.errors.size()").value(2)
        );
    }


    @Test
    void postVideoGameBox_NoGamesPassedIn_ReturnBadRequest() throws Exception {
        // Error every box must have at least one video game
        final SystemResponseDto newRelatedSystem = factory.postSystem();
        final String jsonContent = factory.formatVideoGameBoxPayload("Valid Title", newRelatedSystem.id(), List.of(), List.of(), false, null);

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
    void postVideoGameBox_BoxSystemIdInvalid_ReturnBadRequest() throws Exception {
        //This test is a little different from the last one, in this one we are passing a valid int for the systemId
        //but there is not a matching system in the database for that id, so the error message will be different.
        final VideoGameRequestDto newVideoGame = new VideoGameRequestDto("title", Integer.MAX_VALUE, new ArrayList<>());
        final String jsonContent = factory.formatVideoGameBoxPayload("Valid Title", Integer.MAX_VALUE, List.of(), List.of(newVideoGame), false, null);

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
    void postVideoGameBox_VideoGameIdInvalid_ReturnNotFound() throws Exception {
        //This test is a little different from the last one, in this one we are passing in a valid int for the video game box systemId
        //but one of the video game ids that is not related to an existing video game.
        final SystemResponseDto existingSystem = factory.postSystem();
        final String jsonContent = factory.formatVideoGameBoxPayload("Valid Title", existingSystem.id(), List.of(Integer.MAX_VALUE), List.of(), false, null);

        final ResultActions result = mockMvc.perform(
                post(baseUrl)
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
    void getOneVideoGameBox_GameBoxExists_VideoGameBoxSerializedCorrectly() throws Exception {
        final String title = "Super Mario Bros. 3";
        final SystemResponseDto relatedSystem = factory.postSystem();
        final VideoGameRequestDto newVideoGame = new VideoGameRequestDto(title, relatedSystem.id(), new ArrayList<>());
        final boolean physical = true;
        final boolean collection = false;
        final List<SlimVideoGame> expectedVideoGames = List.of(convertToExpectedSlimVideoGameResponse(newVideoGame, relatedSystem));
        final List<CustomFieldValue> customFieldValues = List.of(new CustomFieldValue(0, "customFieldName", "text", "value"));
        ResultActions postResult = factory.postVideoGameBoxReturnResult(title, relatedSystem.id(), new ArrayList<>(), List.of(newVideoGame), physical, customFieldValues);
        final VideoGameBoxResponseDto expectedDto = factory.resultToVideoGameBoxResponseDto(postResult);

        final ResultActions result = mockMvc.perform(get(baseUrlSlash + expectedDto.id()));

        result.andExpectAll(
                status().isOk(),
                content().contentType(MediaType.APPLICATION_JSON)
        );
        factory.validateVideoGameBoxResponseBody(result, expectedDto.title(), relatedSystem, expectedVideoGames, physical, collection, customFieldValues);
    }

    @Test
    void getOneVideoGameBox_VideoGameBoxMissing_NotFoundReturned() throws Exception {
        final ResultActions result = mockMvc.perform(get(baseUrl + "/-1"));

        result.andExpectAll(
                status().isNotFound(),
                jsonPath("$.data").isEmpty(),
                jsonPath("$.errors.size()").value(1)
        );
    }

    @Test
    void getAllVideoGameBoxes_WithFilters_VideoGameBoxSubsetListReturned() throws Exception {
        //test 1 - when getting all video game boxes with a filter, only a subset of the video game boxes are returned
        final String customFieldName = "Custom";
        final String customFieldType = "number";
        final String customFieldKey = Keychain.VIDEO_GAME_BOX_KEY;
        final int customFieldId = factory.postCustomFieldReturnId(customFieldName, customFieldType, customFieldKey);

        final String title1 = "NES Mega Man";
        final SystemResponseDto relatedSystem1 = factory.postSystem();
        final VideoGameRequestDto relatedGame1 = new VideoGameRequestDto(title1, relatedSystem1.id(), new ArrayList<>());
        final List<CustomFieldValue> customFieldValues1 = List.of(new CustomFieldValue(customFieldId, customFieldName, customFieldType, "1"));
        final ResultActions result1 = factory.postVideoGameBoxReturnResult(title1, relatedSystem1.id(), List.of(), List.of(relatedGame1), false, customFieldValues1);
        final VideoGameBoxResponseDto gameBoxDto1 = factory.resultToVideoGameBoxResponseDto(result1);

        final String title2 = "NES Mega Man 2";
        final SystemResponseDto relatedSystem2 = factory.postSystem();
        final VideoGameRequestDto relatedGame2 = new VideoGameRequestDto(title2, relatedSystem2.id(), new ArrayList<>());
        final List<CustomFieldValue> customFieldValues2 = List.of(new CustomFieldValue(customFieldId, customFieldName, customFieldType, "2"));
        final ResultActions result2 = factory.postVideoGameBoxReturnResult(title2, relatedSystem2.id(), List.of(), List.of(relatedGame2), false, customFieldValues2);
        final VideoGameBoxResponseDto gameBoxDto2 = factory.resultToVideoGameBoxResponseDto(result2);

        final String title3 = "SNES Mega Man 7";
        final SystemResponseDto relatedSystem3 = factory.postSystem();
        final VideoGameRequestDto relatedGame3 = new VideoGameRequestDto(title3, relatedSystem3.id(), new ArrayList<>());
        final List<CustomFieldValue> customFieldValues3 = List.of(new CustomFieldValue(customFieldId, customFieldName, customFieldType, "3"));
        final ResultActions result3 = factory.postVideoGameBoxReturnResult(title3, relatedSystem3.id(), List.of(), List.of(relatedGame3), false, customFieldValues3);
        final VideoGameBoxResponseDto gameBoxDto3 = factory.resultToVideoGameBoxResponseDto(result3);

        final Filter filter = new Filter(Keychain.VIDEO_GAME_BOX_KEY, "text", "title", Filter.OPERATOR_STARTS_WITH, "NES ", false);
        final String formattedJson = factory.formatFiltersPayload(filter);

        final ResultActions result = mockMvc.perform(post(baseUrl + "/function/search")
                .contentType(MediaType.APPLICATION_JSON)
                .content(formattedJson)
        );

        result.andExpectAll(
                status().isOk(),
                content().contentType(MediaType.APPLICATION_JSON)
        );
        factory.validateVideoGameBoxResponseBody(result, List.of(gameBoxDto1, gameBoxDto2));

        final Filter customFilter = new Filter(customFieldKey, customFieldType, customFieldName, Filter.OPERATOR_GREATER_THAN, "2", true);


        //test 2 - when getting all video games boxes with a custom field filter, only a subset of the video game boxes are returned
        final String jsonContent = factory.formatFiltersPayload(customFilter);

        final ResultActions resultActions = mockMvc.perform(post(baseUrl + "/function/search")
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonContent)
        );

        resultActions.andExpectAll(
                status().isOk(),
                content().contentType(MediaType.APPLICATION_JSON)
        );
        factory.validateVideoGameBoxResponseBody(resultActions, List.of(gameBoxDto3));
    }

    @Test
    void getAllVideoGameBoxes_NoResultFilter_EmptyArrayReturned() throws Exception {
        final Filter filter = new Filter(Keychain.VIDEO_GAME_BOX_KEY, "text", "title", Filter.OPERATOR_STARTS_WITH, "NoResults", false);
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
    void updateExistingVideoGameBox_InvalidId_ReturnNotFound() throws Exception {
        final String jsonContent = factory.formatVideoGameBoxPayload("invalidId", 1, List.of(), List.of(), false, null);
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
    void putVideoGameBox_RemoveExistingGame_UpdateSuccessful() throws Exception {
        final SystemResponseDto system = factory.postSystem();
        final VideoGameRequestDto game1 = new VideoGameRequestDto("Game 1", system.id(), new ArrayList<>());
        final VideoGameRequestDto game2 = new VideoGameRequestDto("Game 2", system.id(), new ArrayList<>());

        final ResultActions createResult = factory.postVideoGameBoxReturnResult(
                "Test Box",
                system.id(),
                new ArrayList<>(),
                List.of(game1, game2),
                true,
                new ArrayList<>()
        );

        final VideoGameBoxResponseDto createdBox = factory.resultToVideoGameBoxResponseDto(createResult);

        final Integer gameIdToKeep = createdBox.videoGames().get(0).id();
        final SlimVideoGame expectedRemainingGame = createdBox.videoGames().get(0);
        final String expectedUpdatedTitle = "Updated Box Title";
        
        final String updatePayload = factory.formatVideoGameBoxPayload(
                expectedUpdatedTitle,
                system.id(),
                List.of(gameIdToKeep), // Only keep first game, remove second
                new ArrayList<>(),     // No new games
                true,
                List.of()
        );

        final ResultActions result = mockMvc.perform(
                put(baseUrlSlash + createdBox.id())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updatePayload)
        );

        result.andExpect(status().isOk());
        
        factory.validateVideoGameBoxResponseBody(
                result,
                expectedUpdatedTitle,
                system,
                List.of(expectedRemainingGame), // Only one game should remain
                true,
                false,
                List.of()
        );
    }

    @Test
    void updateExistingVideoGameBox_NoRelatedGames_ReturnBadRequest() throws Exception {
        VideoGameBoxResponseDto responseDto = factory.postVideoGameBox();
        final String jsonContent = factory.formatVideoGameBoxPayload(responseDto.title(), responseDto.system().id(), List.of(), List.of(), false, new ArrayList<>());
        final ResultActions result = mockMvc.perform(
                put(baseUrlSlash + responseDto.id())
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
    void deleteExistingVideoGameBox_VideoGameBoxExists_ReturnNoContent() throws Exception {
        final VideoGameBoxResponseDto responseDto = factory.postVideoGameBox();

        final ResultActions result = mockMvc.perform(
                delete(baseUrl + "/" + responseDto.id())
        );

        result.andExpectAll(
                status().isNoContent(),
                jsonPath("$.data").isEmpty(),
                jsonPath("$.errors").isEmpty()
        );
    }

    @Test
    void deleteExistingVideoGameBox_InvalidId_ReturnNotFound() throws Exception {
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

    private SlimVideoGame convertToExpectedSlimVideoGameResponse(VideoGameRequestDto requestDto, SystemResponseDto expectedSystem) {
        return new SlimVideoGame(0, requestDto.title(), expectedSystem, null, null, null, requestDto.customFieldValues());
    }
}

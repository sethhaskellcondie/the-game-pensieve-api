package com.sethhaskellcondie.thegamepensiveapi.api.controllers;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sethhaskellcondie.thegamepensiveapi.TestFactory;
import com.sethhaskellcondie.thegamepensiveapi.domain.Keychain;
import com.sethhaskellcondie.thegamepensiveapi.domain.customfield.CustomFieldValue;
import com.sethhaskellcondie.thegamepensiveapi.domain.entity.system.SystemResponseDto;
import com.sethhaskellcondie.thegamepensiveapi.domain.entity.videogame.VideoGameResponseDto;
import com.sethhaskellcondie.thegamepensiveapi.domain.entity.videogamebox.VideoGameBoxResponseDto;
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
        final String expectedTitle = "Mega Man Collection";
        final SystemResponseDto relatedSystem = factory.postSystem();
        final VideoGameResponseDto relatedVideoGame1 = factory.postVideoGame();
        final VideoGameResponseDto relatedVideoGame2 = factory.postVideoGame();
        final boolean isPhysical = false;
        final boolean isCollection = true;
        final List<CustomFieldValue> expectedCustomFieldValues = List.of(
                new CustomFieldValue(0, "Owned Box", "boolean", "true"),
                new CustomFieldValue(0, "Max Players", "number", "1"),
                new CustomFieldValue(0, "Developer", "text", "Capcom")
        );

        final ResultActions result = factory.postVideoGameBoxReturnResult(expectedTitle, relatedSystem.id(), List.of(relatedVideoGame1.id(), relatedVideoGame2.id()),
                isPhysical, isCollection, expectedCustomFieldValues);

        validateVideoGameBoxResponseBody(result, expectedTitle, relatedSystem.id(), relatedSystem.name(), List.of(relatedVideoGame1.id(), relatedVideoGame2.id()),
                List.of(relatedVideoGame1, relatedVideoGame2), isPhysical, isCollection, expectedCustomFieldValues);

        final VideoGameBoxResponseDto responseDto = factory.resultToVideoGameBoxResponseDto(result);
        updateExistingVideoGameBox_UpdateVideoGameBoxAndCustomFieldValue_ReturnOk(responseDto, responseDto.customFieldValues());
    }

    void updateExistingVideoGameBox_UpdateVideoGameBoxAndCustomFieldValue_ReturnOk(VideoGameBoxResponseDto existingVideoGameBox, List<CustomFieldValue> existingCustomFieldValue) throws Exception {
        final String updatedTitle = "Mega Man Not A Collection";
        final SystemResponseDto newRelatedSystem = factory.postSystem();
        final VideoGameResponseDto newRelatedVideoGame1 = factory.postVideoGame();
        final VideoGameResponseDto newRelatedVideoGame2 = factory.postVideoGame();
        final boolean newPhysical = true;
        final boolean newCollection = false;
        final CustomFieldValue customFieldValueToUpdate = existingCustomFieldValue.get(0);
        existingCustomFieldValue.remove(0);
        final CustomFieldValue updatedValue = new CustomFieldValue(
                customFieldValueToUpdate.getCustomFieldId(),
                "Updated" + customFieldValueToUpdate.getCustomFieldName(),
                customFieldValueToUpdate.getCustomFieldType(),
                "false"
        );
        existingCustomFieldValue.add(updatedValue);
        //TODO refactor the custom fields validation to not test the order so that lines like this are not needed.
        //TODO there is an off by one error that is present in the test that will need to be worked out.
//        final List<CustomFieldValue> orderedCustomFieldValue = List.of(existingCustomFieldValue.get(2), existingCustomFieldValue.get(0), existingCustomFieldValue.get(1));
//
//        final String jsonContent = factory.formatVideoGameBoxPayload(updatedTitle, newRelatedSystem.id(), List.of(newRelatedVideoGame1.id(), newRelatedVideoGame2.id()),
//                newPhysical, newCollection, existingCustomFieldValue);
//        final ResultActions result = mockMvc.perform(
//                put(baseUrlSlash + existingVideoGameBox.id())
//                        .contentType(MediaType.APPLICATION_JSON)
//                        .content(jsonContent)
//        );
//
//        result.andExpect(status().isOk());
//        validateVideoGameBoxResponseBody(result, updatedTitle, newRelatedSystem.id(), newRelatedSystem.name(), List.of(newRelatedVideoGame1.id(), newRelatedVideoGame2.id()),
//                List.of(newRelatedVideoGame1, newRelatedVideoGame2), newPhysical, newCollection, orderedCustomFieldValue);
    }

    //TODO create and implement a test that when a video game box with no video game id is passed in a video game with the same title will be created

    @Test
    void postVideoGameBox_TitleBlankInvalidSystemIdMissingVideoGames_ReturnBadRequest() throws Exception {
        //Three errors the title cannot be blank
        //the systemId must be a valid int
        //the video game id list cannot be blank (will return 2 errors)
        final String jsonContent = factory.formatVideoGameBoxPayload("", -1, List.of(), false, false, null);

        final ResultActions result = mockMvc.perform(
                post(baseUrl)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonContent)
        );

        result.andExpectAll(
                status().isBadRequest(),
                jsonPath("$.data").isEmpty(),
                jsonPath("$.errors.size()").value(4)
        );
    }

    @Test
    void postVideoGameBox_SystemIdInvalid_ReturnBadRequest() throws Exception {
        //This test is a little different from the last one, in this one we are passing in a valid int for the systemId
        //but there is not a matching system in the database for that id, so the error message will be different.
        final VideoGameResponseDto existingGame = factory.postVideoGame();
        final String jsonContent = factory.formatVideoGameBoxPayload("Valid Title", Integer.MAX_VALUE, List.of(existingGame.id()), false, false, null);

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
    void postVideoGameBox_VideoGameIdInvalid_ReturnBadRequest() throws Exception {
        //This test is a little different from the last one, in this one we are passing in a valid int for the systemId
        //but there is not a matching system in the database for that id, so the error message will be different.
        final SystemResponseDto existingSystem = factory.postSystem();
        final String jsonContent = factory.formatVideoGameBoxPayload("Valid Title", existingSystem.id(), List.of(Integer.MAX_VALUE), false, false, null);

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
    void getOneVideoGameBox_GameBoxExists_VideoGameBoxSerializedCorrectly() throws Exception {
        final String title = "Super Mario Bros. 3";
        final SystemResponseDto relatedSystem = factory.postSystem();
        final VideoGameResponseDto existingGame = factory.postVideoGame();
        final boolean physical = true;
        final boolean collection = false;
        final List<CustomFieldValue> customFieldValues = List.of(new CustomFieldValue(0, "customFieldName", "text", "value"));
        ResultActions postResult = factory.postVideoGameBoxReturnResult(title, relatedSystem.id(), List.of(existingGame.id()), physical, collection, customFieldValues);
        final VideoGameBoxResponseDto expectedDto = factory.resultToVideoGameBoxResponseDto(postResult);

        final ResultActions result = mockMvc.perform(get(baseUrlSlash + expectedDto.id()));

        result.andExpectAll(
                status().isOk(),
                content().contentType(MediaType.APPLICATION_JSON)
        );
        validateVideoGameBoxResponseBody(result, expectedDto.title(), expectedDto.systemId(), expectedDto.systemName(), expectedDto.videoGameIds(), expectedDto.videoGames(),
                expectedDto.isPhysical(), expectedDto.isCollection(), customFieldValues);
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
        final String customFieldKey = Keychain.VIDEO_GAME_BOX_KEY;
        final int customFieldId = factory.postCustomFieldReturnId(customFieldName, customFieldType, customFieldKey);

        final String title1 = "NES Mega Man";
        final SystemResponseDto relatedSystem1 = factory.postSystem();
        final VideoGameResponseDto relatedGame1 = factory.postVideoGame();
        final List<CustomFieldValue> CustomFieldValues1 = List.of(new CustomFieldValue(customFieldId, customFieldName, customFieldType, "1"));
        final ResultActions result1 = factory.postVideoGameBoxReturnResult(title1, relatedSystem1.id(), List.of(relatedGame1.id()), false, false, CustomFieldValues1);
        final VideoGameBoxResponseDto gameBoxDto1 = factory.resultToVideoGameBoxResponseDto(result1);

        final String title2 = "NES Mega Man 2";
        final SystemResponseDto relatedSystem2 = factory.postSystem();
        final VideoGameResponseDto relatedGame2 = factory.postVideoGame();
        final List<CustomFieldValue> CustomFieldValues2 = List.of(new CustomFieldValue(customFieldId, customFieldName, customFieldType, "2"));
        final ResultActions result2 = factory.postVideoGameBoxReturnResult(title2, relatedSystem2.id(), List.of(relatedGame2.id()), false, false, CustomFieldValues2);
        final VideoGameBoxResponseDto gameBoxDto2 = factory.resultToVideoGameBoxResponseDto(result2);

        final String title3 = "SNES Mega Man 7";
        final SystemResponseDto relatedSystem3 = factory.postSystem();
        final VideoGameResponseDto relatedGame3 = factory.postVideoGame();
        final List<CustomFieldValue> CustomFieldValues3 = List.of(new CustomFieldValue(customFieldId, customFieldName, customFieldType, "3"));
        final ResultActions result3 = factory.postVideoGameBoxReturnResult(title3, relatedSystem3.id(), List.of(relatedGame3.id()), false, false, CustomFieldValues3);
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
        validateVideoGameBoxResponseBody(result, List.of(gameBoxDto1, gameBoxDto2));

        final Filter customFilter = new Filter(customFieldKey, customFieldType, customFieldName, Filter.OPERATOR_GREATER_THAN, "2", true);
        getWithFilters_GreaterThanCustomFilter_VideoGameBoxListReturned(customFilter, List.of(gameBoxDto3));
    }

    void getWithFilters_GreaterThanCustomFilter_VideoGameBoxListReturned(Filter filter, List<VideoGameBoxResponseDto> expectedGames) throws Exception {

        final String jsonContent = factory.formatFiltersPayload(filter);

        final ResultActions result = mockMvc.perform(post(baseUrl + "/function/search")
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonContent)
        );

        result.andExpectAll(
                status().isOk(),
                content().contentType(MediaType.APPLICATION_JSON)
        );
        validateVideoGameBoxResponseBody(result, expectedGames);
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
    void deleteExistingVideoGameBox_GameBoxExists_ReturnNoContent() throws Exception {
        VideoGameBoxResponseDto existingVideoGameBox = factory.postVideoGameBox();

        final ResultActions result = mockMvc.perform(
                delete(baseUrlSlash + existingVideoGameBox.id())
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

    private void validateVideoGameBoxResponseBody(ResultActions result, String expectedTitle, int expectedSystemId, String expectedSystemName, List<Integer> expectedGameIds,
                                                  List<VideoGameResponseDto> expectedVideoGames, boolean expectedPhysical, boolean expectedCollection,
                                                  List<CustomFieldValue> customFieldValues) throws Exception {
        result.andExpectAll(
                jsonPath("$.data.key").value(Keychain.VIDEO_GAME_BOX_KEY),
                jsonPath("$.data.id").isNotEmpty(),
                jsonPath("$.data.title").value(expectedTitle),
                jsonPath("$.data.systemId").value(expectedSystemId),
                jsonPath("$.data.systemName").value(expectedSystemName),
                jsonPath("$.data.videoGames.size()").value(expectedVideoGames.size()),
                jsonPath("$.data.isPhysical").value(expectedPhysical),
                jsonPath("$.data.isCollection").value(expectedCollection),
                jsonPath("$.errors").isEmpty()
        );
        for (int i = 0; i < expectedVideoGames.size(); i++) {
            VideoGameResponseDto expectedGameDto = expectedVideoGames.get(i);
            result.andExpectAll(
                    jsonPath("$.data.videoGames[" + i + "].key").value(expectedGameDto.key()),
                    jsonPath("$.data.videoGames[" + i + "].id").value(expectedGameDto.id()),
                    jsonPath("$.data.videoGames[" + i + "].title").value(expectedGameDto.title()),
                    jsonPath("$.data.videoGames[" + i + "].systemId").value(expectedGameDto.systemId())
//                    jsonPath("$.data.videoGames[" + i + "].systemName").value(expectedGameDto.systemName())
            );
            //Note: the custom fields on the video games are not being tested
        }
        VideoGameBoxResponseDto responseDto = factory.resultToVideoGameBoxResponseDto(result);
        factory.validateCustomFieldValues(responseDto.customFieldValues(), customFieldValues);
    }

    private void validateVideoGameBoxResponseBody(ResultActions result, List<VideoGameBoxResponseDto> expectedGameBoxes) throws Exception {
        result.andExpectAll(
                jsonPath("$.data").exists(),
                jsonPath("$.errors").isEmpty()
        );

        final MvcResult mvcResult = result.andReturn();
        final String responseString = mvcResult.getResponse().getContentAsString();
        final Map<String, List<VideoGameBoxResponseDto>> body = new ObjectMapper().readValue(responseString, new TypeReference<>() {
        });
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
                    () -> assertEquals(expectedGameBox.systemId(), returnedGameBox.systemId()),
                    () -> assertEquals(expectedGameBox.systemName(), returnedGameBox.systemName()),
                    () -> assertEquals(expectedGameBox.videoGameIds().size(), returnedGameBox.videoGameIds().size()),
                    () -> assertEquals(expectedGameBox.isPhysical(), returnedGameBox.isPhysical()),
                    () -> assertEquals(expectedGameBox.isCollection(), returnedGameBox.isCollection())
            );
            //Note: we are not testing the video games on this one
            factory.validateCustomFieldValues(expectedGameBox.customFieldValues(), returnedGameBox.customFieldValues());
        }
    }
}

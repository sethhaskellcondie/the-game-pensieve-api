package com.sethhaskellcondie.thegamepensiveapi.domain.system;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sethhaskellcondie.thegamepensiveapi.domain.TestFactory;
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

import java.io.UnsupportedEncodingException;
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
public class SystemTests {

    @Autowired
    private MockMvc mockMvc;
    private TestFactory factory;

    @BeforeEach
    void setUp() {
        factory = new TestFactory(mockMvc);
    }

    @Test
    void postSystem_ValidPayload_SystemCreatedAndReturned() throws Exception {
        final String expectedName = "NES 2";
        final int expectedGeneration = 3;
        final boolean expectedHandheld = false;

        final ResultActions result = factory.postCustomSystem(expectedName, expectedGeneration, expectedHandheld);

        result.andExpect(status().isCreated());
        validateSystemResponseBody(result, expectedName, expectedGeneration, expectedHandheld);
    }

    @Test
    void postSystem_FailedValidation_ReturnArrayOfErrors() throws Exception {
        final String jsonContent = factory.formatSystemPayload("", -1, null);

        final ResultActions result = mockMvc.perform(
                post("/systems")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonContent)
        );

        result.andExpectAll(
                status().isBadRequest(),
                jsonPath("$.data").isEmpty(),
                jsonPath("$.errors").isArray()
        );
    }

    @Test
    void postSystem_SystemNameDuplicate_ReturnBadRequest() throws Exception {
        final String duplicateName = "Game Boy Pocket";
        final int generation = 3;
        final boolean handheld = true;

        factory.postCustomSystem(duplicateName, generation, handheld);
        final String formattedJson = factory.formatSystemPayload(duplicateName, generation, handheld);
        final ResultActions result = mockMvc.perform(
                post("/systems")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(formattedJson)
        );

        result.andExpectAll(
                status().isBadRequest(),
                jsonPath("$.data").isEmpty(),
                jsonPath("$.errors").isNotEmpty()
        );
    }

    @Test
    void getOneSystem_SystemExists_SystemSerializedCorrectly() throws Exception {
        final String name = "Genesis 2";
        final int generation = 4;
        final boolean handheld = true;
        final ResultActions postResult = factory.postCustomSystem(name, generation, handheld);
        final SystemResponseDto expectedDto = resultToResponseDto(postResult);

        final ResultActions result = mockMvc.perform(get("/systems/" + expectedDto.id()));

        result.andExpectAll(
                status().isOk(),
                content().contentType(MediaType.APPLICATION_JSON)
        );
        validateSystemResponseBody(result, expectedDto);
    }

    @Test
    void getOneSystem_SystemMissing_NotFoundReturned() throws Exception {
        final ResultActions result = mockMvc.perform(get("/systems/-1"));

        result.andExpectAll(
                status().isNotFound(),
                jsonPath("$.data").isEmpty(),
                jsonPath("$.errors").isNotEmpty()
        );
    }

    //TODO return to this after get with filters has been implemented (it works but not in sequence with the other tests)
    void getAllSystems_TwoSystemPresent_TwoSystemsReturnedInArray() throws Exception {
        final String name1 = "Super Nintendo";
        final int generation1 = 4;
        final boolean handheld1 = false;
        final ResultActions result1 = factory.postCustomSystem(name1, generation1, handheld1);
        final SystemResponseDto responseDto1 = resultToResponseDto(result1);

        final String name2 = "Sony Playstation";
        final int generation2 = 4;
        final boolean handheld2 = false;
        final ResultActions result2 = factory.postCustomSystem(name2, generation2, handheld2);
        final SystemResponseDto responseDto2 = resultToResponseDto(result2);

        final ResultActions result = mockMvc.perform(post("/systems/search"));

        result.andExpectAll(
                status().isOk(),
                content().contentType(MediaType.APPLICATION_JSON)
        );
        validateSystemResponseBody(result, List.of(responseDto1, responseDto2));
    }

    //TODO return to this after get with filters has been implemented (it works but not in sequence with the other tests)
    void getAllSystems_NoSystemsPresent_EmptyArrayReturned() throws Exception {

        final ResultActions result = mockMvc.perform(post("/systems/search"));

        result.andExpectAll(
                status().isOk(),
                content().contentType(MediaType.APPLICATION_JSON),
                jsonPath("$.data").value(new ArrayList<>()),
                jsonPath("$.errors").isEmpty()
        );
    }

    @Test
    void updateExistingSystem_ValidUpdate_ReturnOk() throws Exception {
        final ResultActions existingResult = factory.postSystem();
        final SystemResponseDto responseDto = resultToResponseDto(existingResult);

        final String newName = "Playstation 2 Slim";
        final int newGeneration = 6;
        final boolean newBoolean = false;

        final String jsonContent = factory.formatSystemPayload(newName, newGeneration, newBoolean);
        final ResultActions result = mockMvc.perform(
                put("/systems/" + responseDto.id())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonContent)
        );

        result.andExpect(status().isOk());
        validateSystemResponseBody(result, resultToResponseDto(result));
    }

    @Test
    void updateExistingSystem_InvalidId_ReturnNotFound() throws Exception {
        final String jsonContent = factory.formatSystemPayload("ValidButMissing", 3, false);
        final ResultActions result = mockMvc.perform(
                put("/systems/-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonContent)
        );

        result.andExpectAll(
                status().isNotFound(),
                jsonPath("$.data").isEmpty(),
                jsonPath("$.errors").isNotEmpty()
        );
    }

    @Test
    void deleteExistingSystem_SystemExists_ReturnNoContent() throws Exception {
        final ResultActions existingResult = factory.postSystem();
        final SystemResponseDto responseDto = resultToResponseDto(existingResult);

        final ResultActions result = mockMvc.perform(
                delete("/systems/" + responseDto.id())
        );

        result.andExpectAll(
                status().isNoContent(),
                jsonPath("$.data").isEmpty(),
                jsonPath("$.errors").isEmpty()
        );
    }

    @Test
    void deleteExistingSystem_InvalidId_ReturnNotFound() throws Exception {
        final ResultActions result = mockMvc.perform(
                delete("/systems/-1")
        );

        result.andExpectAll(
                status().isNotFound(),
                jsonPath("$.data").isEmpty(),
                jsonPath("$.errors").isNotEmpty()
        );
    }

    private SystemResponseDto resultToResponseDto(ResultActions result) throws UnsupportedEncodingException, JsonProcessingException {
        final MvcResult mvcResult = result.andReturn();
        final String responseString = mvcResult.getResponse().getContentAsString();
        final Map<String, SystemResponseDto> body = new ObjectMapper().readValue(responseString, new TypeReference<>() { });
        return body.get("data");
    }

    private void validateSystemResponseBody(ResultActions result, String expectedName, int expectedGeneration, boolean expectedHandheld) throws Exception {
        result.andExpectAll(
                jsonPath("$.data.type").value("system"),
                jsonPath("$.data.id").isNotEmpty(),
                jsonPath("$.data.name").value(expectedName),
                jsonPath("$.data.generation").value(expectedGeneration),
                jsonPath("$.data.handheld").value(expectedHandheld),
                jsonPath("$.errors").isEmpty()
        );
    }

    private void validateSystemResponseBody(ResultActions result, SystemResponseDto responseDto) throws Exception {
        result.andExpectAll(
                jsonPath("$.data.type").value("system"),
                jsonPath("$.data.id").value(responseDto.id()),
                jsonPath("$.data.name").value(responseDto.name()),
                jsonPath("$.data.generation").value(responseDto.generation()),
                jsonPath("$.data.handheld").value(responseDto.handheld()),
                jsonPath("$.errors").isEmpty()
        );
    }

    private void validateSystemResponseBody(ResultActions result, List<SystemResponseDto> expectedSystems) throws Exception {
        result.andExpectAll(
                jsonPath("$.data").exists(),
                jsonPath("$.errors").isEmpty()
        );

        final MvcResult mvcResult = result.andReturn();
        final String responseString = mvcResult.getResponse().getContentAsString();
        final Map<String, List<SystemResponseDto>> body = new ObjectMapper().readValue(responseString, new TypeReference<>() { });
        final List<SystemResponseDto> returnedSystems = body.get("data");
        //testing order as well as each member being deserialized correctly
        for (int i = 0; i < returnedSystems.size(); i++) {
            SystemResponseDto expectedSystem = expectedSystems.get(i);
            SystemResponseDto returnedSystem = returnedSystems.get(i);
            assertAll(
                    "The response body is not formatted correctly",
                    () -> assertEquals("system", returnedSystem.type()),
                    () -> assertEquals(expectedSystem.id(), returnedSystem.id()),
                    () -> assertEquals(expectedSystem.name(), returnedSystem.name()),
                    () -> assertEquals(expectedSystem.generation(), returnedSystem.generation()),
                    () -> assertEquals(expectedSystem.handheld(), returnedSystem.handheld())
            );
        }
    }

}

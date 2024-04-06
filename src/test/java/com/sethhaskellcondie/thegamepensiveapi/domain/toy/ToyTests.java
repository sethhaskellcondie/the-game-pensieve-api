package com.sethhaskellcondie.thegamepensiveapi.domain.toy;

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
public class ToyTests {

    @Autowired
    private MockMvc mockMvc;
    private TestFactory factory;

    @BeforeEach
    void setUp() {
        factory = new TestFactory(mockMvc);
    }

    @Test
    void postToy_ValidPayload_ToyCreatedAndReturned() throws Exception {
        final String expectedName = "Sora";
        final String expectedSet = "Disney Infinity";

        ResultActions result = factory.postCustomToy(expectedName, expectedSet);

        result.andExpect(status().isCreated());
        validateToyResponseBody(result, expectedName, expectedSet);
    }

    @Test
    void postNewToy_NameBlank_ReturnBadRequest() throws Exception {
        final String jsonContent = factory.formatToyPayload("", "set");

        final ResultActions result = mockMvc.perform(
                post("/toys")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonContent)
        );

        result.andExpectAll(
                status().isBadRequest(),
                jsonPath("$.data").isEmpty(),
                jsonPath("$.errors").isNotEmpty()
        );
    }

    @Test
    void getOneToy_ToyExists_ToySerializedCorrectly() throws Exception {
        final String name = "Mario";
        final String set = "Amiibo";
        ResultActions postResult = factory.postCustomToy(name, set);
        final ToyResponseDto expectedDto = resultToResponseDto(postResult);

        final ResultActions result = mockMvc.perform(get("/toys/" + expectedDto.id()));

        result.andExpectAll(
                status().isOk(),
                content().contentType(MediaType.APPLICATION_JSON)
        );
        validateToyResponseBody(result, expectedDto);
    }

    @Test
    void getOneToy_ToyMissing_NotFoundReturned() throws Exception {
        final ResultActions result = mockMvc.perform(get("/toys/-1"));

        result.andExpectAll(
                status().isNotFound(),
                jsonPath("$.data").isEmpty(),
                jsonPath("$.errors").isNotEmpty()
        );
    }

    //TODO return to this after get with filters has been implemented (it works but not in sequence with the other tests)
    void getAllToys_TwoToysPresent_TwoToysReturnedInArray() throws Exception {
//        final String name1 = "MegaMan";
//        final String set1 = "Amiibo";
//        ResultActions result1 = factory.postCustomToy(name1, set1);
//        final ToyResponseDto toyDto1 = resultToResponseDto(result1);
//
//        final String name2 = "Goofy";
//        final String set2 = "Disney Infinity";
//        ResultActions result2 = factory.postCustomToy(name2, set2);
//        final ToyResponseDto toyDto2 = resultToResponseDto(result2);
//
//        final ResultActions result = mockMvc.perform(post("/toys/search"));
//
//        result.andExpectAll(
//                status().isOk(),
//                content().contentType(MediaType.APPLICATION_JSON)
//        );
//        validateToyResponseBody(result, List.of(toyDto1, toyDto2));
    }

    //TODO return to this after get with filters has been implemented (it works but not in sequence with the other tests)
    void getAllToys_NoToysPresent_EmptyArrayReturned() throws Exception {
//        final ResultActions result = mockMvc.perform(post("/toys/search"));
//
//        result.andExpectAll(
//                status().isOk(),
//                content().contentType(MediaType.APPLICATION_JSON),
//                jsonPath("$.data").value(new ArrayList<>()),
//                jsonPath("$.errors").isEmpty()
//        );
    }

    @Test
    void putExistingToy_ValidUpdate_ReturnOk() throws Exception {
        final String name = "Donald Duck";
        final String set = "Disney Infinity";
        ResultActions postResult = factory.postCustomToy(name, set);
        final ToyResponseDto expectedDto = resultToResponseDto(postResult);

        final String newName = "Pit";
        final String newSet = "Amiibo";

        final String jsonContent = factory.formatToyPayload(newName, newSet);
        final ResultActions result = mockMvc.perform(
                put("/toys/" + expectedDto.id())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonContent)
        );

        result.andExpect(status().isOk());
        validateToyResponseBody(result, newName, newSet);
    }

    @Test
    void updateExistingToy_InvalidId_ReturnNotFound() throws Exception {

        final String jsonContent = factory.formatToyPayload("invalidId", "");
        final ResultActions result = mockMvc.perform(
                put("/toys/-1")
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
    void deleteExistingToy_ToyExists_ReturnNoContent() throws Exception {
        ResultActions postResult = factory.postToy();
        final ToyResponseDto expectedDto = resultToResponseDto(postResult);

        final ResultActions result = mockMvc.perform(
                delete("/toys/" + expectedDto.id())
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
                delete("/toys/-1")
        );

        result.andExpectAll(
                status().isNotFound(),
                jsonPath("$.data").isEmpty(),
                jsonPath("$.errors").isNotEmpty()
        );
    }

    private void validateToyResponseBody(ResultActions result, String expectedName, String expectedSet) throws Exception {
        result.andExpectAll(
                jsonPath("$.data.type").value("toy"),
                jsonPath("$.data.id").isNotEmpty(),
                jsonPath("$.data.name").value(expectedName),
                jsonPath("$.data.set").value(expectedSet)
        );
    }

    private ToyResponseDto resultToResponseDto(ResultActions result) throws UnsupportedEncodingException, JsonProcessingException {
        final MvcResult mvcResult = result.andReturn();
        final String responseString = mvcResult.getResponse().getContentAsString();
        final Map<String, ToyResponseDto> body = new ObjectMapper().readValue(responseString, new TypeReference<>() { });
        return body.get("data");
    }

    private void validateToyResponseBody(ResultActions result, ToyResponseDto expectedResponse) throws Exception {
        result.andExpectAll(
                jsonPath("$.data.type").value("toy"),
                jsonPath("$.data.id").value(expectedResponse.id()),
                jsonPath("$.data.name").value(expectedResponse.name()),
                jsonPath("$.data.set").value(expectedResponse.set())
        );
    }

    private void validateToyResponseBody(ResultActions result, List<ToyResponseDto> expectedToys) throws Exception {
        result.andExpectAll(
                jsonPath("$.data").exists(),
                jsonPath("$.errors").isEmpty()
        );

        final MvcResult mvcResult = result.andReturn();
        final String responseString = mvcResult.getResponse().getContentAsString();
        final Map<String, List<ToyResponseDto>> body = new ObjectMapper().readValue(responseString, new TypeReference<>() { });
        final List<ToyResponseDto> returnedToys = body.get("data");
        //test the order, and the deserialization
        for (int i = 0; i < returnedToys.size(); i++) {
            ToyResponseDto expectedToy = expectedToys.get(i);
            ToyResponseDto returnedToy = returnedToys.get(i);
            assertAll(
                    "The response body for Toys is not formatted correctly",
                    () -> assertEquals("toy", returnedToy.type()),
                    () -> assertEquals(expectedToy.id(), returnedToy.id()),
                    () -> assertEquals(expectedToy.name(), returnedToy.name()),
                    () -> assertEquals(expectedToy.set(), returnedToy.set())
            );
        }
    }
}

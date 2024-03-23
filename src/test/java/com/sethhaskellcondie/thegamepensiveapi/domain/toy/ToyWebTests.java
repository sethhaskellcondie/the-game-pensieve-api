package com.sethhaskellcondie.thegamepensiveapi.domain.toy;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sethhaskellcondie.thegamepensiveapi.exceptions.ExceptionResourceNotFound;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ToyController.class)
public class ToyWebTests {
    @TestConfiguration
    static class ToyWebTestsConfiguration {
        @Bean
        public ToyGateway toyGateway(ToyServiceAbstract service) {
            return new ToyGatewayAbstract(service);
        }
    }

    @Autowired
    private MockMvc mockMvc;
    @MockBean
    private ToyServiceAbstract service;

    @Test
    void getOneToy_ToyExists_ToySerializedCorrectly() throws Exception {
        Toy toy = new Toy(1, "Donkey Kong", "Amiibo");
        when(service.getById(1)).thenReturn(toy);

        ResultActions result = mockMvc.perform(get("/toys/1"));

        result.andDo(print());
        result.andExpectAll(
                status().isOk(),
                content().contentType(MediaType.APPLICATION_JSON)
        );
        validateToyResponseBody(result, toy);
    }

    @Test
    void getOneToy_ToyMissing_NotFoundReturned() throws Exception {
        when(service.getById(999)).thenThrow(ExceptionResourceNotFound.class);

        ResultActions result = mockMvc.perform(get("/toys/999"));

        result.andExpectAll(
                status().isNotFound(),
                jsonPath("$.message").hasJsonPath(), //the mock can't throw a message, just check for the path
                jsonPath("$.status").exists()
        );
    }

    @Test
    void getAllToys_TwoToysPresent_TwoToysReturnedInArray() throws Exception {
        Toy toy1 = new Toy(1, "Mega Man", "Amiibo");
        Toy toy2 = new Toy(2, "Samus", "Amiibo");
        List<Toy> toys = List.of(toy1, toy2);
        when(service.getWithFilters("")).thenReturn(toys);

        ResultActions result = mockMvc.perform(get("/toys"));

        result.andExpectAll(
                status().isOk(),
                content().contentType(MediaType.APPLICATION_JSON)
        );
        validateToyResponseBody(result, toys);
    }

    @Test
    void getAllToys_NoToysPresent_EmptyArrayReturned() throws Exception {
        when(service.getWithFilters("")).thenReturn(List.of());

        ResultActions result = mockMvc.perform(get("/toys"));

        result.andExpectAll(
                status().isOk(),
                content().contentType(MediaType.APPLICATION_JSON),
                jsonPath("$").value(new ArrayList<>())
        );
    }

    @Test
    void createNewToy_ValidPayload_ToyCreatedAndReturned() throws Exception {
        int expectedId = 34;
        String expectedName = "Sora";
        String expectedSet = "Disney Infinity";

        Toy expectedToy = new Toy(expectedId, expectedName, expectedSet);

        when(service.createNew(any())).thenReturn(expectedToy);

        String jsonContent = generateValidCreateUpdatePayload(expectedName, expectedSet);
        ResultActions result = mockMvc.perform(
                post("/toys")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonContent)
        );

        result.andExpect(status().isCreated());
        validateToyResponseBody(result, expectedToy);
    }

    @Test
    void createNewToy_NameBlank_ReturnBadRequest() throws Exception {
        String jsonContent = generateValidCreateUpdatePayload("", "set");

        ResultActions result = mockMvc.perform(
                post("/toys")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonContent)
        );

        result.andExpectAll(
                status().isBadRequest(),
                jsonPath("$.message").exists(),
                jsonPath("$.status").exists()
        );
    }

    @Test
    void updateExistingToy_ValidUpdate_ReturnOk() throws Exception {
        int expectedId = 99;
        String expectedName = "Baloo";
        String expectedSet = "Disney Infinity";

        Toy existingToy = new Toy(expectedId, "Old Name", "Old Set");
        Toy updatedToy = new Toy(expectedId, expectedName, expectedSet);

        when(service.getById(expectedId)).thenReturn(existingToy);
        when(service.updateExisting(updatedToy)).thenReturn(updatedToy);

        String jsonContent = generateValidCreateUpdatePayload(expectedName, expectedSet);
        ResultActions result = mockMvc.perform(
                put("/toys/" + expectedId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonContent)
        );

        result.andExpect(status().isOk());
        validateToyResponseBody(result, updatedToy);
    }

    @Test
    void updateExistingToy_InvalidId_ReturnNotFound() throws Exception {
        when(service.getById(79)).thenReturn(new Toy());

        String jsonContent = generateValidCreateUpdatePayload("invalidId", "");
        ResultActions result = mockMvc.perform(
                put("/toys/79")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonContent)
        );

        result.andExpect(status().isNotFound());
    }

    @Test
    void deleteExistingToy_ToyExists_ReturnNoContent() throws Exception {
        when(service.getById(27)).thenReturn(new Toy(27, "MarkedForDeletion", "Set"));

        ResultActions result = mockMvc.perform(
                delete("/toys/27")
        );

        result.andExpect(status().isNoContent());
    }

    @Test
    void deleteExistingToy_InvalidId_ReturnNotFound() throws Exception {

        when(service.getById(27)).thenReturn(new Toy());

        ResultActions result = mockMvc.perform(
                delete("/toys/27")
        );

        result.andExpect(status().isNotFound());
    }

    private String generateValidCreateUpdatePayload(String name, String set) {
        String json = """
                {
                	"name": "%s",
                	"generation": "%s"
                }
                """;
        return String.format(json, name, set);
    }


    private void validateToyResponseBody(ResultActions result, Toy expectedToy) throws Exception {
        result.andExpectAll(
                jsonPath("$.id").value(expectedToy.getId()),
                jsonPath("$.name").value(expectedToy.getName()),
                jsonPath("$.set").value(expectedToy.getSet())
        );
    }

    private void validateToyResponseBody(ResultActions result, List<Toy> expectedToys) throws Exception {
        MvcResult mvcResult = result.andReturn();
        String body = mvcResult.getResponse().getContentAsString();
        List<Toy> returnedToys = new ObjectMapper().readValue(body, new TypeReference<List<Toy>>() {
        });
        //test the order, and the deserialization
        for (int i = 0; i < returnedToys.size(); i++) {
            Toy expectedToy = expectedToys.get(i);
            Toy returnedToy = returnedToys.get(i);
            assertAll(
                    "The response body for Toys is not formatted correctly",
                    () -> assertEquals(expectedToy.getId(), returnedToy.getId()),
                    () -> assertEquals(expectedToy.getName(), returnedToy.getName()),
                    () -> assertEquals(expectedToy.getSet(), returnedToy.getSet())
            );

        }
    }

}

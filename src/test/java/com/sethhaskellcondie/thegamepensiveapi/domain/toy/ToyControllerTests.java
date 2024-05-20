package com.sethhaskellcondie.thegamepensiveapi.domain.toy;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sethhaskellcondie.thegamepensiveapi.domain.filter.Filter;
import com.sethhaskellcondie.thegamepensiveapi.exceptions.ExceptionMalformedEntity;
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

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ToyController.class)
public class ToyControllerTests {
    @TestConfiguration
    static class ToyControllerTestsConfiguration {
        @Bean
        public ToyGateway toyGateway(ToyService service) {
            return new ToyGateway(service);
        }
    }

    @Autowired
    private MockMvc mockMvc;
    @MockBean
    private ToyService service;

    @Test
    void getOneToy_ToyExists_ToySerializedCorrectly() throws Exception {
        final Toy toy = new Toy(1, "Donkey Kong", "Amiibo", Timestamp.from(Instant.now()), Timestamp.from(Instant.now()), null, new ArrayList<>());
        when(service.getById(1)).thenReturn(toy);

        final ResultActions result = mockMvc.perform(get("/toys/1"));

        result.andExpectAll(
                status().isOk(),
                content().contentType(MediaType.APPLICATION_JSON)
        );
        validateToyResponseBody(result, toy);
    }

    @Test
    void getOneToy_ToyMissing_NotFoundReturned() throws Exception {
        when(service.getById(999)).thenThrow(new ExceptionResourceNotFound("Error: Resource Not Found"));

        final ResultActions result = mockMvc.perform(get("/toys/999"));

        result.andExpectAll(
                status().isNotFound(),
                jsonPath("$.data").isEmpty(),
                jsonPath("$.errors").isNotEmpty()
        );
    }

    @Test
    void getAllToys_TwoToysPresent_TwoToysReturnedInArray() throws Exception {
        final Filter filter = new Filter("toy", "name", Filter.OPERATOR_STARTS_WITH, "startsWith");
        final Toy toy1 = new Toy(1, "Mega Man", "Amiibo", Timestamp.from(Instant.now()), Timestamp.from(Instant.now()), null, new ArrayList<>());
        final Toy toy2 = new Toy(2, "Samus", "Amiibo", Timestamp.from(Instant.now()), Timestamp.from(Instant.now()), null, new ArrayList<>());
        final List<Toy> toys = List.of(toy1, toy2);
        when(service.getWithFilters(List.of(filter))).thenReturn(toys);

        final String jsonContent = generateValidFilterPayload(filter);
        final ResultActions result = mockMvc.perform(
                post("/toys/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonContent)
        );

        result.andExpectAll(
                status().isOk(),
                content().contentType(MediaType.APPLICATION_JSON)
        );
        validateToyResponseBody(result, toys);
    }

    @Test
    void getAllToys_NoToysPresent_EmptyArrayReturned() throws Exception {
        final Filter filter = new Filter("toy", "name", Filter.OPERATOR_STARTS_WITH, "noResults");
        when(service.getWithFilters(List.of(filter))).thenReturn(List.of());

        final String jsonContent = generateValidFilterPayload(filter);
        final ResultActions result = mockMvc.perform(
                post("/toys/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonContent)
        );

        result.andExpectAll(
                status().isOk(),
                content().contentType(MediaType.APPLICATION_JSON),
                jsonPath("$.data").value(new ArrayList<>()),
                jsonPath("$.errors").isEmpty()
        );
    }

    @Test
    void createNewToy_ValidPayload_ToyCreatedAndReturned() throws Exception {
        final int expectedId = 34;
        final String expectedName = "Sora";
        final String expectedSet = "Disney Infinity";

        final Toy expectedToy = new Toy(expectedId, expectedName, expectedSet, Timestamp.from(Instant.now()), Timestamp.from(Instant.now()), null, new ArrayList<>());

        when(service.createNew(any())).thenReturn(expectedToy);

        final String jsonContent = generateValidCreateUpdatePayload(expectedName, expectedSet);
        final ResultActions result = mockMvc.perform(
                post("/toys")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonContent)
        );

        result.andExpect(status().isCreated());
        validateToyResponseBody(result, expectedToy);
    }

    @Test
    void createNewToy_NameBlank_ReturnBadRequest() throws Exception {
        final String jsonContent = generateValidCreateUpdatePayload("", "set");

        when(service.createNew(any())).thenThrow(new ExceptionMalformedEntity(List.of(new Exception("Error 1"), new Exception("Error 2"))));

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
    void updateExistingToy_ValidUpdate_ReturnOk() throws Exception {
        final int expectedId = 99;
        final String expectedName = "Baloo";
        final String expectedSet = "Disney Infinity";

        final Toy existingToy = new Toy(expectedId, "Old Name", "Old Set", Timestamp.from(Instant.now()), Timestamp.from(Instant.now()), null, new ArrayList<>());
        final Toy updatedToy = new Toy(expectedId, expectedName, expectedSet, Timestamp.from(Instant.now()), Timestamp.from(Instant.now()), null, new ArrayList<>());

        when(service.getById(expectedId)).thenReturn(existingToy);
        when(service.updateExisting(updatedToy)).thenReturn(updatedToy);

        final String jsonContent = generateValidCreateUpdatePayload(expectedName, expectedSet);
        final ResultActions result = mockMvc.perform(
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

        final String jsonContent = generateValidCreateUpdatePayload("invalidId", "");
        final ResultActions result = mockMvc.perform(
                put("/toys/79")
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
        when(service.getById(27)).thenReturn(new Toy(27, "MarkedForDeletion", "Set", Timestamp.from(Instant.now()), Timestamp.from(Instant.now()), null, new ArrayList<>()));

        final ResultActions result = mockMvc.perform(
                delete("/toys/27")
        );

        result.andExpectAll(
                status().isNoContent(),
                jsonPath("$.data").isEmpty(),
                jsonPath("$.errors").isEmpty()
        );
    }

    @Test
    void deleteExistingToy_InvalidId_ReturnNotFound() throws Exception {
        doThrow(new ExceptionResourceNotFound("Error: Resource Not Found")).when(service).deleteById(27);

        final ResultActions result = mockMvc.perform(
                delete("/toys/27")
        );

        result.andExpectAll(
                status().isNotFound(),
                jsonPath("$.data").isEmpty(),
                jsonPath("$.errors").isNotEmpty()
        );
    }

    private String generateValidCreateUpdatePayload(String name, String set) {
        final String json = """
                {
                	"toy": {
                	    "name": "%s",
                	    "set": "%s"
                	    }
                }
                """;
        return String.format(json, name, set);
    }

    private String generateValidFilterPayload(Filter filter) {
        final String json = """
                {
                  "filters": [
                    {
                      "resource": "%s",
                      "field": "%s",
                      "operator": "%s",
                      "operand": "%s"
                    }
                  ]
                }
                """;
        return String.format(json, filter.getKey(), filter.getField(), filter.getOperator(), filter.getOperand());
    }

    private void validateToyResponseBody(ResultActions result, Toy expectedToy) throws Exception {
        result.andExpectAll(
                jsonPath("$.data.key").value("toy"),
                jsonPath("$.data.id").value(expectedToy.getId()),
                jsonPath("$.data.name").value(expectedToy.getName()),
                jsonPath("$.data.set").value(expectedToy.getSet()),
                jsonPath("$.data.createdAt").isNotEmpty(),
                jsonPath("$.data.updatedAt").isNotEmpty(),
                jsonPath("$.data.deletedAt").isEmpty(),
                jsonPath("$.errors").isEmpty()
        );
    }

    private void validateToyResponseBody(ResultActions result, List<Toy> expectedToys) throws Exception {
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
            Toy expectedToy = expectedToys.get(i);
            ToyResponseDto returnedToy = returnedToys.get(i);
            assertAll(
                    "The response body for Toys is not formatted correctly",
                    () -> assertEquals("toy", returnedToy.key()),
                    () -> assertEquals(expectedToy.getId(), returnedToy.id()),
                    () -> assertEquals(expectedToy.getName(), returnedToy.name()),
                    () -> assertEquals(expectedToy.getSet(), returnedToy.set())
            );
        }
    }
}

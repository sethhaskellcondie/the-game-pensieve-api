package com.sethhaskellcondie.thegamepensiveapi.domain.system;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sethhaskellcondie.thegamepensiveapi.domain.filter.Filter;
import com.sethhaskellcondie.thegamepensiveapi.domain.filter.FilterRequestDto;
import com.sethhaskellcondie.thegamepensiveapi.domain.exceptions.ExceptionFailedDbValidation;
import com.sethhaskellcondie.thegamepensiveapi.domain.exceptions.ExceptionMalformedEntity;
import com.sethhaskellcondie.thegamepensiveapi.domain.exceptions.ExceptionResourceNotFound;
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

@Deprecated
//WebMvcTest is a "slice test" used for testing controllers with a mock servlet for http requests
@WebMvcTest(SystemController.class)
public class SystemControllerTests {
    //WebMvcTest will set up the dependencies for the SystemController
    //Since my gateway classes are @Components not automatically included by WebMvcTest
    //I need to manually set up a config for the gateway, just for this test
    @TestConfiguration
    static class SystemControllerTestsConfiguration {
        @Bean
        public SystemGateway systemGateway(SystemService service) {
            return new SystemGateway(service);
        }
    }

    //MockMvc Docs -> https://docs.spring.io/spring-framework/reference/testing/spring-mvc-test-framework.html
    @Autowired
    private MockMvc mockMvc;

    //@Service beans are not included with the WebMvcTest configuration they will need to be mocked
    @MockBean
    private SystemService service;

    @Test
    void getOneSystem_SystemExists_SystemSerializedCorrectly() throws Exception {
        final System system = new System(1, "test", 3, false, Timestamp.from(Instant.now()), Timestamp.from(Instant.now()), null, new ArrayList<>());
        when(service.getById(1)).thenReturn(system);

        final ResultActions result = mockMvc.perform(get("/v1/systems/1"));

        // result.andDo(print()); //will print out the result to the console
        result.andExpectAll(
                status().isOk(),
                content().contentType(MediaType.APPLICATION_JSON)
        );
        validateSystemResponseBody(result, system);
    }

    @Test
    void getOneSystem_SystemMissing_NotFoundReturned() throws Exception {
        when(service.getById(999)).thenThrow(new ExceptionResourceNotFound("Error: Resource Not Found"));

        final ResultActions result = mockMvc.perform(get("/v1/systems/999"));

        result.andExpectAll(
                status().isNotFound(),
                jsonPath("$.data").isEmpty(),
                jsonPath("$.errors").isNotEmpty()
        );
    }

    @Test
    void getAllSystems_TwoSystemPresent_TwoSystemsReturnedInArray() throws Exception {
        final FilterRequestDto filter = new FilterRequestDto("system", "name", Filter.OPERATOR_STARTS_WITH, "startsWith");
        final System system1 = new System(1, "test", 10, false, Timestamp.from(Instant.now()), Timestamp.from(Instant.now()), null, new ArrayList<>());
        final System system2 = new System(2, "test again", 20, true, Timestamp.from(Instant.now()), Timestamp.from(Instant.now()), null, new ArrayList<>());
        final List<System> systems = List.of(system1, system2);
        when(service.getWithFilters(List.of(filter))).thenReturn(systems);

        final String jsonContent = generateValidFilterPayload(filter);
        final ResultActions result = mockMvc.perform(
                post("/v1/systems/function/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonContent)
        );

        result.andExpectAll(
                status().isOk(),
                content().contentType(MediaType.APPLICATION_JSON)
        );
        validateSystemResponseBody(result, systems);
    }

    @Test
    void getAllSystems_NoSystemsPresent_EmptyArrayReturned() throws Exception {
        final FilterRequestDto filter = new FilterRequestDto("system", "name", Filter.OPERATOR_STARTS_WITH, "noResults");
        when(service.getWithFilters(List.of(filter))).thenReturn(List.of());

        final String jsonContent = generateValidFilterPayload(filter);
        final ResultActions result = mockMvc.perform(
                post("/v1/systems/function/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}")
        );

        result.andExpectAll(
                status().isOk(),
                content().contentType(MediaType.APPLICATION_JSON),
                jsonPath("$.data").value(new ArrayList<>()),
                jsonPath("$.errors").isEmpty()
        );
    }

    @Test
    void createNewSystem_ValidPayload_SystemCreatedAndReturned() throws Exception {
        final int expectedId = 44;
        final String expectedName = "Create Test";
        final int expectedGeneration = 3;
        final boolean expectedHandheld = false;

        final System expectedSystemPersisted = new System(expectedId, expectedName, expectedGeneration, expectedHandheld,
                Timestamp.from(Instant.now()), Timestamp.from(Instant.now()), null, new ArrayList<>()
        );

        when(service.createNew(any())).thenReturn(expectedSystemPersisted);

        final String jsonContent = generateValidCreateUpdatePayload(expectedName, expectedGeneration, expectedHandheld);
        ResultActions result = mockMvc.perform(
                post("/v1/systems")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonContent)
        );

        result.andExpect(status().isCreated());
        validateSystemResponseBody(result, expectedSystemPersisted);
    }

    @Test
    void createNewSystem_SystemNameBlank_ReturnBadRequest() throws Exception {
        final String jsonContent = generateValidCreateUpdatePayload("", 3, false);

        when(service.createNew(any())).thenThrow(new ExceptionMalformedEntity(List.of(new Exception("Error 1"), new Exception("Error 2"))));

        final ResultActions result = mockMvc.perform(
                post("/v1/systems")
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
    void createNewSystem_SystemNameDuplicate_ReturnBadRequest() throws Exception {
        final String jsonContent = generateValidCreateUpdatePayload("duplicate check", 3, false);

        when(service.createNew(any())).thenThrow(new ExceptionFailedDbValidation("Error: DB Validation"));

        final ResultActions result = mockMvc.perform(
                post("/v1/systems")
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
    void updateExistingSystem_ValidUpdate_ReturnOk() throws Exception {
        final int expectedId = 44;
        final String expectedName = "Updated Name";
        final int expectedGeneration = 3;
        final boolean expectedHandheld = false;

        final System existingSystem = new System(expectedId, "Created Name", 4, true, Timestamp.from(Instant.now()), Timestamp.from(Instant.now()), null, new ArrayList<>());
        final System updatedSystem = new System(expectedId, expectedName, expectedGeneration, expectedHandheld,
                Timestamp.from(Instant.now()), Timestamp.from(Instant.now()), null, new ArrayList<>()
        );

        when(service.getById(expectedId)).thenReturn(existingSystem);
        when(service.updateExisting(updatedSystem)).thenReturn(updatedSystem);

        final String jsonContent = generateValidCreateUpdatePayload(expectedName, expectedGeneration, expectedHandheld);
        final ResultActions result = mockMvc.perform(
                put("/v1/systems/" + expectedId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonContent)
        );

        result.andExpect(status().isOk());
        validateSystemResponseBody(result, updatedSystem);
    }

    @Test
    void updateExistingSystem_InvalidId_ReturnNotFound() throws Exception {
        when(service.getById(33)).thenThrow(new ExceptionResourceNotFound("Error: Resource Not Found"));

        final String jsonContent = generateValidCreateUpdatePayload("testName", 3, false);
        final ResultActions result = mockMvc.perform(
                put("/v1/systems/33")
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
        final ResultActions result = mockMvc.perform(
                delete("/v1/systems/22")
        );

        result.andExpectAll(
                status().isNoContent(),
                jsonPath("$.data").isEmpty(),
                jsonPath("$.errors").isEmpty()
        );
    }

    @Test
    void deleteExistingSystem_InvalidId_ReturnNotFound() throws Exception {
        doThrow(new ExceptionResourceNotFound("Error: Resource Not Found")).when(service).deleteById(22);

        final ResultActions result = mockMvc.perform(
                delete("/v1/systems/22")
        );

        result.andExpectAll(
                status().isNotFound(),
                jsonPath("$.data").isEmpty(),
                jsonPath("$.errors").isNotEmpty()
        );
    }

    private String generateValidCreateUpdatePayload(String name, int generation, boolean handheld) {
        final String json = """
                {
                    "system": {
                        "name": "%s",
                        "generation": %d,
                        "handheld": %b
                    }
                }
                """;
        return String.format(json, name, generation, handheld);
    }

    private String generateValidFilterPayload(FilterRequestDto filter) {
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
        return String.format(json, filter.key(), filter.field(), filter.operator(), filter.operand());
    }

    private void validateSystemResponseBody(ResultActions result, System expectedSystem) throws Exception {
        result.andExpectAll(
                jsonPath("$.data.key").value("system"),
                jsonPath("$.data.id").value(expectedSystem.getId()),
                jsonPath("$.data.name").value(expectedSystem.getName()),
                jsonPath("$.data.generation").value(expectedSystem.getGeneration()),
                jsonPath("$.data.handheld").value(expectedSystem.isHandheld()),
                jsonPath("$.data.createdAt").isNotEmpty(),
                jsonPath("$.data.updatedAt").isNotEmpty(),
                jsonPath("$.data.deletedAt").isEmpty(),
                jsonPath("$.errors").isEmpty()
        );
    }

    private void validateSystemResponseBody(ResultActions result, List<System> expectedSystems) throws Exception {
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
            final System expectedSystem = expectedSystems.get(i);
            final SystemResponseDto returnedSystem = returnedSystems.get(i);
            assertAll(
                    "The response body is not formatted correctly",
                    () -> assertEquals("system", returnedSystem.key()),
                    () -> assertEquals(expectedSystem.getId(), returnedSystem.id()),
                    () -> assertEquals(expectedSystem.getName(), returnedSystem.name()),
                    () -> assertEquals(expectedSystem.getGeneration(), returnedSystem.generation()),
                    () -> assertEquals(expectedSystem.isHandheld(), returnedSystem.handheld())
            );
        }
    }

}

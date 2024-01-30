package com.sethhaskellcondie.thegamepensiveapi.system;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;

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

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sethhaskellcondie.thegamepensiveapi.domain.system.System;
import com.sethhaskellcondie.thegamepensiveapi.domain.system.SystemController;
import com.sethhaskellcondie.thegamepensiveapi.domain.system.SystemGateway;
import com.sethhaskellcondie.thegamepensiveapi.domain.system.SystemGatewayImpl;
import com.sethhaskellcondie.thegamepensiveapi.domain.system.SystemServiceImpl;
import com.sethhaskellcondie.thegamepensiveapi.exceptions.ExceptionFailedDbValidation;
import com.sethhaskellcondie.thegamepensiveapi.exceptions.ExceptionResourceNotFound;

//WebMvcTest is a "slice test" used for testing controllers with a mock servlet for http requests
@WebMvcTest(SystemController.class)
public class SystemWebTests {
	//WebMvcTest will set up the dependencies for the SystemController
	//Since my gateway classes are @Components not automatically included by WebMvcTest
	//I need to manually set up a config for the gateway, just for this test
	@TestConfiguration
	static class SystemWebTestsConfiguration {
		@Bean
		public SystemGateway systemGateway(SystemServiceImpl service) {
			return new SystemGatewayImpl(service);
		}
	}

	@Autowired
	private MockMvc mockMvc;

	//@Service beans are not included with the WebMvcTest configuration they will need to be mocked
	@MockBean
	private SystemServiceImpl service;

	@Test
	void getOneSystem_SystemExists_SystemSerializedCorrectly() throws Exception {
		System system = new System(1, "test", 3, false);
		when(service.getById(1)).thenReturn(system);

		ResultActions result = mockMvc.perform(get("/systems/1"));

		result.andDo(print()); //will print out the result to the console
		result.andExpectAll(
			status().isOk(),
			content().contentType(MediaType.APPLICATION_JSON)
		);
		validateSystemResponseBody(result, system);
	}

	@Test
	void getOneSystem_SystemMissing_NotFoundReturned() throws Exception {
		//this is testing that the ExceptionHandler.java class that contains the @ControllerAdvice
		//is handling the getById request with a bad id correctly
		when(service.getById(999)).thenThrow(ExceptionResourceNotFound.class);

		ResultActions result = mockMvc.perform(get("/systems/999"));

		result.andExpectAll(
			status().isNotFound(),
			jsonPath("$.message").hasJsonPath(), //the mock can't throw a message, just check for the path
			jsonPath("$.status").exists()
		);
	}

	@Test
	void getAllSystems_TwoSystemPresent_TwoSystemsReturnedInArray() throws Exception {
		System system1 = new System(1, "test", 10, false);
		System system2 = new System(2, "test again", 20, true);
		List<System> systems = List.of(system1, system2);
		when(service.getWithFilters("")).thenReturn(systems);

		ResultActions result = mockMvc.perform(get("/systems"));

		result.andExpectAll(
			status().isOk(),
			content().contentType(MediaType.APPLICATION_JSON)
		);
		validateSystemResponseBody(result, systems);
	}

	@Test
	void getAllSystems_NoSystemsPresent_EmptyArrayReturned() throws Exception {
		when(service.getWithFilters("")).thenReturn(List.of());

		ResultActions result = mockMvc.perform(get("/systems"));

		result.andExpectAll(
			status().isOk(),
			content().contentType("application/json"),
			jsonPath("$").value(new ArrayList<>())
		);
	}

	@Test
	void createNewSystem_ValidPayload_SystemCreatedAndReturned() throws Exception {
		int expectedId = 44;
		String expectedName = "Create Test";
		int expectedGeneration = 3;
		boolean expectedHandheld = false;

		System expectedSystemPersisted = new System(expectedId, expectedName, expectedGeneration, expectedHandheld);

		when(service.createNew(any())).thenReturn(expectedSystemPersisted);

		String jsonContent = generateValidCreateUpdatePayload(expectedName, expectedGeneration, expectedHandheld);
		ResultActions result = mockMvc.perform(
			post("/systems")
				.contentType(MediaType.APPLICATION_JSON)
				.content(jsonContent)
		);

		result.andExpect(status().isCreated());
		validateSystemResponseBody(result, expectedSystemPersisted);
	}

	@Test
	void createNewSystem_SystemNameBlank_ReturnBadRequest() throws Exception {
		//only testing this on create not on update
		//this is testing the SystemRequestDto constructor validation
		String jsonContent = generateValidCreateUpdatePayload("", 3, false);

		ResultActions result = mockMvc.perform(
			post("/systems")
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
	void createNewSystem_SystemGenerationMissing_ReturnBadRequest() throws Exception {
		//only testing this on create not on update
		//this is testing the SystemRequestDto constructor validation
		String jsonContent = """
			{
			  "name": "test name",
			  "handheld": false
			}
			""";

		ResultActions result = mockMvc.perform(
			post("/systems")
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
	void createNewSystem_SystemHandheldMissing_ReturnBadRequest() throws Exception {
		//only testing this on create not on update
		//this is testing the SystemRequestDto constructor validation
		String jsonContent = """
			{
			  "name": "test name",
			  "generation": 3
			}
			""";

		ResultActions result = mockMvc.perform(
			post("/systems")
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
	void createNewSystem_SystemNameDuplicate_ReturnPreConditionFailed() throws Exception {
		//only testing this on create not on update
		String jsonContent = generateValidCreateUpdatePayload("duplicate check", 3, false);

		when(service.createNew(any())).thenThrow(ExceptionFailedDbValidation.class);

		ResultActions result = mockMvc.perform(
			post("/systems")
				.contentType(MediaType.APPLICATION_JSON)
				.content(jsonContent)
		);

		result.andExpectAll(
			status().isPreconditionFailed(),
			jsonPath("$.message").hasJsonPath(), //the mock can't throw the exception with a message
			jsonPath("$.status").exists()
		);
	}

	@Test
	void updateExistingSystem_ValidUpdate_ReturnOk() throws Exception {
		int expectedId = 44;
		String expectedName = "Updated Name";
		int expectedGeneration = 3;
		boolean expectedHandheld = false;

		System existingSystem = new System(expectedId, "Created Name", 4, true);
		System updatedSystem = new System(expectedId, expectedName, expectedGeneration, expectedHandheld);

		when(service.getById(expectedId)).thenReturn(existingSystem);
		when(service.updateExisting(updatedSystem)).thenReturn(updatedSystem);

		String jsonContent = generateValidCreateUpdatePayload(expectedName, expectedGeneration, expectedHandheld);
		ResultActions result = mockMvc.perform(
			put("/systems/" + expectedId)
				.contentType(MediaType.APPLICATION_JSON)
				.content(jsonContent)
		);

		result.andExpect(status().isOk());
		validateSystemResponseBody(result, updatedSystem);
	}

	@Test
	void updateExistingSystem_InvalidId_ReturnNotFound() throws Exception {
		when(service.getById(33)).thenReturn(new System());

		String jsonContent = generateValidCreateUpdatePayload("testName", 3, false);
		ResultActions result = mockMvc.perform(
			put("/systems/33")
				.contentType(MediaType.APPLICATION_JSON)
				.content(jsonContent)
		);

		result.andExpect(status().isNotFound());
	}

	@Test
	void deleteExistingSystem_SystemExists_ReturnNoContent() throws Exception {
		when(service.getById(22)).thenReturn(new System(22, "deleteTest", 5, false));

		ResultActions result = mockMvc.perform(
			delete("/systems/22")
		);

		result.andExpect(status().isNoContent());
	}

	@Test
	void deleteExistingSystem_InvalidId_ReturnNotFound() throws Exception {
		when(service.getById(22)).thenReturn(new System());

		ResultActions result = mockMvc.perform(
			delete("/systems/22")
		);

		result.andExpect(status().isNotFound());
	}

	private String generateValidCreateUpdatePayload(String name, int generation, boolean handheld) {
		String json = """
			{
			  "name": "%s",
			  "generation": %d,
			  "handheld": %b
			}
			""";
		return String.format(json, name, generation, handheld);
	}

	private void validateSystemResponseBody(ResultActions result, System expectedSystem) throws Exception {
		result.andExpectAll(
			jsonPath("$.id").value(expectedSystem.getId()),
			jsonPath("$.name").value(expectedSystem.getName()),
			jsonPath("$.generation").value(expectedSystem.getGeneration()),
			jsonPath("$.handheld").value(expectedSystem.isHandheld())
		);
	}

	private void validateSystemResponseBody(ResultActions result, List<System> expectedSystems) throws Exception {
		MvcResult mvcResult = result.andReturn();
		String body = mvcResult.getResponse().getContentAsString();
		List<System> returnedSystems = new ObjectMapper().readValue(body, new TypeReference<List<System>>(){});
		//testing order as well as each member being deserialized correctly
		for (int i = 0; i < returnedSystems.size(); i++) {
			System expectedSystem = expectedSystems.get(i);
			System returnedSystem = returnedSystems.get(i);
			assertAll(
				"The response body is not formatted correctly",
				() -> assertEquals(expectedSystem.getId(), returnedSystem.getId()),
				() -> assertEquals(expectedSystem.getName(), returnedSystem.getName()),
				() -> assertEquals(expectedSystem.getGeneration(), returnedSystem.getGeneration()),
				() -> assertEquals(expectedSystem.isHandheld(), returnedSystem.isHandheld())
			);
		}
	}

}

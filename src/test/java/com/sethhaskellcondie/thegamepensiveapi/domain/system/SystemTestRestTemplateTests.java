package com.sethhaskellcondie.thegamepensiveapi.domain.system;

import com.sethhaskellcondie.thegamepensiveapi.domain.filter.Filter;
import org.apache.commons.lang3.RandomStringUtils;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import java.sql.Timestamp;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * This is another class where I experiment with a new kind of integration test. This time using a SpringBootTest
 * to run the application context on a real server through a random port. This will allow me to use a TestRestTemplate
 * to run RestApi calls on the given server. This will run slower than the MockMvc but will run closer to a production
 * environment. There is another iteration on the TestRestTemplate that will run async called a WebTestClient.
 * A couple of big features for the TestRestTemplate is that it can be provided with auth credentials, and different
 * options that can be used to test different parts of the system when running as a server.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test-container")
public class SystemTestRestTemplateTests {

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void postSystem_ValidPayload_SystemCreatedAndReturned() {
        final String expectedName = "NES";
        final int expectedGeneration = 3;
        final boolean expectedHandheld = false;

        final ResponseEntity<Map> response = this.postCustomSystem(expectedName, expectedGeneration, expectedHandheld);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        validateSystemResponseBody(response, expectedName, expectedGeneration, expectedHandheld);
    }

    @Test
    void postSystem_FailedValidation_ReturnArrayOfErrors() {
        final SystemRequestDto requestDto = new SystemRequestDto("", -1, null);
        final HttpEntity<SystemRequestDto> request = new HttpEntity<>(requestDto);

        final ResponseEntity<Map> response = restTemplate.postForEntity("/systems", request, Map.class);
        final Map<String, Object> data = (Map<String, Object>) response.getBody().get("data");
        final List<String> errors = (List<String>) response.getBody().get("errors");

        assertAll(
                "Error response wasn't returned properly on bad POST system request. (failed input validation)",
                () -> assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode()),
                () -> assertNull(data),
                () -> assertEquals(3, errors.size())
        );
    }

    @Test
    void postSystem_SystemNameDuplicate_ReturnBadRequest() {
        final String duplicateName = "Game Boy";
        final int generation = 3;
        final boolean handheld = true;

        this.postCustomSystem(duplicateName, generation, handheld);

        final SystemRequestDto requestDto = new SystemRequestDto(duplicateName, generation, handheld);
        final HttpEntity<SystemRequestDto> request = new HttpEntity<>(requestDto);

        final ResponseEntity<Map> response = restTemplate.postForEntity("/systems", request, Map.class);
        final Map<String, Object> data = (Map<String, Object>) response.getBody().get("data");
        final List<String> errors = (List<String>) response.getBody().get("errors");

        assertAll(
                "Error response wasn't returned properly on bad POST system request. (duplicate name)",
                () -> assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode()),
                () -> assertNull(data),
                () -> assertEquals(1, errors.size())
        );
    }

    @Test
    void getOneSystem_SystemExists_SystemSerializedCorrectly() {
        final String name = "Genesis";
        final int generation = 4;
        final boolean handheld = true;
        final ResponseEntity<Map> postResponse = this.postCustomSystem(name, generation, handheld);
        final SystemResponseDto expectedDto = resultToResponseDto(postResponse);

        final ResponseEntity<Map> response = restTemplate.getForEntity("/systems/" + expectedDto.id(), Map.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        validateSystemResponseBody(response, name, generation, handheld);
    }

    @Test
    void getOneSystem_SystemMissing_NotFoundReturned() {
        final ResponseEntity<Map> response = restTemplate.getForEntity("/systems/-1", Map.class);
        final Map<String, Object> data = (Map<String, Object>) response.getBody().get("data");
        final List<String> errors = (List<String>) response.getBody().get("errors");

        assertAll(
                "Error response wasn't returned properly on bad POST system request. (not found)",
                () -> assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode()),
                () -> assertNull(data),
                () -> assertEquals(1, errors.size())
        );
    }

    @Test
    void getAllSystems_TwoSystemPresent_TwoSystemsReturnedInArray() {
        final String name1 = "startsWith Super Nintendo";
        final int generation1 = 4;
        final boolean handheld1 = false;
        final ResponseEntity<Map> result1 = this.postCustomSystem(name1, generation1, handheld1);
        final SystemResponseDto responseDto1 = resultToResponseDto(result1);

        final String name2 = "startsWith Sony Playstation";
        final int generation2 = 4;
        final boolean handheld2 = false;
        final ResponseEntity<Map> result2 = this.postCustomSystem(name2, generation2, handheld2);
        final SystemResponseDto responseDto2 = resultToResponseDto(result2);

        final Filter filter = new Filter("system", "name", Filter.FILTER_OPERATOR_STARTS_WITH, "startsWith");
        final Map<String, List<Filter>> requestBody = new HashMap<>();
        requestBody.put("filters", List.of(filter));
        final HttpEntity<Map<String, List<Filter>>> request = new HttpEntity<>(requestBody);

        final ResponseEntity<Map> response = restTemplate.postForEntity("/systems/search", request, Map.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        validateSystemResponseBody(response, List.of(responseDto1, responseDto2));
    }

    @Test
    void getAllSystems_NoSystemsPresent_EmptyArrayReturned() {
        final Filter filter = new Filter("system", "name", Filter.FILTER_OPERATOR_STARTS_WITH, "noResults");
        final Map<String, List<Filter>> requestBody = new HashMap<>();
        requestBody.put("filters", List.of(filter));
        final HttpEntity<Map<String, List<Filter>>> request = new HttpEntity<>(requestBody);

        final ResponseEntity<Map> response = restTemplate.postForEntity("/systems/search", request, Map.class);
        final List<Map<String, Object>> returnedSystems = (List<Map<String, Object>>) response.getBody().get("data");
        final List<String> errors = (List<String>) response.getBody().get("errors");

        assertAll(
                "Response Body Incorrect on Empty Search Result",
                () -> assertEquals(HttpStatus.OK, response.getStatusCode()),
                () -> assertEquals(0, returnedSystems.size()),
                () -> assertNull(errors)
        );
    }

    @Test
    void updateExistingSystem_ValidUpdate_ReturnOk() {
        final ResponseEntity<Map> existingResult = this.postSystem();
        final SystemResponseDto responseDto = resultToResponseDto(existingResult);

        final String newName = "Playstation 2";
        final int newGeneration = 6;
        final boolean newHandheld = false;
        final SystemResponseDto expectedDto = new SystemResponseDto(
                responseDto.type(),
                responseDto.id(),
                newName,
                newGeneration,
                newHandheld,
                responseDto.createdAt(),
                responseDto.updatedAt(),
                responseDto.deletedAt()
        );

        final SystemRequestDto requestDto = new SystemRequestDto(newName, newGeneration, newHandheld);
        final HttpEntity<SystemRequestDto> request = new HttpEntity<>(requestDto);

        final ResponseEntity<Map> response = restTemplate.exchange("/systems/" + responseDto.id(), HttpMethod.PUT, request, Map.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        validateSystemResponseBody(response, expectedDto);
    }

    @Test
    void updateExistingSystem_InvalidId_ReturnNotFound() {
        final SystemRequestDto requestDto = new SystemRequestDto("ValidButMissing", 3, false);
        final HttpEntity<SystemRequestDto> request = new HttpEntity<>(requestDto);

        final ResponseEntity<Map> response = restTemplate.exchange("/systems/-1", HttpMethod.PUT, request, Map.class);
        final Map<String, Object> data = (Map<String, Object>) response.getBody().get("data");
        final List<String> errors = (List<String>) response.getBody().get("errors");

        assertAll(
                "Error response wasn't returned properly on bad PUT system request. (not found)",
                () -> assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode()),
                () -> assertNull(data),
                () -> assertEquals(1, errors.size())
        );
    }

    @Test
    void deleteExistingSystem_SystemExists_ReturnNoContent() {
        final ResponseEntity<Map> existingResult = this.postSystem();
        final SystemResponseDto responseDto = resultToResponseDto(existingResult);

        final ResponseEntity<Map> response = restTemplate.exchange("/systems/" + responseDto.id(), HttpMethod.DELETE, null, Map.class);

        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
        //Limitation: I may be doing this wrong, but I haven't been able to get a DELETE call to return a response body.
        //So I can't check so make sure that the errors are empty and the data is ALSO empty.
    }

    @Test
    void deleteExistingSystem_InvalidId_ReturnNotFound() {
        final ResponseEntity<Map> response = restTemplate.exchange("/systems/-1", HttpMethod.DELETE, null, Map.class);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        //Limitation: I may be doing this wrong, but I haven't been able to get a DELETE call to return a response body.
        //So I can't check so make sure that the errors are empty and the data is ALSO empty.
    }

    private SystemResponseDto resultToResponseDto(ResponseEntity<Map> response) {
        final Map<String, Object> data = (Map<String, Object>) response.getBody().get("data");
        return new SystemResponseDto(
                (String) data.get("type"),
                (int) data.get("id"),
                (String) data.get("name"),
                (int) data.get("generation"),
                (boolean) data.get("handheld"),
                (Timestamp) data.get("created_at"),
                (Timestamp) data.get("updated_at"),
                (Timestamp) data.get("deleted_at")
        );
    }

    private void validateSystemResponseBody(ResponseEntity<Map> response, String expectedName, int expectedGeneration, boolean expectedHandheld) {
        final List<String> errors = (List<String>) response.getBody().get("errors");
        //the SystemResponseDto is returned as a Map
        final Map<String, Object> data = (Map<String, Object>) response.getBody().get("data");

        assertAll(
                "The SystemResponseBody was formatted incorrectly",
                () -> assertEquals("system", data.get("type")),
                () -> assertNotNull(data.get("id")),
                () -> assertEquals(expectedName, data.get("name")),
                () -> assertEquals(expectedGeneration, data.get("generation")),
                () -> assertEquals(expectedHandheld, data.get("handheld")),
                () -> assertNull(errors)
        );
    }

    private void validateSystemResponseBody(ResponseEntity<Map> response, SystemResponseDto responseDto) {
        final List<String> errors = (List<String>) response.getBody().get("errors");
        final Map<String, Object> data = (Map<String, Object>) response.getBody().get("data");

        assertAll(
                "The SystemResponseBody was formatted incorrectly",
                () -> assertEquals(responseDto.type(), data.get("type")),
                () -> assertEquals(responseDto.id(), (int) data.get("id")),
                () -> assertEquals(responseDto.name(), data.get("name")),
                () -> assertEquals(responseDto.generation(), data.get("generation")),
                () -> assertEquals(responseDto.handheld(), data.get("handheld")),
                () -> assertNull(errors)
        );
    }

    private void validateSystemResponseBody(ResponseEntity<Map> response, List<SystemResponseDto> expectedSystems) {
        final List<Map<String, Object>> returnedSystems = (List<Map<String, Object>>) response.getBody().get("data");
        final List<String> errors = (List<String>) response.getBody().get("errors");
        assertNull(errors);

        //testing order as well as each member being deserialized correctly
        for (int i = 0; i < returnedSystems.size(); i++) {
            SystemResponseDto expectedSystem = expectedSystems.get(i);
            Map<String, Object> returnedSystem = returnedSystems.get(i);
            assertAll(
                    "The response body is not formatted correctly",
                    () -> assertEquals("system", returnedSystem.get("type")),
                    () -> assertEquals(expectedSystem.id(), (int) returnedSystem.get("id")),
                    () -> assertEquals(expectedSystem.name(), returnedSystem.get("name")),
                    () -> assertEquals(expectedSystem.generation(), (int) returnedSystem.get("generation")),
                    () -> assertEquals(expectedSystem.handheld(), returnedSystem.get("handheld"))
            );
        }
    }

    // --------- Transplants from the TestFactory ---------
    private String randomString(int length) {
        return RandomStringUtils.random(length, true, true);
    }

    private ResponseEntity<Map> postSystem() {
        final String name = "TestSystem-" + randomString(8);
        final int generation = 1;
        final boolean handheld = false;

        return postCustomSystem(name, generation, handheld);
    }

    private ResponseEntity<Map> postCustomSystem(String name, int generation, boolean handheld) {
        final SystemRequestDto requestDto = new SystemRequestDto(name, generation, handheld);
        final System system = new System().updateFromRequestDto(requestDto);
        final HttpEntity<System> request = new HttpEntity<>(system);

        final ResponseEntity<Map> response = restTemplate.postForEntity("/systems", request, Map.class);
        assertEquals(response.getStatusCode(), HttpStatus.CREATED);
        return response;
    }
}

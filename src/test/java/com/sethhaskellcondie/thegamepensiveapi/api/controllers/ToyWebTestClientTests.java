package com.sethhaskellcondie.thegamepensiveapi.api.controllers;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sethhaskellcondie.thegamepensiveapi.domain.entity.toy.ToyResponseDto;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.test.web.reactive.server.WebTestClient.ResponseSpec;
import org.testcontainers.shaded.org.apache.commons.lang3.RandomStringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * This is another class where I experiment with a new kind of integration test. This time using a WebTestClient
 * this runs the application context as real server through a random port. The WebTestClient can be run with or without
 * a server, it can be run similar to a MockMvc, and it's much more like that than running the TestRestTemplate.
 * The big advantage of the WebTestClient over the TestRestTemplate is that the TestRestTemplate runs the requests
 * synchronously while the WebTestClient will run the test asynchronously, so they will run faster overall.
 */
@Deprecated
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test-container")
public class ToyWebTestClientTests {

    @Autowired
    private WebTestClient client;

    @Test
    void postToy_ValidPayload_ToyCreatedAndReturned() {
        final String expectedName = "Sora";
        final String expectedSet = "Disney Infinity";

        final ResponseSpec response = this.postCustomToy(expectedName, expectedSet);

        response.expectStatus().isCreated();
        validateToyResponseBody(response, expectedName, expectedSet);
    }

    @Test
    void postNewToy_NameBlank_ReturnBadRequest() {
        final String formattedJson = formatToyPayload("", "");

        ResponseSpec response = client.post().uri("/v1/toys").contentType(MediaType.APPLICATION_JSON).bodyValue(formattedJson).exchange();

        response.expectStatus().isBadRequest();
        response.expectHeader().contentType(MediaType.APPLICATION_JSON);
        response.expectBody()
                .jsonPath("$.data").isEmpty()
                .jsonPath("$.errors").isNotEmpty();
    }

    @Test
    void getOneToy_ToyExists_ToySerializedCorrectly() throws JsonProcessingException {
        final String name = "Mario";
        final String set = "Amiibo";
        final ResponseSpec postResult = this.postCustomToy(name, set);
        final ToyResponseDto expectedDto = resultToResponseDto(postResult);

        ResponseSpec response = client.get().uri("/v1/toys/" + expectedDto.id()).exchange();

        response.expectStatus().isOk();
        response.expectHeader().contentType(MediaType.APPLICATION_JSON);
        validateToyResponseBody(response, expectedDto);
    }

    @Test
    void getOneToy_ToyMissing_NotFoundReturned() {
        ResponseSpec response = client.get().uri("/v1/toys/-1").exchange();

        response.expectStatus().isNotFound();
        response.expectHeader().contentType(MediaType.APPLICATION_JSON);
        response.expectBody()
                .jsonPath("$.data").isEmpty()
                .jsonPath("$.errors").isNotEmpty();
    }

    @Test
    void getAllToys_StartsWithFilter_TwoToysReturnedInArray() throws JsonProcessingException {
        final String name1 = "Epic MegaMan";
        final String set1 = "Amiibo";
        final ResponseSpec result1 = this.postCustomToy(name1, set1);
        final ToyResponseDto toyDto1 = resultToResponseDto(result1);

        final String name2 = "Epic Goofy";
        final String set2 = "Disney Infinity";
        final ResponseSpec result2 = this.postCustomToy(name2, set2);
        final ToyResponseDto toyDto2 = resultToResponseDto(result2);

        final String json = """
                {
                  "filters": [
                    {
                      "key": "toy",
                      "field": "name",
                      "operator": "starts_with",
                      "operand": "Epic "
                    }
                  ]
                }
                """;

        final ResponseSpec response = client.post().uri("/v1/toys/function/search")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(json)
                .exchange();

        validateToyResponseBody(response, List.of(toyDto1, toyDto2));
        response.expectStatus().isOk();
        response.expectHeader().contentType(MediaType.APPLICATION_JSON);
    }

    @Test
    void getAllToys_NoResultsFilter_EmptyArrayReturned() {
        final String json = """
                {
                  "filters": [
                    {
                      "key": "toy",
                      "field": "name",
                      "operator": "starts_with",
                      "operand": "NoResultsReturned"
                    }
                  ]
                }
                """;
        final ResponseSpec response = client.post().uri("/v1/toys/function/search")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(json)
                .exchange();

        response.expectStatus().isOk();
        response.expectHeader().contentType(MediaType.APPLICATION_JSON);
        response.expectBody()
                .jsonPath("$.data").isEqualTo(new ArrayList<>())
                .jsonPath("$.errors").isEmpty();
    }

    @Test
    void putExistingToy_ValidUpdate_ReturnOk() throws JsonProcessingException {
        final String name = "Donald Duck";
        final String set = "Disney Infinity";
        final ResponseSpec postResult = this.postCustomToy(name, set);
        final ToyResponseDto expectedDto = resultToResponseDto(postResult);

        final String newName = "Pit";
        final String newSet = "Amiibo";

        final String jsonContent = this.formatToyPayload(newName, newSet);
        final ResponseSpec response = client.put().uri("/v1/toys/" + expectedDto.id()).contentType(MediaType.APPLICATION_JSON).bodyValue(jsonContent).exchange();

        response.expectStatus().isOk();
        validateToyResponseBody(response, newName, newSet);
    }

    @Test
    void updateExistingToy_InvalidId_ReturnNotFound() {

        final String jsonContent = this.formatToyPayload("invalidId", "");
        final ResponseSpec response = client.put().uri("/v1/toys/-1").contentType(MediaType.APPLICATION_JSON).bodyValue(jsonContent).exchange();

        response.expectStatus().isNotFound();
        response.expectBody()
                        .jsonPath("$.data").isEmpty()
                        .jsonPath("$.errors").isNotEmpty();
    }

    @Test
    void deleteExistingToy_ToyExists_ReturnNoContent() throws JsonProcessingException {
        final ResponseSpec postResult = this.postToy();
        final ToyResponseDto expectedDto = resultToResponseDto(postResult);

        final ResponseSpec response = client.delete().uri("/v1/toys/" + expectedDto.id()).exchange();

        response.expectStatus().isNoContent();
        //-Limitation- I can't get the body to return properly, it is always returned as null...
    }

    @Test
    void deleteExistingToy_InvalidId_ReturnNotFound() {
        final ResponseSpec response = client.delete().uri("/v1/toys/-1").exchange();

        response.expectStatus().isNotFound()
                .expectBody()
                .jsonPath("$.data").isEmpty()
                .jsonPath("$.errors").isNotEmpty();
    }

    private void validateToyResponseBody(ResponseSpec result, String expectedName, String expectedSet) {
        result.expectBody()
                .jsonPath("$.data.key").isEqualTo("toy")
                .jsonPath("$.data.name").isEqualTo(expectedName)
                .jsonPath("$.data.set").isEqualTo(expectedSet);
    }

    private ToyResponseDto resultToResponseDto(ResponseSpec result) throws JsonProcessingException {
        final String responseString = result.expectBody(String.class).returnResult().getResponseBody();
        final Map<String, ToyResponseDto> body = new ObjectMapper().readValue(responseString, new TypeReference<>() { });
        return body.get("data");
    }

    private void validateToyResponseBody(ResponseSpec result, ToyResponseDto expectedResponse) {
        result.expectBody()
                .jsonPath("$.data.key").isEqualTo("toy")
                .jsonPath("$.data.id").isEqualTo(expectedResponse.id())
                .jsonPath("$.data.name").isEqualTo(expectedResponse.name())
                .jsonPath("$.data.set").isEqualTo(expectedResponse.set());
    }

    private void validateToyResponseBody(ResponseSpec result, List<ToyResponseDto> expectedToys) throws JsonProcessingException {
        final String responseString = result.expectBody(String.class).returnResult().getResponseBody();
        final Map<String, List<ToyResponseDto>> body = new ObjectMapper().readValue(responseString, new TypeReference<>() { });
        final List<ToyResponseDto> returnedToys = body.get("data");
        //test the order, and the deserialization
        for (int i = 0; i < returnedToys.size(); i++) {
            ToyResponseDto expectedToy = expectedToys.get(i);
            ToyResponseDto returnedToy = returnedToys.get(i);
            assertAll(
                    "The response body for Toys is not formatted correctly",
                    () -> assertEquals("toy", returnedToy.key()),
                    () -> assertEquals(expectedToy.id(), returnedToy.id()),
                    () -> assertEquals(expectedToy.name(), returnedToy.name()),
                    () -> assertEquals(expectedToy.set(), returnedToy.set())
            );
        }
    }

    private String randomString(int length) {
        return RandomStringUtils.random(length, true, true);
    }

    public ResponseSpec postToy() {
        final String name = "TestToy-" + randomString(8);
        final String set = "TestSet-" + randomString(8);
        return postCustomToy(name, set);
    }

    public ResponseSpec postCustomToy(String name, String set) {
        final String formattedJson = formatToyPayload(name, set);
        ResponseSpec response = client.post().uri("/v1/toys").contentType(MediaType.APPLICATION_JSON).bodyValue(formattedJson).exchange();

        response.expectStatus().isCreated();
        response.expectHeader().contentType(MediaType.APPLICATION_JSON);
        return response;
    }

    public String formatToyPayload(String name, String set) {
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
}

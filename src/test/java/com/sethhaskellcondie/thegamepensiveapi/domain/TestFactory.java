package com.sethhaskellcondie.thegamepensiveapi.domain;

import com.sethhaskellcondie.thegamepensiveapi.domain.filter.Filter;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.shaded.org.apache.commons.lang3.RandomStringUtils;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

public class TestFactory {

    private final MockMvc mockMvc;

    public TestFactory(MockMvc mockMvc) {
        this.mockMvc = mockMvc;
    }

    private String randomString(int length) {
        return RandomStringUtils.random(length, true, true);
    }

    public String formatFiltersPayload(Filter filter) {
        final String json = """
                {
                  "filters": [
                    {
                      "key": "%s",
                      "field": "%s",
                      "operator": "%s",
                      "operand": "%s"
                    }
                  ]
                }
                """;
        return String.format(json, filter.getKey(), filter.getField(), filter.getOperator(), filter.getOperand());
    }

    public ResultActions postSystem() throws Exception {
        final String name = "TestSystem-" + randomString(8);
        final int generation = 1;
        final boolean handheld = false;

        return postCustomSystem(name, generation, handheld);
    }

    public ResultActions postCustomSystem(String name, int generation, boolean handheld) throws Exception {
        final String json = """
                {
                  "system": {
                    "name": "%s",
                    "generation": %d,
                    "handheld": %b
                  }
                }
                """;
        final String formattedJson = String.format(json, name, generation, handheld);

        final ResultActions result = mockMvc.perform(
                post("/v1/systems")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(formattedJson)
        );

        result.andExpect(status().isCreated());
        return result;
    }

    public String formatSystemPayload(String name, Integer generation, Boolean handheld) {
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

    public ResultActions postToy() throws Exception {
        final String name = "TestToy-" + randomString(4);
        final String set = "TestSet-" + randomString(4);
        return postCustomToy(name, set);
    }

    public ResultActions postCustomToy(String name, String set) throws Exception {
        final String formattedJson = formatToyPayload(name, set);

        final ResultActions result = mockMvc.perform(
                post("/v1/toys")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(formattedJson)
        );

        result.andExpect(status().isCreated());
        return result;
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

    public ResultActions postCustomField() throws Exception {
        final String name = "TestCustomField-" + randomString(6);
        final String type = "Text";
        final String entityKey = "toy";
        return postCustomCustomField(name, type, entityKey);
    }

    public ResultActions postCustomCustomField(String name, String type, String entityKey) throws Exception {
        final String formattedJson = formatCustomFieldPayload(name, type, entityKey);

        final ResultActions result = mockMvc.perform(
                post("/v1/custom_fields")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(formattedJson)
        );

        result.andExpect(status().isCreated());
        return result;
    }

    public String formatCustomFieldPayload(String name, String type, String entityKey) {
        final String json = """
                {
                	"custom_field": {
                	    "name": "%s",
                	    "type": "%s",
                	    "entityKey": "%s"
                	    }
                }
                """;
        return String.format(json, name, type, entityKey);
    }
}

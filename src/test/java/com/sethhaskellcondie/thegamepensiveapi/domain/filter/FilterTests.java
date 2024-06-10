package com.sethhaskellcondie.thegamepensiveapi.domain.filter;

import com.sethhaskellcondie.thegamepensiveapi.domain.Keychain;
import com.sethhaskellcondie.thegamepensiveapi.domain.customfield.CustomField;
import com.sethhaskellcondie.thegamepensiveapi.domain.customfield.CustomFieldRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * These tests will test the GET endpoint results for filters
 * There will be unit tests that will do a deep dive into converting filters into SQL
 * in a SqlFilterTests class
 */
@SpringBootTest
@ActiveProfiles("test-container")
@AutoConfigureMockMvc
public class FilterTests {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    private CustomFieldRepository customFieldRepository;

    @BeforeEach
    void setUp() {
        customFieldRepository = new CustomFieldRepository(jdbcTemplate);
    }

    @Test
    void getFilters_NoFilters_ReturnEmpty() throws Exception {
        final ResultActions result = mockMvc.perform(get("/filters/missing_entity"));

        result.andExpectAll(
                status().isOk(),
                content().contentType(MediaType.APPLICATION_JSON),
                jsonPath("$.data.type").value("missing_entity_filters"),
                jsonPath("$.data.fields").isEmpty(),
                jsonPath("$.data.filters").isEmpty(),
                jsonPath("$.errors").isEmpty()
        );
    }

    @Test
    void getSystemFilters_FiltersSerializedCorrectly() throws Exception {
        String textCustomFieldName = "Text_Custom_Field";
        String textCustomFieldType = CustomField.TYPE_TEXT;
        customFieldRepository.insertCustomField(textCustomFieldName, textCustomFieldType, Keychain.SYSTEM_KEY);

        String numberCustomFieldName = "Number_Custom_Field";
        String numberCustomFieldType = CustomField.TYPE_NUMBER;
        customFieldRepository.insertCustomField(numberCustomFieldName, numberCustomFieldType, Keychain.SYSTEM_KEY);

        String booleanCustomFieldName = "Boolean_Custom_Field";
        String booleanCustomFieldType = CustomField.TYPE_BOOLEAN;
        customFieldRepository.insertCustomField(booleanCustomFieldName, booleanCustomFieldType, Keychain.SYSTEM_KEY);

        final ResultActions result = mockMvc.perform(get("/filters/system"));

        result.andExpectAll(
                status().isOk(),
                content().contentType(MediaType.APPLICATION_JSON),
                jsonPath("$.data.type").value("system_filters"),
                //The fields should be returned in this specific order, but we are not testing for that right now
                jsonPath("$.data.fields.name").value("text"),
                jsonPath("$.data.fields.generation").value("number"),
                jsonPath("$.data.fields.handheld").value("boolean"),
                jsonPath("$.data.fields.created_at").value("time"),
                jsonPath("$.data.fields.updated_at").value("time"),
                jsonPath("$.data.fields.all_fields").value("sort"),
                jsonPath("$.data.fields.pagination_fields").value("pagination"),
                jsonPath("$.data.fields." + textCustomFieldName).value(textCustomFieldType),
                jsonPath("$.data.fields." + numberCustomFieldName).value(numberCustomFieldType),
                jsonPath("$.data.fields." + booleanCustomFieldName).value(booleanCustomFieldType),
                //the filters should be returned in this specific order
                jsonPath("$.data.filters.name[0]").value("equals"),
                jsonPath("$.data.filters.name[1]").value("not_equals"),
                jsonPath("$.data.filters.name[2]").value("contains"),
                jsonPath("$.data.filters.name[3]").value("starts_with"),
                jsonPath("$.data.filters.name[4]").value("ends_with"),
                jsonPath("$.data.filters.generation[0]").value("equals"),
                jsonPath("$.data.filters.generation[1]").value("not_equals"),
                jsonPath("$.data.filters.generation[2]").value("greater_than"),
                jsonPath("$.data.filters.generation[3]").value("greater_than_equal_to"),
                jsonPath("$.data.filters.generation[4]").value("less_than"),
                jsonPath("$.data.filters.generation[5]").value("less_than_equal_to"),
                jsonPath("$.data.filters.handheld[0]").value("equals"),
                jsonPath("$.data.filters.created_at[0]").value("since"),
                jsonPath("$.data.filters.created_at[1]").value("before"),
                jsonPath("$.data.filters.updated_at[0]").value("since"),
                jsonPath("$.data.filters.updated_at[1]").value("before"),
                jsonPath("$.data.filters.all_fields[0]").value("order_by"),
                jsonPath("$.data.filters.all_fields[1]").value("order_by_desc"),
                jsonPath("$.data.filters.pagination_fields[0]").value("limit"),
                jsonPath("$.data.filters.pagination_fields[1]").value("offset"),
                jsonPath("$.data.filters." + textCustomFieldName + "[0]").value("equals"),
                jsonPath("$.data.filters." + textCustomFieldName + "[1]").value("not_equals"),
                jsonPath("$.data.filters." + textCustomFieldName + "[2]").value("contains"),
                jsonPath("$.data.filters." + textCustomFieldName + "[3]").value("starts_with"),
                jsonPath("$.data.filters." + textCustomFieldName + "[4]").value("ends_with"),
                jsonPath("$.data.filters." + numberCustomFieldName + "[0]").value("equals"),
                jsonPath("$.data.filters." + numberCustomFieldName + "[1]").value("not_equals"),
                jsonPath("$.data.filters." + numberCustomFieldName + "[2]").value("greater_than"),
                jsonPath("$.data.filters." + numberCustomFieldName + "[3]").value("greater_than_equal_to"),
                jsonPath("$.data.filters." + numberCustomFieldName + "[4]").value("less_than"),
                jsonPath("$.data.filters." + numberCustomFieldName + "[5]").value("less_than_equal_to"),
                jsonPath("$.data.filters." + booleanCustomFieldName + "[0]").value("equals"),
                jsonPath("$.errors").isEmpty()
        );
    }

    @Test
    void getToyFilters_FiltersSerializedCorrectly() throws Exception {
        String textCustomFieldName = "Text_Custom_Field";
        String textCustomFieldType = CustomField.TYPE_TEXT;
        customFieldRepository.insertCustomField(textCustomFieldName, textCustomFieldType, Keychain.TOY_KEY);

        String numberCustomFieldName = "Number_Custom_Field";
        String numberCustomFieldType = CustomField.TYPE_NUMBER;
        customFieldRepository.insertCustomField(numberCustomFieldName, numberCustomFieldType, Keychain.TOY_KEY);

        String booleanCustomFieldName = "Boolean_Custom_Field";
        String booleanCustomFieldType = CustomField.TYPE_BOOLEAN;
        customFieldRepository.insertCustomField(booleanCustomFieldName, booleanCustomFieldType, Keychain.TOY_KEY);

        final ResultActions result = mockMvc.perform(get("/filters/toy"));

        result.andExpectAll(
                status().isOk(),
                content().contentType(MediaType.APPLICATION_JSON),
                jsonPath("$.data.type").value("toy_filters"),
                jsonPath("$.data.fields.name").value("text"),
                jsonPath("$.data.fields.set").value("text"),
                jsonPath("$.data.fields.created_at").value("time"),
                jsonPath("$.data.fields.updated_at").value("time"),
                jsonPath("$.data.fields.all_fields").value("sort"),
                jsonPath("$.data.fields.pagination_fields").value("pagination"),
                jsonPath("$.data.fields." + textCustomFieldName).value(textCustomFieldType),
                jsonPath("$.data.fields." + numberCustomFieldName).value(numberCustomFieldType),
                jsonPath("$.data.fields." + booleanCustomFieldName).value(booleanCustomFieldType),
                jsonPath("$.data.filters.name[0]").value("equals"),
                jsonPath("$.data.filters.name[1]").value("not_equals"),
                jsonPath("$.data.filters.name[2]").value("contains"),
                jsonPath("$.data.filters.name[3]").value("starts_with"),
                jsonPath("$.data.filters.name[4]").value("ends_with"),
                jsonPath("$.data.filters.set[0]").value("equals"),
                jsonPath("$.data.filters.set[1]").value("not_equals"),
                jsonPath("$.data.filters.set[2]").value("contains"),
                jsonPath("$.data.filters.set[3]").value("starts_with"),
                jsonPath("$.data.filters.set[4]").value("ends_with"),
                jsonPath("$.data.filters.created_at[0]").value("since"),
                jsonPath("$.data.filters.created_at[1]").value("before"),
                jsonPath("$.data.filters.updated_at[0]").value("since"),
                jsonPath("$.data.filters.updated_at[1]").value("before"),
                jsonPath("$.data.filters.all_fields[0]").value("order_by"),
                jsonPath("$.data.filters.all_fields[1]").value("order_by_desc"),
                jsonPath("$.data.filters.pagination_fields[0]").value("limit"),
                jsonPath("$.data.filters.pagination_fields[1]").value("offset"),
                jsonPath("$.data.filters." + textCustomFieldName + "[0]").value("equals"),
                jsonPath("$.data.filters." + textCustomFieldName + "[1]").value("not_equals"),
                jsonPath("$.data.filters." + textCustomFieldName + "[2]").value("contains"),
                jsonPath("$.data.filters." + textCustomFieldName + "[3]").value("starts_with"),
                jsonPath("$.data.filters." + textCustomFieldName + "[4]").value("ends_with"),
                jsonPath("$.data.filters." + numberCustomFieldName + "[0]").value("equals"),
                jsonPath("$.data.filters." + numberCustomFieldName + "[1]").value("not_equals"),
                jsonPath("$.data.filters." + numberCustomFieldName + "[2]").value("greater_than"),
                jsonPath("$.data.filters." + numberCustomFieldName + "[3]").value("greater_than_equal_to"),
                jsonPath("$.data.filters." + numberCustomFieldName + "[4]").value("less_than"),
                jsonPath("$.data.filters." + numberCustomFieldName + "[5]").value("less_than_equal_to"),
                jsonPath("$.data.filters." + booleanCustomFieldName + "[0]").value("equals"),
                jsonPath("$.errors").isEmpty()
        );
    }
}

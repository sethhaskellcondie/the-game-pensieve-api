package com.sethhaskellcondie.thegamepensiveapi.domain.filter;

import com.sethhaskellcondie.thegamepensiveapi.domain.Keychain;
import com.sethhaskellcondie.thegamepensiveapi.domain.customfield.CustomField;
import com.sethhaskellcondie.thegamepensiveapi.domain.customfield.CustomFieldRepository;
import com.sethhaskellcondie.thegamepensiveapi.domain.customfield.CustomFieldValue;
import com.sethhaskellcondie.thegamepensiveapi.domain.system.System;
import com.sethhaskellcondie.thegamepensiveapi.domain.system.SystemRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.JdbcTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * This is part of an extensive set of tests on the GetWithFilters() call
 * it is called the same way for each entity in the EntityRepository.
 * <p>
 * The test suites have been broken up to reduce the complexity of the data set
 * that is being queried.
 * <p>
 * By the end every Filter in this system will have been tests against the
 * systems table. Then each entity will get that it works with the getWithFilters()
 * call. This is considered 'good enough' we don't need to test every filter on
 * every entity.
 */
@JdbcTest
@ActiveProfiles("test-container")
public class GetWithFiltersCustomFieldBooleanTests {

    @Autowired
    protected JdbcTemplate jdbcTemplate;
    protected SystemRepository systemRepository;
    protected CustomFieldRepository customFieldRepository;
    private final String customFieldName = "Favorite";

    @BeforeEach
    public void setUp() {
        systemRepository = new SystemRepository(jdbcTemplate);
        customFieldRepository = new CustomFieldRepository(jdbcTemplate);
    }

    @Test
    void testCustomFieldBooleanFilters() {
        final CustomField favoriteCustomField = customFieldRepository.insertCustomField(customFieldName, CustomField.TYPE_BOOLEAN, Keychain.SYSTEM_KEY);

        CustomFieldValue favoriteTrue = new CustomFieldValue(favoriteCustomField.id(), favoriteCustomField.name(), favoriteCustomField.type(), "true");
        CustomFieldValue favoriteFalse = new CustomFieldValue(favoriteCustomField.id(), favoriteCustomField.name(), favoriteCustomField.type(), "false");

        insertSystemData("System 1", List.of(favoriteTrue));
        insertSystemData("System 2", List.of(favoriteTrue));
        insertSystemData("System 3", List.of(favoriteFalse));

        testNoFilters(3);
        testCustomFieldBooleanTrue(2);
        testCustomFieldBooleanFalse(1);
    }

    void testNoFilters(int expectedResults) {
        final List<Filter> filters = List.of();

        final List<System> results = systemRepository.getWithFilters(filters);

        assertEquals(expectedResults, results.size(), "Wrong number of results returned when testing with no filters with custom field boolean data.");
    }

    void testCustomFieldBooleanTrue(int expectedResults) {
        final List<Filter> filters = List.of(
                new Filter(Keychain.SYSTEM_KEY, Filter.FIELD_TYPE_NUMBER, customFieldName, Filter.OPERATOR_EQUALS, "true", true) //wrong field type
        );

        final List<System> results = systemRepository.getWithFilters(filters);

        assertEquals(expectedResults, results.size(), "Wrong number of results returned when testing custom field filter boolean is true");
    }

    void testCustomFieldBooleanFalse(int expectedResults) {
        final List<Filter> filters = List.of(
                new Filter(Keychain.SYSTEM_KEY, Filter.FIELD_TYPE_BOOLEAN, customFieldName, Filter.OPERATOR_EQUALS, "false", true)
        );

        final List<System> results = systemRepository.getWithFilters(filters);

        assertEquals(expectedResults, results.size(), "Wrong number of results returned when testing custom field filter boolean is false");
    }

    private void insertSystemData(String name, List<CustomFieldValue> customFieldValues) {
        systemRepository.insert(new System(null, name, 1, false, null, null, null, customFieldValues));
    }
}

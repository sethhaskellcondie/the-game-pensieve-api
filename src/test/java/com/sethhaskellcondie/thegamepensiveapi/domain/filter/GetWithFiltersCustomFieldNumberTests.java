package com.sethhaskellcondie.thegamepensiveapi.domain.filter;

import com.sethhaskellcondie.thegamepensiveapi.domain.Keychain;
import com.sethhaskellcondie.thegamepensiveapi.domain.customfield.CustomField;
import com.sethhaskellcondie.thegamepensiveapi.domain.customfield.CustomFieldRepository;
import com.sethhaskellcondie.thegamepensiveapi.domain.customfield.CustomFieldRequestDto;
import com.sethhaskellcondie.thegamepensiveapi.domain.customfield.CustomFieldValue;
import com.sethhaskellcondie.thegamepensiveapi.domain.entity.system.System;
import com.sethhaskellcondie.thegamepensiveapi.domain.entity.system.SystemRepository;
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
@ActiveProfiles("test-db5")
public class GetWithFiltersCustomFieldNumberTests {

    @Autowired
    protected JdbcTemplate jdbcTemplate;
    protected SystemRepository systemRepository;
    protected CustomFieldRepository customFieldRepository;
    private final String customFieldName = "Release Date";

    @BeforeEach
    public void setUp() {
        systemRepository = new SystemRepository(jdbcTemplate);
        customFieldRepository = new CustomFieldRepository(jdbcTemplate);
    }

    @Test
    void testCustomFieldsNumberFilters() {
        final CustomField releaseDateCustomField = customFieldRepository.insertCustomField(new CustomFieldRequestDto(customFieldName, CustomField.TYPE_NUMBER, Keychain.SYSTEM_KEY));

        CustomFieldValue releaseDate1991 = new CustomFieldValue(releaseDateCustomField.id(), releaseDateCustomField.name(), releaseDateCustomField.type(), "1991");
        CustomFieldValue releaseDate1992 = new CustomFieldValue(releaseDateCustomField.id(), releaseDateCustomField.name(), releaseDateCustomField.type(), "1992");
        CustomFieldValue releaseDate1993 = new CustomFieldValue(releaseDateCustomField.id(), releaseDateCustomField.name(), releaseDateCustomField.type(), "1993");
        CustomFieldValue releaseDate1994 = new CustomFieldValue(releaseDateCustomField.id(), releaseDateCustomField.name(), releaseDateCustomField.type(), "1994");
        CustomFieldValue releaseDate1995 = new CustomFieldValue(releaseDateCustomField.id(), releaseDateCustomField.name(), releaseDateCustomField.type(), "1995");

        insertSystemData("System 1", List.of(releaseDate1991));
        insertSystemData("System 2", List.of(releaseDate1992));
        insertSystemData("System 3", List.of(releaseDate1992));
        insertSystemData("System 4", List.of(releaseDate1993));
        insertSystemData("System 5", List.of(releaseDate1994));
        insertSystemData("System 6", List.of(releaseDate1995));

        testNoFilters(6);
        testNumberEquals(1992, 2);
        testNumberNotEquals(1992, 4);
        testNumberGreaterThan(1993, 2);
        testNumberGreaterThanEqualTo(1993, 3);
        testNumberLessThan(1992, 1);
        testNumberLessThanEqualTo(1992, 3);
    }

    void testNoFilters(int expectedResults) {
        final List<Filter> filters = List.of();

        final List<System> results = systemRepository.getWithFilters(filters);

        assertEquals(expectedResults, results.size(), "Wrong number of results returned when testing with no filters on custom fields number data.");
    }

    void testNumberEquals(int number, int expectedResults) {
        final List<Filter> filters = List.of(
                new Filter(Keychain.SYSTEM_KEY, Filter.FIELD_TYPE_NUMBER, customFieldName, Filter.OPERATOR_EQUALS, Integer.toString(number), true)
        );

        final List<System> results = systemRepository.getWithFilters(filters);

        assertEquals(expectedResults, results.size(), "Wrong number of results returned when testing custom field number filter 'equals.'");
    }

    void testNumberNotEquals(int number, int expectedResults) {
        final List<Filter> filters = List.of(
                new Filter(Keychain.SYSTEM_KEY, Filter.FIELD_TYPE_NUMBER, customFieldName, Filter.OPERATOR_NOT_EQUALS, Integer.toString(number), true)
        );

        final List<System> results = systemRepository.getWithFilters(filters);

        assertEquals(expectedResults, results.size(), "Wrong number of results returned when testing custom field number filter 'not_equals.'");
    }

    void testNumberGreaterThan(int number, int expectedResults) {
        final List<Filter> filters = List.of(
                new Filter(Keychain.SYSTEM_KEY, Filter.FIELD_TYPE_NUMBER, customFieldName, Filter.OPERATOR_GREATER_THAN, Integer.toString(number), true)
        );

        final List<System> results = systemRepository.getWithFilters(filters);

        assertEquals(expectedResults, results.size(), "Wrong number of results returned when testing custom field number filter 'greater_than.'");
    }

    void testNumberGreaterThanEqualTo(int number, int expectedResults) {
        final List<Filter> filters = List.of(
                new Filter(Keychain.SYSTEM_KEY, Filter.FIELD_TYPE_NUMBER, customFieldName, Filter.OPERATOR_GREATER_THAN_EQUAL_TO, Integer.toString(number), true)
        );

        final List<System> results = systemRepository.getWithFilters(filters);

        assertEquals(expectedResults, results.size(), "Wrong number of results returned when testing custom field number filter 'greater_than_equal_to.'");
    }

    void testNumberLessThan(int number, int expectedResults) {
        final List<Filter> filters = List.of(
                new Filter(Keychain.SYSTEM_KEY, Filter.FIELD_TYPE_NUMBER, customFieldName, Filter.OPERATOR_LESS_THAN, Integer.toString(number), true)
        );

        final List<System> results = systemRepository.getWithFilters(filters);

        assertEquals(expectedResults, results.size(), "Wrong number of results returned when testing custom field number filter 'less_than.'");
    }

    void testNumberLessThanEqualTo(int number, int expectedResults) {
        final List<Filter> filters = List.of(
                new Filter(Keychain.SYSTEM_KEY, Filter.FIELD_TYPE_NUMBER, customFieldName, Filter.OPERATOR_LESS_THAN_EQUAL_TO, Integer.toString(number), true)
        );

        final List<System> results = systemRepository.getWithFilters(filters);

        assertEquals(expectedResults, results.size(), "Wrong number of results returned when testing custom field number filter 'less_than_equal_to.'");
    }

    private void insertSystemData(String name, List<CustomFieldValue> customFieldValues) {
        systemRepository.insert(new System(null, name, 1, false, null, null, null, customFieldValues));
    }
}

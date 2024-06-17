package com.sethhaskellcondie.thegamepensiveapi.domain.filter;

import com.sethhaskellcondie.thegamepensiveapi.domain.Keychain;
import com.sethhaskellcondie.thegamepensiveapi.domain.system.System;
import com.sethhaskellcondie.thegamepensiveapi.domain.system.SystemRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.JdbcTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.ArrayList;
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
@ActiveProfiles("test-db1")
public class GetWithFiltersNumberTests {

    @Autowired
    protected JdbcTemplate jdbcTemplate;
    protected SystemRepository systemRepository;

    @BeforeEach
    public void setUp() {
        systemRepository = new SystemRepository(jdbcTemplate);
    }

    @Test
    void testNumberFilters() {
        insertSystemData("Master System", 1, true);
        insertSystemData("Genesis", 2, true);
        insertSystemData("32X", 2, true);
        insertSystemData("Game Gear", 3, true);
        insertSystemData("Saturn", 4, true);
        insertSystemData("Dreamcast", 5, true);

        testNoFilters(6);
        testNumberEquals(2, 2);
        testNumberNotEquals(2, 4);
        testNumberGreaterThan(3, 2);
        testNumberGreaterThanEqualTo(3, 3);
        testNumberLessThan(2, 1);
        testNumberLessThanEqualTo(2, 3);
    }

    void testNoFilters(int expectedResults) {
        final List<Filter> filters = List.of();

        final List<System> results = systemRepository.getWithFilters(filters);

        assertEquals(expectedResults, results.size(), "Wrong number of results returned when testing with no filters on the Number data.");
    }

    void testNumberEquals(int number, int expectedResults) {
        final List<Filter> filters = List.of(
                new Filter(Keychain.SYSTEM_KEY, Filter.FIELD_TYPE_NUMBER, "generation", Filter.OPERATOR_EQUALS, Integer.toString(number), false)
        );

        final List<System> results = systemRepository.getWithFilters(filters);

        assertEquals(expectedResults, results.size(), "Wrong number of results returned when testing number filter 'equals.'");
    }

    void testNumberNotEquals(int number, int expectedResults) {
        final List<Filter> filters = List.of(
                new Filter(Keychain.SYSTEM_KEY, Filter.FIELD_TYPE_NUMBER, "generation", Filter.OPERATOR_NOT_EQUALS, Integer.toString(number), false)
        );

        final List<System> results = systemRepository.getWithFilters(filters);

        assertEquals(expectedResults, results.size(), "Wrong number of results returned when testing number filter 'not_equals.'");
    }

    void testNumberGreaterThan(int number, int expectedResults) {
        final List<Filter> filters = List.of(
                new Filter(Keychain.SYSTEM_KEY, Filter.FIELD_TYPE_NUMBER, "generation", Filter.OPERATOR_GREATER_THAN, Integer.toString(number), false)
        );

        final List<System> results = systemRepository.getWithFilters(filters);

        assertEquals(expectedResults, results.size(), "Wrong number of results returned when testing number filter 'greater_than.'");
    }

    void testNumberGreaterThanEqualTo(int number, int expectedResults) {
        final List<Filter> filters = List.of(
                new Filter(Keychain.SYSTEM_KEY, Filter.FIELD_TYPE_NUMBER, "generation", Filter.OPERATOR_GREATER_THAN_EQUAL_TO, Integer.toString(number), false)
        );

        final List<System> results = systemRepository.getWithFilters(filters);

        assertEquals(expectedResults, results.size(), "Wrong number of results returned when testing number filter 'greater_than_equal_to.'");
    }

    void testNumberLessThan(int number, int expectedResults) {
        final List<Filter> filters = List.of(
                new Filter(Keychain.SYSTEM_KEY, Filter.FIELD_TYPE_NUMBER, "generation", Filter.OPERATOR_LESS_THAN, Integer.toString(number), false)
        );

        final List<System> results = systemRepository.getWithFilters(filters);

        assertEquals(expectedResults, results.size(), "Wrong number of results returned when testing number filter 'less_than.'");
    }

    void testNumberLessThanEqualTo(int number, int expectedResults) {
        final List<Filter> filters = List.of(
                new Filter(Keychain.SYSTEM_KEY, Filter.FIELD_TYPE_NUMBER, "generation", Filter.OPERATOR_LESS_THAN_EQUAL_TO, Integer.toString(number), false)
        );

        final List<System> results = systemRepository.getWithFilters(filters);

        assertEquals(expectedResults, results.size(), "Wrong number of results returned when testing number filter 'less_than_equal_to.'");
    }

    private void insertSystemData(String name, int generation, boolean handheld) {
        systemRepository.insert(new System(null, name, generation, handheld, null, null, null, new ArrayList<>()));
    }
}

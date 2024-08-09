package com.sethhaskellcondie.thegamepensiveapi.domain.filter;

import com.sethhaskellcondie.thegamepensiveapi.domain.Keychain;
import com.sethhaskellcondie.thegamepensiveapi.domain.entity.system.System;
import com.sethhaskellcondie.thegamepensiveapi.domain.entity.system.SystemRepository;
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
@ActiveProfiles("filter-tests2")
public class GetWithFiltersBooleanAndTimeTests {

    @Autowired
    protected JdbcTemplate jdbcTemplate;
    protected SystemRepository systemRepository;

    @BeforeEach
    public void setUp() {
        systemRepository = new SystemRepository(jdbcTemplate);
    }

    @Test
    void testBooleanAndTimeFilters() {
        insertSystemData("NES", 3, false);
        insertSystemData("SNES", 4, false);
        insertSystemData("Game Boy", 4, true);

        testNoFilters(3);
        testBooleanEqualsTrue(1);
        testBooleanEqualsFalse(2);
        testSinceFilter(3);
        testBeforeFilter(3);
    }

    void testNoFilters(int expectedResults) {
        final List<Filter> filters = List.of();

        final List<System> results = systemRepository.getWithFilters(filters);

        assertEquals(expectedResults, results.size(), "Wrong number of results returned when testing with no filters on the Boolean and Time data.");
    }

    void testBooleanEqualsTrue(int expectedResults) {
        final List<Filter> filters = List.of(
                new Filter(Keychain.SYSTEM_KEY, Filter.FIELD_TYPE_BOOLEAN, "handheld", Filter.OPERATOR_EQUALS, "true", false)
        );

        final List<System> results = systemRepository.getWithFilters(filters);

        assertEquals(expectedResults, results.size(), "Wrong number of results returned when testing boolean filter equals true.");
    }

    void testBooleanEqualsFalse(int expectedResults) {
        final List<Filter> filters = List.of(
                new Filter(Keychain.SYSTEM_KEY, Filter.FIELD_TYPE_BOOLEAN, "handheld", Filter.OPERATOR_EQUALS, "false", false)
        );

        final List<System> results = systemRepository.getWithFilters(filters);

        assertEquals(expectedResults, results.size(), "Wrong number of results returned when testing boolean filter equals false.");
    }

    void testSinceFilter(int expectedResults) {
        final List<Filter> filters = List.of(
                new Filter(Keychain.SYSTEM_KEY, Filter.FIELD_TYPE_TIME, "created_at", Filter.OPERATOR_SINCE, "2012-05-01 00:00:00", false)
        );

        final List<System> results = systemRepository.getWithFilters(filters);

        assertEquals(expectedResults, results.size(), "Wrong number of results returned when testing the since filter.");
    }

    void testBeforeFilter(int expectedResults) {
        final List<Filter> filters = List.of(
                new Filter(Keychain.SYSTEM_KEY, Filter.FIELD_TYPE_TIME, "updated_at", Filter.OPERATOR_BEFORE, "3024-05-01 00:00:00", false)
        );

        final List<System> results = systemRepository.getWithFilters(filters);

        assertEquals(expectedResults, results.size(), "Wrong number of results returned when testing the before filter.");
    }

    private void insertSystemData(String name, int generation, boolean handheld) {
        systemRepository.insert(new System(null, name, generation, handheld, null, null, null, new ArrayList<>()));
    }
}

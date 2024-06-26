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
@ActiveProfiles("test-db6")
public class GetWithFiltersTextPaginationAndSortTests {

    @Autowired
    protected JdbcTemplate jdbcTemplate;
    protected SystemRepository systemRepository;

    @BeforeEach
    public void setUp() {
        systemRepository = new SystemRepository(jdbcTemplate);
    }

    @Test
    void testTextAndPaginationFilters() {
        //In this data set the generation is used for the sort tests, in the other GetWithFiltersNumberFilters test the generation is used for number filter tests.
        //The systems start inserting with generation 5 so that the id and the generation will not match then we know that the sort is working correctly.
        System superGameBoy = insertSystemData("Super Game Boy", 5, false);
        System nes = insertSystemData("Nintendo Entertainment System", 1, false);
        System snes = insertSystemData("Super Nintendo Entertainment System", 2, false);
        System n64 = insertSystemData("Nintendo 64", 3, false);
        System gameBoy = insertSystemData("Game Boy", 4, false);

        testNoFilters(5);

        testTextFilterNameEquals("Nintendo 64", 1);
        testTextFilterNameNotEquals("Nintendo 64", 4);
        testTextFilterNameContains("Nintendo", 3);
        testTextFilterNameStartsWith("Super", 2);
        testTextFilterNameEndsWith("Boy", 2);

        testPaginationFilterLimit(2, List.of(superGameBoy, nes));
        testPaginationFilterOffsetAndLimit(1, 4, List.of(nes, snes, n64, gameBoy));

        //All sorting is done on generation for these tests
        testPaginationFilterOffsetLimitAndSortAsc(2, 3, List.of(n64, gameBoy, superGameBoy));
        testPaginationFilterOffsetLimitAndSortDesc(2, 3, List.of(n64, snes, nes));
        //Testing the order filters is the same as offset, limit, and sort, instead the filters are mixed up before getting passed to getWithFilters() and they are sorted correctly before the query.
        testOrderFilters(2, 3, List.of(n64, snes, nes));
    }

    void testNoFilters(int expectedResults) {
        final List<Filter> filters = List.of();

        final List<System> results = systemRepository.getWithFilters(filters);

        assertEquals(expectedResults, results.size(), "Wrong number of results returned when testing results with no filters on the Text and Pagination data.");
    }

    void testTextFilterNameEquals(String name, int expectedResults) {
        final List<Filter> filters = List.of(
                new Filter(Keychain.SYSTEM_KEY, Filter.FIELD_TYPE_TEXT, "name", Filter.OPERATOR_EQUALS, name, false)
        );

        final List<System> results = systemRepository.getWithFilters(filters);

        assertEquals(expectedResults, results.size(), "Wrong number of results returned when testing text filter name equals.");
    }

    void testTextFilterNameNotEquals(String name, int expectedResults) {
        final List<Filter> filters = List.of(
                new Filter(Keychain.SYSTEM_KEY, Filter.FIELD_TYPE_TEXT, "name", Filter.OPERATOR_NOT_EQUALS, name, false)
        );

        final List<System> results = systemRepository.getWithFilters(filters);

        assertEquals(expectedResults, results.size(), "Wrong number of results returned when testing text filter name not_equals.");
    }

    void testTextFilterNameContains(String name, int expectedResults) {
        final List<Filter> filters = List.of(
                new Filter(Keychain.SYSTEM_KEY, Filter.FIELD_TYPE_TEXT, "name", Filter.OPERATOR_CONTAINS, name, false)
        );

        final List<System> results = systemRepository.getWithFilters(filters);

        assertEquals(expectedResults, results.size(), "Wrong number of results returned when testing text filter name contains.");
    }

    void testTextFilterNameStartsWith(String name, int expectedResults) {
        final List<Filter> filters = List.of(
                new Filter(Keychain.SYSTEM_KEY, Filter.FIELD_TYPE_TEXT, "name", Filter.OPERATOR_STARTS_WITH, name, false)
        );

        final List<System> results = systemRepository.getWithFilters(filters);

        assertEquals(expectedResults, results.size(), "Wrong number of results returned when testing text filter name starts_with.");
    }

    void testTextFilterNameEndsWith(String name, int expectedResults) {
        final List<Filter> filters = List.of(
                new Filter(Keychain.SYSTEM_KEY, Filter.FIELD_TYPE_TEXT, "name", Filter.OPERATOR_ENDS_WITH, name, false)
        );

        final List<System> results = systemRepository.getWithFilters(filters);

        assertEquals(expectedResults, results.size(), "Wrong number of results returned when testing text filter name ends_with.");
    }

    void testPaginationFilterLimit(int limitResults, List<System> expectedResults) {
        final List<Filter> filters = List.of(
                new Filter(Keychain.SYSTEM_KEY, Filter.FIELD_TYPE_PAGINATION, Filter.PAGINATION_FIELDS, Filter.OPERATOR_LIMIT, Integer.toString(limitResults), false)
        );

        final List<System> results = systemRepository.getWithFilters(filters);

        assertEquals(limitResults, results.size(), "Wrong number of results returned when testing pagination filter limit.");
        assertEquals(expectedResults.size(), results.size(), "Wrong number of results returned when testing pagination filter limit.");
        for (int i = 0; i < results.size(); i++) {
            assertEquals(expectedResults.get(i), results.get(i));
        }
    }

    void testPaginationFilterOffsetAndLimit(int offset, int limit, List<System> expectedResults) {
        final List<Filter> filters = List.of(
                new Filter(Keychain.SYSTEM_KEY, Filter.FIELD_TYPE_PAGINATION, Filter.PAGINATION_FIELDS, Filter.OPERATOR_OFFSET, Integer.toString(offset), false),
                new Filter(Keychain.SYSTEM_KEY, Filter.FIELD_TYPE_PAGINATION, Filter.PAGINATION_FIELDS, Filter.OPERATOR_LIMIT, Integer.toString(limit), false)
        );

        final List<System> results = systemRepository.getWithFilters(filters);

        assertEquals(limit, results.size(), "Wrong number of results returned when testing pagination filters offset and limit.");
        assertEquals(expectedResults.size(), results.size(), "Wrong number of results returned when testing pagination filters offset and limit.");
        for (int i = 0; i < results.size(); i++) {
            assertEquals(expectedResults.get(i), results.get(i));
        }
    }

    void testPaginationFilterOffsetLimitAndSortAsc(int offset, int limit, List<System> expectedResults) {
        final List<Filter> filters = List.of(
                new Filter(Keychain.SYSTEM_KEY, Filter.FIELD_TYPE_PAGINATION, Filter.PAGINATION_FIELDS, Filter.OPERATOR_OFFSET, Integer.toString(offset), false),
                new Filter(Keychain.SYSTEM_KEY, Filter.FIELD_TYPE_PAGINATION, Filter.PAGINATION_FIELDS, Filter.OPERATOR_LIMIT, Integer.toString(limit), false),
                new Filter(Keychain.SYSTEM_KEY, Filter.FIELD_TYPE_NUMBER, "generation", Filter.OPERATOR_ORDER_BY, "unused", false)
        );

        final List<System> results = systemRepository.getWithFilters(filters);

        assertEquals(limit, results.size(), "Wrong number of results returned when testing pagination filters offset, limit and sort ascending.");
        assertEquals(expectedResults.size(), results.size(), "Wrong number of results returned when testing pagination filters offset, limit and sort ascending.");
        for (int i = 0; i < results.size(); i++) {
            assertEquals(expectedResults.get(i), results.get(i));
        }
    }

    void testPaginationFilterOffsetLimitAndSortDesc(int offset, int limit, List<System> expectedResults) {
        final List<Filter> filters = List.of(
                new Filter(Keychain.SYSTEM_KEY, Filter.FIELD_TYPE_PAGINATION, Filter.PAGINATION_FIELDS, Filter.OPERATOR_OFFSET, Integer.toString(offset), false),
                new Filter(Keychain.SYSTEM_KEY, Filter.FIELD_TYPE_PAGINATION, Filter.PAGINATION_FIELDS, Filter.OPERATOR_LIMIT, Integer.toString(limit), false),
                new Filter(Keychain.SYSTEM_KEY, Filter.FIELD_TYPE_NUMBER, "generation", Filter.OPERATOR_ORDER_BY_DESC, "unused", false)
        );

        final List<System> results = systemRepository.getWithFilters(filters);

        assertEquals(limit, results.size(), "Wrong number of results returned when testing pagination filters offset, limit and sort ascending.");
        assertEquals(expectedResults.size(), results.size(), "Wrong number of results returned when testing pagination filters offset, limit and sort ascending.");
        for (int i = 0; i < results.size(); i++) {
            assertEquals(expectedResults.get(i), results.get(i));
        }
    }

    void testOrderFilters(int offset, int limit, List<System> expectedResults) {
        //pass in the order of the filters mixed up to make sure that they are sorted correctly before the query
        final List<Filter> filters = List.of(
                new Filter(Keychain.SYSTEM_KEY, Filter.FIELD_TYPE_PAGINATION, Filter.PAGINATION_FIELDS, Filter.OPERATOR_LIMIT, Integer.toString(limit), false),
                new Filter(Keychain.SYSTEM_KEY, Filter.FIELD_TYPE_NUMBER, "generation", Filter.OPERATOR_ORDER_BY_DESC, "unused", false),
                new Filter(Keychain.SYSTEM_KEY, Filter.FIELD_TYPE_PAGINATION, Filter.PAGINATION_FIELDS, Filter.OPERATOR_OFFSET, Integer.toString(offset), false)
        );

        final List<System> results = systemRepository.getWithFilters(filters);

        assertEquals(limit, results.size(), "Wrong number of results returned when testing the order of the filters.");
        assertEquals(expectedResults.size(), results.size(), "Wrong number of results returned when testing the order of the filters.");
        for (int i = 0; i < results.size(); i++) {
            assertEquals(expectedResults.get(i), results.get(i));
        }
    }

    private System insertSystemData(String name, int generation, boolean handheld) {
        return systemRepository.insert(new System(null, name, generation, handheld, null, null, null, new ArrayList<>()));
    }
}

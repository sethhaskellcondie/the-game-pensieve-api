package com.sethhaskellcondie.thegamepensiveapi.domain.filter;

import com.sethhaskellcondie.thegamepensiveapi.domain.Keychain;
import com.sethhaskellcondie.thegamepensiveapi.domain.system.SystemRepository;
import com.sethhaskellcondie.thegamepensiveapi.exceptions.ExceptionInvalidFilter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.JdbcTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * This is part of an extensive set of tests on the GetWithFilters() call
 * it is called the same way for each entity in the EntityRepository.
 * <p>
 * This test suite only includes the validation tests. Other test suites
 * will test the results after running the different queries.
 */
@JdbcTest
@ActiveProfiles("test-container")
public class GetWithFiltersValidationTests {

    @Autowired
    protected JdbcTemplate jdbcTemplate;
    protected SystemRepository systemRepository;

    @BeforeEach
    public void setUp() {
        systemRepository = new SystemRepository(jdbcTemplate);
    }

    @Test
    void validateAndOrderFilters_TwoFiltersMultipleErrors_ThrowExceptionWithMultipleErrors() {
        final List<Filter> filters = List.of(
                new Filter(Keychain.SYSTEM_KEY,
                        Filter.FIELD_TYPE_TEXT, //will throw one error for not matching the computed field type
                        "missingField",         //will throw two errors for not being allowed with system
                        Filter.OPERATOR_EQUALS,
                        "operand with ; not allowed",   //will throw one error for including a semicolon in the operand
                        false),
                new Filter(Keychain.SYSTEM_KEY,
                        Filter.FIELD_TYPE_TEXT, //will throw one error for not matching the computed field type
                        "anotherMissingField",  //will throw two errors for not being allowed with system
                        Filter.OPERATOR_STARTS_WITH,
                        "keyword select is not allowed", //will throw one error for including the word 'select' in the operand
                        false)
        );

        ExceptionInvalidFilter exception = assertThrows(ExceptionInvalidFilter.class, () -> systemRepository.getWithFilters(filters));

        assertEquals(8, exception.getMessages().size(), "Unexpected number of errors thrown in ExceptionInvalidFilter");
    }

    @Test
    void validateAndOrderFilters_containsAllBlacklistedWords_ThrowsMultipleErrors() {
        final String blacklistedWords = String.join(",", Filter.getBlacklistedWords());
        final List<Filter> filters = List.of(
                new Filter(Keychain.SYSTEM_KEY, Filter.FIELD_TYPE_TEXT, "name", Filter.OPERATOR_EQUALS, blacklistedWords, false)
        );

        ExceptionInvalidFilter exception = assertThrows(ExceptionInvalidFilter.class, () -> systemRepository.getWithFilters(filters));

        assertEquals(Filter.getBlacklistedWords().size(), exception.getMessages().size(), "Unexpected number of errors returned while testing blacklisted words in filters.");
    }

    @Test
    void validateAndOrderFilters_TooManyOrderByLimitAndOffsetFilters_ReturnExceptionWithMultipleErrors() {
        final List<Filter> filters = List.of(
                new Filter(Keychain.SYSTEM_KEY, Filter.FIELD_TYPE_NUMBER, "generation", Filter.OPERATOR_ORDER_BY, "asc", false),
                new Filter(Keychain.SYSTEM_KEY, Filter.FIELD_TYPE_NUMBER, "generation", Filter.OPERATOR_ORDER_BY_DESC, "desc", false), //only one order by filter is allowed
                new Filter(Keychain.SYSTEM_KEY, Filter.FIELD_TYPE_PAGINATION, Filter.PAGINATION_FIELDS, Filter.OPERATOR_LIMIT, "3", false),
                new Filter(Keychain.SYSTEM_KEY, Filter.FIELD_TYPE_PAGINATION, Filter.PAGINATION_FIELDS, Filter.OPERATOR_LIMIT, "4", false), //only one limit filter is allowed
                new Filter(Keychain.SYSTEM_KEY, Filter.FIELD_TYPE_PAGINATION, Filter.PAGINATION_FIELDS, Filter.OPERATOR_OFFSET, "1", false),
                new Filter(Keychain.SYSTEM_KEY, Filter.FIELD_TYPE_PAGINATION, Filter.PAGINATION_FIELDS, Filter.OPERATOR_OFFSET, "2", false) //only one offset filter is allowed
        );

        ExceptionInvalidFilter exception = assertThrows(ExceptionInvalidFilter.class, () -> systemRepository.getWithFilters(filters));

        assertEquals(3, exception.getMessages().size(), "Unexpected number of errors returned while testing the allowed number of 'order_by,' 'limit,' and 'offset' filters.");
    }

    @Test
    void validateAndOrderFilters_ErrorOnCastingBooleanAndInteger_ThrowException() {
        final List<Filter> filters = List.of(
                new Filter(Keychain.SYSTEM_KEY, Filter.FIELD_TYPE_NUMBER, "generation", Filter.OPERATOR_GREATER_THAN, "not_a_number", false),
                new Filter(Keychain.SYSTEM_KEY, Filter.FIELD_TYPE_PAGINATION, Filter.PAGINATION_FIELDS, Filter.OPERATOR_LIMIT, "also_not_a_number", false),
                new Filter(Keychain.SYSTEM_KEY, Filter.FIELD_TYPE_BOOLEAN, "handheld", Filter.OPERATOR_EQUALS, "not_a_boolean", false)
        );

        ExceptionInvalidFilter exception = assertThrows(ExceptionInvalidFilter.class, () -> systemRepository.getWithFilters(filters));

        assertEquals(3, exception.getMessages().size(), "Unexpected number of errors returned while testing the casting of number, pagination, and boolean filters.");
    }

    @Test
    void validateAndOrderFilters_TimeFiltersIncorrectlyFormatted_ThrowException() {
        final List<Filter> filters = List.of(
                new Filter(Keychain.SYSTEM_KEY, Filter.FIELD_TYPE_TIME, "created_at", Filter.OPERATOR_SINCE, "2024-05-32 00:00:00", false), //day too big
                new Filter(Keychain.SYSTEM_KEY, Filter.FIELD_TYPE_TIME, "updated_at", Filter.OPERATOR_BEFORE, "2024-13-06 00:00:00", false) //month too big
        );

        ExceptionInvalidFilter exception = assertThrows(ExceptionInvalidFilter.class, () -> systemRepository.getWithFilters(filters));

        assertEquals(2, exception.getMessages().size(), "Unexpected number of errors returned while testing time filter formatting.");
    }

    @Test
    void validateAndOrderFilters_MissingLimitWhileOffSetIncluded_ThrowException() {
        final List<Filter> filters = List.of(
                new Filter(Keychain.SYSTEM_KEY, Filter.FIELD_TYPE_NUMBER, "generation", Filter.OPERATOR_ORDER_BY, "asc", false),
                new Filter(Keychain.SYSTEM_KEY, Filter.FIELD_TYPE_PAGINATION, Filter.PAGINATION_FIELDS, Filter.OPERATOR_OFFSET, "2", false)
        );
        ExceptionInvalidFilter exception = assertThrows(ExceptionInvalidFilter.class, () -> systemRepository.getWithFilters(filters));

        assertEquals(1, exception.getMessages().size(), "Unexpected errors returned when testing that an offset filter must include one limit filter.");
    }
}

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
import static org.junit.jupiter.api.Assertions.fail;

/**
 * This is an extensive set of tests on the GetWithFilters() call
 * it is called the same way for each entity in the EntityRepository.
 * Every filter will be tested here through the Systems table then there will
 * be a light tests to make sure that this call works with each entity in
 * those tests. Any variations of the default will be tested in those tests.
 * This is considered "good enough" we don't need to test every filter
 * with every entity.
 */
@JdbcTest
@ActiveProfiles("test-container")
public class GetWithFiltersTests {

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

    @Test
    void formatWhereStatementsAndFormatOperands_StringFilters_ValidSql() {
        //TODO refactor this
        final String expectedSql = "SELECT * FROM systems WHERE 1 = 1 AND systems.name = ? AND systems.name <> ? AND systems.name LIKE ? AND systems.name LIKE ? AND systems.name LIKE ?";
        final List<Object> expectedOperands = List.of(
                "SuperMegaForceWin",
                "NotMe",
                "%Force%",
                "SuperMega%",
                "%Win"
        );
        final List<Filter> filters = List.of(
                new Filter("system", "text", "name", Filter.OPERATOR_EQUALS, "SuperMegaForceWin", false),
                new Filter("system", "text", "name", Filter.OPERATOR_NOT_EQUALS, "NotMe", false),
                new Filter("system", "text", "name", Filter.OPERATOR_CONTAINS, "Force", false),
                new Filter("system", "text", "name", Filter.OPERATOR_STARTS_WITH, "SuperMega", false),
                new Filter("system", "text", "name", Filter.OPERATOR_ENDS_WITH, "Win", false)
        );
        try {
            Filter.validateAndOrderFilters(filters);
        } catch (ExceptionInvalidFilter exception) {
            fail("ExceptionInvalidFilter caught when it shouldn't have been while testing Pagination filters.");
        }
        final List<String> whereStatements = Filter.formatWhereStatements(filters);
        final List<Object> operands = Filter.formatOperands(filters);
        final String sql = getBaseQuery() + String.join("", whereStatements);
        assertEquals(expectedSql, sql);
        assertEquals(expectedOperands, operands);
    }

    @Test
    void formatWhereStatementsAndFormatOperands_NumberFilters_ValidSql() {
        //TODO refactor this
        final String expectedSql = "SELECT * FROM systems WHERE 1 = 1 AND systems.generation = ? AND systems.generation <> ? AND systems.generation > ? AND systems.generation >= ? " +
                "AND systems.generation < ? AND systems.generation <= ?";
        final List<Object> expectedOperands = List.of(3, 4, 5, 6, 7, 8);
        final List<Filter> filters = List.of(
                new Filter("system", "number", "generation", Filter.OPERATOR_EQUALS, "3", false),
                new Filter("system", "number", "generation", Filter.OPERATOR_NOT_EQUALS, "4", false),
                new Filter("system", "number", "generation", Filter.OPERATOR_GREATER_THAN, "5", false),
                new Filter("system", "number", "generation", Filter.OPERATOR_GREATER_THAN_EQUAL_TO, "6", false),
                new Filter("system", "number", "generation", Filter.OPERATOR_LESS_THAN, "7", false),
                new Filter("system", "number", "generation", Filter.OPERATOR_LESS_THAN_EQUAL_TO, "8", false)
        );
        try {
            Filter.validateAndOrderFilters(filters);
        } catch (ExceptionInvalidFilter exception) {
            fail("ExceptionInvalidFilter caught when it shouldn't have been while testing number filters.");
        }
        final List<String> whereStatements = Filter.formatWhereStatements(filters);
        final List<Object> operands = Filter.formatOperands(filters);
        final String sql = getBaseQuery() + String.join("", whereStatements);
        assertEquals(expectedSql, sql);
        assertEquals(expectedOperands, operands);
    }

    @Test
    void formatWhereStatementsAndFormatOperands_BooleanFilters_ValidSql() {
        //TODO refactor this
        //This SQL wouldn't return any results, because we are testing the same field with true and false
        //perhaps I will update this when another resource has two different boolean fields
        final String expectedSql = "SELECT * FROM systems WHERE 1 = 1 AND systems.handheld = ? AND systems.handheld = ?";
        final List<Object> expectedOperands = List.of(true, false);
        final List<Filter> filters = List.of(
                new Filter("system", "boolean", "handheld", Filter.OPERATOR_EQUALS, "true", false),
                new Filter("system", "boolean", "handheld", Filter.OPERATOR_EQUALS, "false", false)
        );
        try {
            Filter.validateAndOrderFilters(filters);
        } catch (ExceptionInvalidFilter exception) {
            fail("ExceptionInvalidFilter caught when it shouldn't have been while testing boolean filters.");
        }
        final List<String> whereStatements = Filter.formatWhereStatements(filters);
        final List<Object> operands = Filter.formatOperands(filters);
        final String sql = getBaseQuery() + String.join("", whereStatements);
        assertEquals(expectedSql, sql);
        assertEquals(expectedOperands, operands);
    }

    @Test
    void formatWhereStatementsAndFormatOperands_PaginationFilters_ValidSql() {
        //TODO refactor this
        final String expectedSql = "SELECT * FROM systems WHERE 1 = 1 ORDER BY systems.generation ASC LIMIT ? OFFSET ?";
        final List<Object> expectedOperands = List.of(5, 1);
        final List<Filter> filters = List.of(
                new Filter("system", "number", "generation", Filter.OPERATOR_ORDER_BY, "asc", false),
                new Filter("system", "pagination", "pagination_fields", Filter.OPERATOR_LIMIT, "5", false),
                new Filter("system", "pagination", "pagination_fields", Filter.OPERATOR_OFFSET, "1", false)
        );
        try {
            Filter.validateAndOrderFilters(filters);
        } catch (ExceptionInvalidFilter exception) {
            fail("ExceptionInvalidFilter caught when it shouldn't have been while testing pagination filters.");
        }
        final List<String> whereStatements = Filter.formatWhereStatements(filters);
        final List<Object> operands = Filter.formatOperands(filters);
        final String sql = getBaseQuery() + String.join("", whereStatements);
        assertEquals(expectedSql, sql);
        assertEquals(expectedOperands, operands);
    }

    @Test
    void formatWhereStatementsAndFormatOperands_TimeFilters_ValidSql() {
        //TODO refactor this
        final String expectedSql =
                "SELECT * FROM systems WHERE 1 = 1 AND systems.created_at >= TO_TIMESTAMP( ? , 'yyyy-mm-dd hh:mm:ss') AND systems.updated_at <= TO_TIMESTAMP( ? , 'yyyy-mm-dd hh:mm:ss') " +
                        "ORDER BY systems.generation DESC";
        final List<Object> expectedOperands = List.of("2024-05-06 00:00:00", "2024-05-04 00:00:00");
        final List<Filter> filters = List.of(
                new Filter("system", "time", "created_at", Filter.OPERATOR_SINCE, "2024-05-06 00:00:00", false),
                new Filter("system", "time", "updated_at", Filter.OPERATOR_BEFORE, "2024-05-04 00:00:00", false),
                new Filter("system", "number", "generation", Filter.OPERATOR_ORDER_BY_DESC, "desc", false)
        );
        try {
            Filter.validateAndOrderFilters(filters);
        } catch (ExceptionInvalidFilter exception) {
            fail("ExceptionInvalidFilter caught when it shouldn't have been while testing time filters.");
        }
        final List<String> whereStatements = Filter.formatWhereStatements(filters);
        final List<Object> operands = Filter.formatOperands(filters);
        final String sql = getBaseQuery() + String.join("", whereStatements);
        assertEquals(expectedSql, sql);
        assertEquals(expectedOperands, operands);
    }

    private String getBaseQuery() {
        return "SELECT * FROM systems WHERE 1 = 1";
    }
}

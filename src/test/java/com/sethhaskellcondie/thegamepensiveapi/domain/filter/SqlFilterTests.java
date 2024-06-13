package com.sethhaskellcondie.thegamepensiveapi.domain.filter;

import com.sethhaskellcondie.thegamepensiveapi.exceptions.ExceptionInvalidFilter;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.jdbc.JdbcTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * This will test the code in the Filter.java class of validating and changing filters
 * into the proper SQL clauses. The Response to a GET filters request is tested in FilterTests.java
 * and the proper error handling for invalid filters is tested in by the different (Entity)Test.java files.
 * This is considered "good enough" we don't need to test every filter on every entity.
 */
@JdbcTest
@ActiveProfiles("test-container")
public class SqlFilterTests {

    //TODO refactor this

    @Test
    void validateAndOrderFilters_TwoFiltersSixErrors_ThrowExceptionWithSixMessages() {
        final List<Filter> filters = List.of(
                new Filter("system", "text", "missingField", Filter.OPERATOR_EQUALS, "not allowed ;", false),
                new Filter("system", "text", "anotherMissingField", Filter.OPERATOR_STARTS_WITH, "keyword select is not allowed", false)
        );
        boolean exceptionCaught = false;
        try {
            Filter.validateAndOrderFilters(filters);
        } catch (ExceptionInvalidFilter exception) {
            exceptionCaught = true;
            assertEquals(8, exception.getMessages().size(), "Malformed ExceptionInvalidFilter thrown while testing invalid filters.");
        }
        if (!exceptionCaught) {
            fail("ExceptionInvalidFilter not caught when it should have been while testing invalid filters.");
        }
    }

    @Test
    void validateAndOrderFilters_containsAllBlacklistedWords_ThrowsMultipleErrors() {
        final String blacklistedWords = String.join(",", Filter.getBlacklistedWords());
        final List<Filter> filters = List.of(
                new Filter("system", "text", "name", Filter.OPERATOR_EQUALS, blacklistedWords, false)
        );
        boolean exceptionCaught = false;
        try {
            Filter.validateAndOrderFilters(filters);
        } catch (ExceptionInvalidFilter exception) {
            exceptionCaught = true;
            assertEquals(Filter.getBlacklistedWords().size(), exception.getMessages().size(), "Malformed ExceptionInvalidFilter thrown while testing invalid filters.");
        }
        if (!exceptionCaught) {
            fail("ExceptionInvalidFilter not caught when it should have been while testing blacklisted words in filters.");
        }
    }

    @Test
    void validateAndOrderFilters_FiltersInWrongOrder_ReturnedInTheCorrectOrder() {
        Filter whereFilter = new Filter("system", "text", "name", Filter.OPERATOR_CONTAINS, "Force", false);
        Filter orderByFilter = new Filter("system", "number", "generation", Filter.OPERATOR_ORDER_BY, "asc", false);
        Filter limitFilter = new Filter("system", "pagination", "pagination_fields", Filter.OPERATOR_LIMIT, "3", false);
        Filter offsetFilter = new Filter("system", "pagination", "pagination_fields", Filter.OPERATOR_OFFSET, "2", false);

        final List<Filter> expected = List.of(whereFilter, orderByFilter, limitFilter, offsetFilter);
        final List<Filter> wrongOrder = List.of(offsetFilter, limitFilter, orderByFilter, whereFilter);
        List<Filter> actual = null;
        try {
            actual = Filter.validateAndOrderFilters(wrongOrder);
        } catch (ExceptionInvalidFilter exception) {
            fail("Exception caught when it should have been valid while testing filter order validation");
        }
        assertEquals(expected, actual);
    }

    @Test
    void validateAndOrderFilters_TooManyOrderByLimitAndOffsetFilters_ReturnExceptionWithMultipleErrors() {
        final List<Filter> filters = List.of(
                new Filter("system", "number", "generation", Filter.OPERATOR_ORDER_BY, "asc", false),
                new Filter("system", "number", "generation", Filter.OPERATOR_ORDER_BY_DESC, "desc", false),
                new Filter("system", "pagination", "pagination_fields", Filter.OPERATOR_LIMIT, "3", false),
                new Filter("system", "pagination", "pagination_fields", Filter.OPERATOR_LIMIT, "4", false),
                new Filter("system", "pagination", "pagination_fields", Filter.OPERATOR_OFFSET, "1", false),
                new Filter("system", "pagination", "pagination_fields", Filter.OPERATOR_OFFSET, "2", false)
        );
        boolean exceptionCaught = false;
        try {
            Filter.validateAndOrderFilters(filters);
        } catch (ExceptionInvalidFilter exception) {
            exceptionCaught = true;
            assertEquals(3, exception.getMessages().size(), "Malformed ExceptionInvalidFilter thrown while testing filter multiples that are not allowed.");
        }
        if (!exceptionCaught) {
            fail("ExceptionInvalidFilter not caught when it should have been while testing multiple " +
                    Filter.OPERATOR_ORDER_BY + ", " +
                    Filter.OPERATOR_LIMIT + ", and " +
                    Filter.OPERATOR_OFFSET + " filters.");
        }
    }

    @Test
    void validateAndOrderFilters_MissingLimitWhileOffSetIncluded_ThrowException() {
        final List<Filter> filters = List.of(
                new Filter("system", "number", "generation", Filter.OPERATOR_ORDER_BY, "asc", false),
                new Filter("system", "pagination", "pagination_fields", Filter.OPERATOR_OFFSET, "2", false)
        );
        boolean exceptionCaught = false;
        try {
            Filter.validateAndOrderFilters(filters);
        } catch (ExceptionInvalidFilter exception) {
            exceptionCaught = true;
            assertEquals(1, exception.getMessages().size(), "Malformed ExceptionInvalidFilter thrown while testing missing " +
                    Filter.OPERATOR_LIMIT + " but included an " +
                    Filter.OPERATOR_OFFSET + " filter.");
        }
        if (!exceptionCaught) {
            fail("ExceptionInvalidFilter not caught when it should have been while testing a missing " +
                    Filter.OPERATOR_LIMIT + " but included an " +
                    Filter.OPERATOR_OFFSET + " filter.");
        }
    }

    @Test
    void validateAndOrderFilters_ErrorOnCastingBooleanAndInteger_ThrowException() {
        final List<Filter> filters = List.of(
                new Filter("system", "number", "generation", Filter.OPERATOR_GREATER_THAN, "not_a_number", false),
                new Filter("system", "boolean", "handheld", Filter.OPERATOR_EQUALS, "not_a_boolean", false)
        );
        boolean exceptionCaught = false;
        try {
            Filter.validateAndOrderFilters(filters);
        } catch (ExceptionInvalidFilter exception) {
            exceptionCaught = true;
            assertEquals(2, exception.getMessages().size(), "Malformed ExceptionInvalidFilter thrown while testing the casting of boolean and number filters.");
        }
        if (!exceptionCaught) {
            fail("ExceptionInvalidFilter not caught when it should have been while testing the casting of boolean and number filters.");
        }
    }

    @Test
    void validateAndOrderFilters_TimeFiltersIncorrectlyFormatted_ThrowException() {
        final List<Filter> filters = List.of(
                new Filter("system", "time", "created_at", Filter.OPERATOR_SINCE, "2024-05-32 00:00:00", false),
                new Filter("system", "time", "updated_at", Filter.OPERATOR_BEFORE, "2024-13-06 00:00:00", false)
        );
        boolean exceptionCaught = false;
        try {
            Filter.validateAndOrderFilters(filters);
        } catch (ExceptionInvalidFilter exception) {
            exceptionCaught = true;
            assertEquals(2, exception.getMessages().size(), "Malformed ExceptionInvalidFilter thrown while testing time operator format.");
        }
        if (!exceptionCaught) {
            fail("ExceptionInvalidFilter not caught when it should have been while testing time operator format.");
        }
    }

    @Test
    void formatWhereStatementsAndFormatOperands_StringFilters_ValidSql() {
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

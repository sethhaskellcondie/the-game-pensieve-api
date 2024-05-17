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

    @Test
    void validateAndOrderFilters_TwoFiltersSixErrors_ThrowExceptionWithSixMessages() {
        final List<Filter> filters = List.of(
                new Filter("system", "missingField", Filter.FILTER_OPERATOR_EQUALS, "not allowed ;"),
                new Filter("system", "anotherMissingField", Filter.FILTER_OPERATOR_STARTS_WITH, "keyword select is not allowed")
        );
        boolean exceptionCaught = false;
        try {
            Filter.validateAndOrderFilters(filters);
        } catch (ExceptionInvalidFilter exception) {
            exceptionCaught = true;
            assertEquals(6, exception.getMessages().size(), "Malformed ExceptionInvalidFilter thrown while testing invalid filters.");
        }
        if (!exceptionCaught) {
            fail("ExceptionInvalidFilter not caught when it should have been while testing invalid filters.");
        }
    }

    @Test
    void validateAndOrderFilters_containsAllBlacklistedWords_ThrowsMultipleErrors() {
        final String blacklistedWords = String.join(",", Filter.getBlacklistedWords());
        final List<Filter> filters = List.of(
                new Filter("system", "name", Filter.FILTER_OPERATOR_EQUALS, blacklistedWords)
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
        Filter whereFilter = new Filter("system", "name", Filter.FILTER_OPERATOR_CONTAINS, "Force");
        Filter orderByFilter = new Filter("system", "generation", Filter.FILTER_OPERATOR_ORDER_BY, "asc");
        Filter limitFilter = new Filter("system", "pagination", Filter.FILTER_OPERATOR_LIMIT, "3");
        Filter offsetFilter = new Filter("system", "pagination", Filter.FILTER_OPERATOR_OFFSET, "2");

        final List<Filter> expected = List.of(whereFilter, orderByFilter, limitFilter, offsetFilter);
        final List<Filter> wrongOrder = List.of(offsetFilter, limitFilter, orderByFilter, whereFilter);
        List<Filter> actual = Filter.validateAndOrderFilters(wrongOrder);
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
                new Filter("system", "generation", Filter.FILTER_OPERATOR_ORDER_BY, "asc"),
                new Filter("system", "generation", Filter.FILTER_OPERATOR_ORDER_BY, "desc"),
                new Filter("system", "pagination", Filter.FILTER_OPERATOR_LIMIT, "3"),
                new Filter("system", "pagination", Filter.FILTER_OPERATOR_LIMIT, "4"),
                new Filter("system", "pagination", Filter.FILTER_OPERATOR_OFFSET, "1"),
                new Filter("system", "pagination", Filter.FILTER_OPERATOR_OFFSET, "2")
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
                    Filter.FILTER_OPERATOR_ORDER_BY + ", " +
                    Filter.FILTER_OPERATOR_LIMIT + ", and " +
                    Filter.FILTER_OPERATOR_OFFSET + " filters.");
        }
    }

    @Test
    void validateAndOrderFilters_MissingLimitWhileOffSetIncluded_ThrowException() {
        final List<Filter> filters = List.of(
                new Filter("system", "generation", Filter.FILTER_OPERATOR_ORDER_BY, "asc"),
                new Filter("system", "pagination", Filter.FILTER_OPERATOR_OFFSET, "2")
        );
        boolean exceptionCaught = false;
        try {
            Filter.validateAndOrderFilters(filters);
        } catch (ExceptionInvalidFilter exception) {
            exceptionCaught = true;
            assertEquals(1, exception.getMessages().size(), "Malformed ExceptionInvalidFilter thrown while testing missing " +
                    Filter.FILTER_OPERATOR_LIMIT + " but included an " +
                    Filter.FILTER_OPERATOR_OFFSET + " filter.");
        }
        if (!exceptionCaught) {
            fail("ExceptionInvalidFilter not caught when it should have been while testing a missing " +
                    Filter.FILTER_OPERATOR_LIMIT + " but included an " +
                    Filter.FILTER_OPERATOR_OFFSET + " filter.");
        }
    }

    @Test
    void formatWhereStatementsAndOperands_StringFilters_ValidSql() {
        final String expectedSql = "SELECT * FROM systems WHERE 1 = 1 AND name = ? AND name <> ? AND name LIKE ? AND name LIKE ? AND name LIKE ?";
        final List<Object> expectedOperands = List.of(
                "SuperMegaForceWin",
                "NotMe",
                "%Force%",
                "SuperMega%",
                "%Win"
        );
        final List<Filter> filters = List.of(
                new Filter("system", "name", Filter.FILTER_OPERATOR_EQUALS, "SuperMegaForceWin"),
                new Filter("system", "name", Filter.FILTER_OPERATOR_NOT_EQUALS, "NotMe"),
                new Filter("system", "name", Filter.FILTER_OPERATOR_CONTAINS, "Force"),
                new Filter("system", "name", Filter.FILTER_OPERATOR_STARTS_WITH, "SuperMega"),
                new Filter("system", "name", Filter.FILTER_OPERATOR_ENDS_WITH, "Win")
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
    void formatWhereStatementsAndOperands_NumberFilters_ValidSql() {
        final String expectedSql = "SELECT * FROM systems WHERE 1 = 1 AND generation = ? AND generation <> ? AND generation > ? AND generation >= ? AND generation < ? AND generation <= ?";
        final List<Object> expectedOperands = List.of(3, 4, 5, 6, 7, 8);
        final List<Filter> filters = List.of(
                new Filter("system", "generation", Filter.FILTER_OPERATOR_EQUALS, "3"),
                new Filter("system", "generation", Filter.FILTER_OPERATOR_NOT_EQUALS, "4"),
                new Filter("system", "generation", Filter.FILTER_OPERATOR_GREATER_THAN, "5"),
                new Filter("system", "generation", Filter.FILTER_OPERATOR_GREATER_THAN_EQUAL_TO, "6"),
                new Filter("system", "generation", Filter.FILTER_OPERATOR_LESS_THAN, "7"),
                new Filter("system", "generation", Filter.FILTER_OPERATOR_LESS_THAN_EQUAL_TO, "8")
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
    void formatWhereStatementsAndOperands_BooleanFilters_ValidSql() {
        //This SQL wouldn't return any results, because we are testing the same field with true and false
        //perhaps I will update this when another resource has two different boolean fields
        final String expectedSql = "SELECT * FROM systems WHERE 1 = 1 AND handheld = ? AND handheld = ?";
        final List<Object> expectedOperands = List.of(true, false);
        final List<Filter> filters = List.of(
                new Filter("system", "handheld", Filter.FILTER_OPERATOR_EQUALS, "true"),
                new Filter("system", "handheld", Filter.FILTER_OPERATOR_EQUALS, "false")
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
    void formatWhereStatementsAndOperands_PaginationFilters_ValidSql() {
        final String expectedSql = "SELECT * FROM systems WHERE 1 = 1 ORDER BY generation ASC LIMIT ? OFFSET ?";
        final List<Object> expectedOperands = List.of(5, 1);
        final List<Filter> filters = List.of(
                new Filter("system", "generation", Filter.FILTER_OPERATOR_ORDER_BY, "asc"),
                new Filter("system", "pagination", Filter.FILTER_OPERATOR_LIMIT, "5"),
                new Filter("system", "pagination", Filter.FILTER_OPERATOR_OFFSET, "1")
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
    void formatWhereStatementsAndOperands_TimeFilters_ValidSql() {
        final String expectedSql = "SELECT * FROM systems WHERE 1 = 1 AND created_at > TO_TIMESTAMP( ? , 'YYYY-MM-DD') AND updated_at < TO_TIMESTAMP( ? , 'YYYY-MM-DD') ORDER BY generation DESC";
        final List<Object> expectedOperands = List.of("2024-05-06", "2024-05-04");
        final List<Filter> filters = List.of(
                new Filter("system", "created_at", Filter.FILTER_OPERATOR_SINCE, "2024-05-06"),
                new Filter("system", "updated_at", Filter.FILTER_OPERATOR_BEFORE, "2024-05-04"),
                new Filter("system", "generation", Filter.FILTER_OPERATOR_ORDER_BY_DESC, "desc")
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

    //TODO Test Enum filters (after an entity uses enums)

    private String getBaseQuery() {
        return "SELECT * FROM systems WHERE 1 = 1";
    }
}

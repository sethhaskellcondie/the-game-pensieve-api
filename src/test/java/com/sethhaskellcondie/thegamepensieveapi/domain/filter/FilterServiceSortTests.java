package com.sethhaskellcondie.thegamepensieveapi.domain.filter;

import com.sethhaskellcondie.thegamepensieveapi.domain.Keychain;
import com.sethhaskellcondie.thegamepensieveapi.domain.exceptions.ExceptionInvalidFilter;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * These tests exercise the sort (order_by) SQL generation in isolation by calling the static
 * FilterService methods directly. They need neither a database nor Docker.
 * <p>
 * Background: 'all_fields' (FIELD_TYPE_SORT) is a placeholder that the filter metadata advertises to
 * signal sorting is supported — it is not a real column. A client previously could send it as a
 * literal sort field (field="all_fields", operator="order_by"); validation let it through and the
 * query then died at the database with "bad SQL grammar" because there is no systems.all_fields
 * column. Sorting is instead done by putting the real column name in the field.
 */
public class FilterServiceSortTests {

    /**
     * Regression guard for the reported bug: sorting on the 'all_fields' placeholder must be rejected
     * during validation (a clean 400) rather than crashing at SQL execution time (a 500).
     */
    @Test
    void orderByAllFieldsPlaceholder_IsRejectedByValidation() {
        final List<Filter> filters = List.of(
                new Filter(Keychain.SYSTEM_KEY, Filter.FIELD_TYPE_SORT, Filter.ALL_FIELDS, Filter.OPERATOR_ORDER_BY, "Release Year", false),
                new Filter(Keychain.SYSTEM_KEY, Filter.FIELD_TYPE_SORT, Filter.ALL_FIELDS, Filter.OPERATOR_ORDER_BY, "name", false)
        );

        assertThrows(ExceptionInvalidFilter.class, () -> FilterService.validateAndOrderFilters(filters, List.of()),
                "Sorting on the 'all_fields' placeholder should be rejected during validation, not crash at SQL execution.");
    }

    /**
     * The intended way to sort: the real column name goes in the field, order_by goes in the operator,
     * and the operand is ignored. This is how the working sort tests build their filters.
     */
    @Test
    void orderByRealColumns_GeneratesValidOrderBy() {
        final List<Filter> filters = List.of(
                new Filter(Keychain.SYSTEM_KEY, Filter.FIELD_TYPE_NUMBER, "generation", Filter.OPERATOR_ORDER_BY, "unused", false),
                new Filter(Keychain.SYSTEM_KEY, Filter.FIELD_TYPE_TEXT, "name", Filter.OPERATOR_ORDER_BY, "unused", false)
        );

        final List<Filter> validated = assertDoesNotThrow(() -> FilterService.validateAndOrderFilters(filters, List.of()));

        final String generatedSql = String.join("", FilterService.formatWhereStatements(validated));

        assertTrue(generatedSql.contains("ORDER BY systems.generation ASC, systems.name ASC"),
                "Expected a valid ORDER BY referencing real columns. Generated: " + generatedSql);
    }
}

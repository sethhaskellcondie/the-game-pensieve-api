package com.sethhaskellcondie.thegamepensieveapi.domain.filter;

import com.sethhaskellcondie.thegamepensieveapi.domain.Keychain;
import com.sethhaskellcondie.thegamepensieveapi.domain.customfield.CustomField;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * These tests exercise the custom field filter SQL generation in isolation by calling the static FilterService
 * methods directly. They need neither a database nor Docker.
 * <p>
 * Background: the clause that selects WHICH custom field is being filtered on used to interpolate the field's
 * name straight into a quoted SQL literal — {@code AND fields1.name = '<name>'}. The name is fully user
 * controlled (the user types it when creating the custom field) and the filter denylist only ever inspected the
 * operand, so a field created with a name like {@code zzz' OR pg_sleep(10)--} became a stored, second-order SQL
 * injection that fired the moment anyone filtered on it.
 * <p>
 * The name is now bound as a parameter. Because {@code formatWhereStatements} and {@code formatOperands} are
 * separate methods walking the same list, the placeholders and the operands have to stay in lockstep — that
 * ordering contract is what most of these tests pin down, since getting it wrong silently shifts every operand
 * by one and produces wrong results rather than an error.
 */
public class FilterServiceCustomFieldNameBindingTests {

    private static final CustomField TEXT_FIELD = CustomField.withoutOptions(1, "Publisher", CustomField.TYPE_TEXT, Keychain.TOY_KEY);
    private static final CustomField NUMBER_FIELD = CustomField.withoutOptions(2, "Weight", CustomField.TYPE_NUMBER, Keychain.TOY_KEY);

    /**
     * The regression guard for the injection itself: whatever the name contains, it must leave the generated SQL
     * as a '?' and arrive at the driver as an operand. A name carrying a quote and a comment marker is the exact
     * payload the old code executed.
     */
    @Test
    void maliciousCustomFieldName_IsBoundAsAnOperandNotInterpolatedIntoSql() {
        final String maliciousName = "zzz' OR pg_sleep(10)--";
        final CustomField maliciousField = CustomField.withoutOptions(3, maliciousName, CustomField.TYPE_TEXT, Keychain.TOY_KEY);
        final List<Filter> filters = List.of(
                new Filter(Keychain.TOY_KEY, CustomField.TYPE_TEXT, maliciousName, Filter.OPERATOR_EQUALS, "anything", true)
        );

        final List<Filter> validated = assertDoesNotThrow(() -> FilterService.validateAndOrderFilters(filters, List.of(maliciousField)));
        final String generatedSql = String.join("", FilterService.formatWhereStatements(validated));

        assertFalse(generatedSql.contains(maliciousName),
                "The custom field name must never appear in the generated SQL. Generated: " + generatedSql);
        assertFalse(generatedSql.contains("pg_sleep"),
                "The generated SQL must not carry any part of the injected payload. Generated: " + generatedSql);
        assertTrue(generatedSql.contains("AND fields1.name = ?"),
                "The name clause must be a bound parameter. Generated: " + generatedSql);
        assertEquals(List.of(maliciousName, "anything"), FilterService.formatOperands(validated),
                "The name must reach the driver as an operand, ahead of the filter's own value.");
    }

    /**
     * The name clause is NOT redundant with the JOIN. buildQueryWithCustomFieldJoins() joins custom_fields on
     * values<i>.custom_field_id = fields<i>.id with no constraint on which field that is, so this clause is the
     * only thing narrowing the query to the field being filtered on. Deleting it (rather than binding it) would
     * make every custom field filter match values belonging to every custom field.
     */
    @Test
    void nameClauseIsStillEmitted_ItIsWhatSelectsTheField() {
        final List<Filter> filters = List.of(
                new Filter(Keychain.TOY_KEY, CustomField.TYPE_TEXT, "Publisher", Filter.OPERATOR_EQUALS, "Nintendo", true)
        );

        final List<Filter> validated = assertDoesNotThrow(() -> FilterService.validateAndOrderFilters(filters, List.of(TEXT_FIELD)));
        final String generatedSql = String.join("", FilterService.formatWhereStatements(validated));

        assertTrue(generatedSql.contains("AND fields1.name = ?"),
                "Expected the field-selecting clause to still be generated. Generated: " + generatedSql);
        assertTrue(generatedSql.contains("AND values1.value_text = ?"),
                "Expected the value comparison to still be generated. Generated: " + generatedSql);
    }

    /**
     * Two custom field filters get their own JOIN aliases, so each contributes its own name placeholder followed
     * by its own value — four operands in strict name, value, name, value order.
     */
    @Test
    void multipleCustomFieldFilters_EachContributeANameThenAValue() {
        final List<Filter> filters = List.of(
                new Filter(Keychain.TOY_KEY, CustomField.TYPE_TEXT, "Publisher", Filter.OPERATOR_EQUALS, "Nintendo", true),
                new Filter(Keychain.TOY_KEY, CustomField.TYPE_NUMBER, "Weight", Filter.OPERATOR_GREATER_THAN, "5", true)
        );

        final List<Filter> validated = assertDoesNotThrow(() -> FilterService.validateAndOrderFilters(filters, List.of(TEXT_FIELD, NUMBER_FIELD)));
        final String generatedSql = String.join("", FilterService.formatWhereStatements(validated));

        assertTrue(generatedSql.contains("AND fields1.name = ?"), "Generated: " + generatedSql);
        assertTrue(generatedSql.contains("AND fields2.name = ?"), "Generated: " + generatedSql);
        assertEquals(List.of("Publisher", "Nintendo", "Weight", 5), FilterService.formatOperands(validated),
                "Each custom field filter contributes its name then its value, in filter order.");
    }

    /**
     * A custom field SORT contributes only a name placeholder: its ORDER BY is deferred to the end of the
     * statement list and carries no placeholder of its own. This is the case most likely to drift, because the
     * name clause and the ORDER BY it belongs to end up in different parts of the statement.
     */
    @Test
    void customFieldSort_ContributesANameOperandButNoValue() {
        final List<Filter> filters = List.of(
                new Filter(Keychain.TOY_KEY, CustomField.TYPE_TEXT, "Publisher", Filter.OPERATOR_ORDER_BY, "unused", true)
        );

        final List<Filter> validated = assertDoesNotThrow(() -> FilterService.validateAndOrderFilters(filters, List.of(TEXT_FIELD)));
        final String generatedSql = String.join("", FilterService.formatWhereStatements(validated));

        assertTrue(generatedSql.contains("AND fields1.name = ?"), "Generated: " + generatedSql);
        assertTrue(generatedSql.contains("ORDER BY values1.value_text ASC"), "Generated: " + generatedSql);
        assertEquals(List.of("Publisher"), FilterService.formatOperands(validated),
                "A sort ignores its operand, so it contributes the name and nothing else.");
    }

    /**
     * The full ordering contract in one query: a custom where filter, a plain where filter, a custom sort, then
     * pagination. validateAndOrderFilters reorders into where then sort then limit then offset, and the operands
     * must come out in exactly the order the '?' placeholders appear in the assembled statement.
     */
    @Test
    void mixedFilters_OperandsMatchThePlaceholderOrder() {
        final List<Filter> filters = List.of(
                new Filter(Keychain.TOY_KEY, Filter.FIELD_TYPE_PAGINATION, Filter.PAGINATION_FIELDS, Filter.OPERATOR_LIMIT, "10", false),
                new Filter(Keychain.TOY_KEY, CustomField.TYPE_TEXT, "Publisher", Filter.OPERATOR_ORDER_BY, "unused", true),
                new Filter(Keychain.TOY_KEY, CustomField.TYPE_NUMBER, "Weight", Filter.OPERATOR_EQUALS, "7", true),
                new Filter(Keychain.TOY_KEY, Filter.FIELD_TYPE_TEXT, "name", Filter.OPERATOR_EQUALS, "Darkwing Duck", false),
                new Filter(Keychain.TOY_KEY, Filter.FIELD_TYPE_PAGINATION, Filter.PAGINATION_FIELDS, Filter.OPERATOR_OFFSET, "20", false)
        );

        final List<Filter> validated = assertDoesNotThrow(() -> FilterService.validateAndOrderFilters(filters, List.of(TEXT_FIELD, NUMBER_FIELD)));
        final String generatedSql = String.join("", FilterService.formatWhereStatements(validated));
        final List<Object> operands = FilterService.formatOperands(validated);

        //The custom where filter's name+value, then the plain where filter's value, then the custom sort's name,
        //then limit and offset — the same order the placeholders appear in above.
        assertEquals(List.of("Weight", 7, "Darkwing Duck", "Publisher", 10, 20), operands,
                "Operands drifted out of step with the placeholders. Generated: " + generatedSql);
        assertEquals(countPlaceholders(generatedSql), operands.size(),
                "Every '?' in the statement needs exactly one operand. Generated: " + generatedSql);
    }

    /**
     * A LIKE-family operator wraps its operand in wildcards, and that wrapping must not disturb the name that
     * precedes it.
     */
    @Test
    void customFieldContains_WrapsOnlyTheValueNotTheName() {
        final List<Filter> filters = List.of(
                new Filter(Keychain.TOY_KEY, CustomField.TYPE_TEXT, "Publisher", Filter.OPERATOR_CONTAINS, "tendo", true)
        );

        final List<Filter> validated = assertDoesNotThrow(() -> FilterService.validateAndOrderFilters(filters, List.of(TEXT_FIELD)));

        assertEquals(List.of("Publisher", "%tendo%"), FilterService.formatOperands(validated),
                "Only the value takes the LIKE wildcards; the bound name is passed through untouched.");
    }

    private static int countPlaceholders(String sql) {
        return (int) sql.chars().filter(c -> c == '?').count();
    }
}

package com.sethhaskellcondie.thegamepensieveapi.domain.filter;

import com.sethhaskellcondie.thegamepensieveapi.domain.Keychain;
import com.sethhaskellcondie.thegamepensieveapi.domain.customfield.CustomField;
import com.sethhaskellcondie.thegamepensieveapi.domain.customfield.CustomFieldOption;
import com.sethhaskellcondie.thegamepensieveapi.domain.customfield.CustomFieldOptionRepository;
import com.sethhaskellcondie.thegamepensieveapi.domain.customfield.CustomFieldRepository;
import com.sethhaskellcondie.thegamepensieveapi.domain.customfield.CustomFieldRequestDto;
import com.sethhaskellcondie.thegamepensieveapi.domain.customfield.CustomFieldValue;
import com.sethhaskellcondie.thegamepensieveapi.domain.entity.system.System;
import com.sethhaskellcondie.thegamepensieveapi.domain.entity.system.SystemRepository;
import com.sethhaskellcondie.thegamepensieveapi.domain.exceptions.ExceptionInvalidFilter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.JdbcTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Enum type custom fields (dropdown, radio button, progress bar) store the selected option as a
 * reference to custom_field_options.id, so they filter by that option id exactly like the system
 * filter: only equals/not-equals, the operand is the option id (a whole number), and no text
 * matching (contains/starts_with/ends_with).
 * <p>
 * Sorting IS supported, but it orders by the selected option's display_order (the order the user
 * arranged the options in) rather than the option id, so that values sort in a logical, user-defined
 * order instead of creation order.
 */
@JdbcTest
@ActiveProfiles("filter-tests8")
public class GetWithFiltersCustomFieldOptionTests {

    @Autowired
    protected JdbcTemplate jdbcTemplate;
    protected SystemRepository systemRepository;
    protected CustomFieldRepository customFieldRepository;
    protected CustomFieldOptionRepository optionRepository;
    private final String customFieldName = "Status";

    @BeforeEach
    public void setUp() {
        systemRepository = new SystemRepository(jdbcTemplate);
        optionRepository = new CustomFieldOptionRepository(jdbcTemplate);
        customFieldRepository = new CustomFieldRepository(jdbcTemplate, optionRepository);
    }

    @Test
    void testCustomFieldOptionFilters() {
        final CustomField statusField = customFieldRepository.insertCustomField(
                CustomFieldRequestDto.withoutOptions(customFieldName, CustomField.TYPE_DROPDOWN, Keychain.SYSTEM_KEY));
        final CustomFieldOption newOption = optionRepository.insertOption(statusField.id(), "New", true, 0);
        final CustomFieldOption usedOption = optionRepository.insertOption(statusField.id(), "Used", false, 1);

        final CustomFieldValue newValue = new CustomFieldValue(statusField.id(), statusField.name(), CustomField.TYPE_DROPDOWN, null, newOption.id());
        final CustomFieldValue usedValue = new CustomFieldValue(statusField.id(), statusField.name(), CustomField.TYPE_DROPDOWN, null, usedOption.id());

        insertSystemData("System 1", List.of(newValue));
        insertSystemData("System 2", List.of(newValue));
        insertSystemData("System 3", List.of(usedValue));

        //equals on the "New" option id returns the two systems that selected it
        assertEquals(2, systemRepository.getWithFilters(List.of(
                new Filter(Keychain.SYSTEM_KEY, Filter.FIELD_TYPE_DROPDOWN, customFieldName, Filter.OPERATOR_EQUALS, String.valueOf(newOption.id()), true)
        )).size(), "Wrong number of results filtering enum custom field by option id equals.");

        //not-equals on the "New" option id returns the single system that selected "Used"
        assertEquals(1, systemRepository.getWithFilters(List.of(
                new Filter(Keychain.SYSTEM_KEY, Filter.FIELD_TYPE_DROPDOWN, customFieldName, Filter.OPERATOR_NOT_EQUALS, String.valueOf(newOption.id()), true)
        )).size(), "Wrong number of results filtering enum custom field by option id not-equals.");
    }

    @Test
    void optionFilter_EqualsGeneratesOptionIdWhereClause() {
        final List<Filter> filters = List.of(
                new Filter(Keychain.SYSTEM_KEY, Filter.FIELD_TYPE_DROPDOWN, customFieldName, Filter.OPERATOR_EQUALS, "5", true)
        );
        final String generatedSql = String.join("", FilterService.formatWhereStatements(filters));
        assertTrue(generatedSql.contains(".value_option_id = ?"),
                "Enum equals filter should compare the option id column. Generated: " + generatedSql);
    }

    @Test
    void optionFilter_TextOperatorsRejectedByValidation() {
        final CustomField statusField = new CustomField(1, customFieldName, CustomField.TYPE_DROPDOWN, Keychain.SYSTEM_KEY, 0, List.of());
        for (String textOperator : List.of(Filter.OPERATOR_CONTAINS, Filter.OPERATOR_STARTS_WITH, Filter.OPERATOR_ENDS_WITH)) {
            final List<Filter> filters = List.of(
                    new Filter(Keychain.SYSTEM_KEY, Filter.FIELD_TYPE_DROPDOWN, customFieldName, textOperator, "New", true)
            );
            assertThrows(ExceptionInvalidFilter.class, () -> FilterService.validateAndOrderFilters(filters, List.of(statusField)),
                    "Text operator '" + textOperator + "' should not be allowed on an enum custom field.");
        }
    }

    @Test
    void optionFilter_SortAllowedByValidation() {
        final CustomField statusField = new CustomField(1, customFieldName, CustomField.TYPE_DROPDOWN, Keychain.SYSTEM_KEY, 0, List.of());
        for (String sortOperator : List.of(Filter.OPERATOR_ORDER_BY, Filter.OPERATOR_ORDER_BY_DESC)) {
            final List<Filter> filters = List.of(
                    new Filter(Keychain.SYSTEM_KEY, Filter.FIELD_TYPE_DROPDOWN, customFieldName, sortOperator, "unused", true)
            );
            assertDoesNotThrow(() -> FilterService.validateAndOrderFilters(filters, List.of(statusField)),
                    "Sorting with '" + sortOperator + "' should be allowed on an enum custom field.");
        }
    }

    @Test
    void optionFilter_NonIntegerOperandRejectedByValidation() {
        final CustomField statusField = new CustomField(1, customFieldName, CustomField.TYPE_DROPDOWN, Keychain.SYSTEM_KEY, 0, List.of());
        final List<Filter> filters = List.of(
                new Filter(Keychain.SYSTEM_KEY, Filter.FIELD_TYPE_DROPDOWN, customFieldName, Filter.OPERATOR_EQUALS, "not-a-number", true)
        );
        assertThrows(ExceptionInvalidFilter.class, () -> FilterService.validateAndOrderFilters(filters, List.of(statusField)),
                "Enum filters must reject non-integer operands (option ids are whole numbers).");
    }

    @Test
    void optionFilter_IntegerOperandEqualsAllowed() {
        final CustomField statusField = new CustomField(1, customFieldName, CustomField.TYPE_DROPDOWN, Keychain.SYSTEM_KEY, 0, List.of());
        final List<Filter> filters = List.of(
                new Filter(Keychain.SYSTEM_KEY, Filter.FIELD_TYPE_DROPDOWN, customFieldName, Filter.OPERATOR_EQUALS, "5", true)
        );
        assertDoesNotThrow(() -> FilterService.validateAndOrderFilters(filters, List.of(statusField)),
                "Enum equals filter with a whole-number option id operand should be valid.");
    }

    @Test
    void dropdownSort_OrdersByOptionDisplayOrder() {
        assertEnumSortFollowsDisplayOrder(CustomField.TYPE_DROPDOWN, "Dropdown Status");
    }

    @Test
    void radioButtonSort_OrdersByOptionDisplayOrder() {
        assertEnumSortFollowsDisplayOrder(CustomField.TYPE_RADIO_BUTTON, "Radio Status");
    }

    @Test
    void progressBarSort_OrdersByOptionDisplayOrder() {
        assertEnumSortFollowsDisplayOrder(CustomField.TYPE_PROGRESS_BAR, "Progress Status");
    }

    /**
     * Sorting an enum custom field must follow the option display_order. The options below are arranged so
     * that display_order disagrees with both alphabetical order and insertion/id order, so a passing result
     * can only come from sorting on display_order:
     *   - alphabetical:        Alpha, Mango, Zeta
     *   - insertion/id order:  Zeta, Alpha, Mango
     *   - display_order:       Zeta(0), Alpha(1), Mango(2)
     */
    private void assertEnumSortFollowsDisplayOrder(String type, String fieldName) {
        final CustomField field = customFieldRepository.insertCustomField(
                CustomFieldRequestDto.withoutOptions(fieldName, type, Keychain.SYSTEM_KEY));
        final CustomFieldOption zeta = optionRepository.insertOption(field.id(), "Zeta", true, 0);
        final CustomFieldOption alpha = optionRepository.insertOption(field.id(), "Alpha", false, 1);
        final CustomFieldOption mango = optionRepository.insertOption(field.id(), "Mango", false, 2);

        final CustomFieldValue zetaValue = new CustomFieldValue(field.id(), field.name(), type, null, zeta.id());
        final CustomFieldValue alphaValue = new CustomFieldValue(field.id(), field.name(), type, null, alpha.id());
        final CustomFieldValue mangoValue = new CustomFieldValue(field.id(), field.name(), type, null, mango.id());

        //inserted in an order that matches neither display_order nor alphabetical order
        insertSystemData("Alpha System", List.of(alphaValue));
        insertSystemData("Mango System", List.of(mangoValue));
        insertSystemData("Zeta System", List.of(zetaValue));

        final List<System> ascending = systemRepository.getWithFilters(List.of(
                new Filter(Keychain.SYSTEM_KEY, type, fieldName, Filter.OPERATOR_ORDER_BY, "unused", true)
        ));
        assertEquals(List.of("Zeta System", "Alpha System", "Mango System"), ascending.stream().map(System::getName).toList(),
                "Ascending sort on a " + type + " custom field should follow the option display_order, not alphabetical or id order.");

        final List<System> descending = systemRepository.getWithFilters(List.of(
                new Filter(Keychain.SYSTEM_KEY, type, fieldName, Filter.OPERATOR_ORDER_BY_DESC, "unused", true)
        ));
        assertEquals(List.of("Mango System", "Alpha System", "Zeta System"), descending.stream().map(System::getName).toList(),
                "Descending sort on a " + type + " custom field should follow the reverse option display_order.");
    }

    private void insertSystemData(String name, List<CustomFieldValue> customFieldValues) {
        systemRepository.insert(new System(null, name, 1, false, null, null, null, customFieldValues));
    }
}

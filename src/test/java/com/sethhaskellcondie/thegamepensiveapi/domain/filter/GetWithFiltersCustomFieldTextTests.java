package com.sethhaskellcondie.thegamepensiveapi.domain.filter;

import com.sethhaskellcondie.thegamepensiveapi.domain.Keychain;
import com.sethhaskellcondie.thegamepensiveapi.domain.customfield.CustomField;
import com.sethhaskellcondie.thegamepensiveapi.domain.customfield.CustomFieldRepository;
import com.sethhaskellcondie.thegamepensiveapi.domain.customfield.CustomFieldValue;
import com.sethhaskellcondie.thegamepensiveapi.domain.system.System;
import com.sethhaskellcondie.thegamepensiveapi.domain.system.SystemRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.JdbcTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

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
@ActiveProfiles("test-container")
public class GetWithFiltersCustomFieldTextTests {

    @Autowired
    protected JdbcTemplate jdbcTemplate;
    protected SystemRepository systemRepository;
    protected CustomFieldRepository customFieldRepository;
    private final String customFieldName = "Publisher";

    @BeforeEach
    public void setUp() {
        systemRepository = new SystemRepository(jdbcTemplate);
        customFieldRepository = new CustomFieldRepository(jdbcTemplate);
    }

    @Test
    void testCustomFieldsTextFilters() {
        final CustomField publisherCustomField = customFieldRepository.insertCustomField(customFieldName, CustomField.TYPE_TEXT, Keychain.SYSTEM_KEY);

        final CustomFieldValue nintendo = new CustomFieldValue(publisherCustomField.id(), publisherCustomField.name(), publisherCustomField.type(), "nintendo");
        final CustomFieldValue sega = new CustomFieldValue(publisherCustomField.id(), publisherCustomField.name(), publisherCustomField.type(), "sega sammy");
        final CustomFieldValue sony = new CustomFieldValue(publisherCustomField.id(), publisherCustomField.name(), publisherCustomField.type(), "sony");
        final CustomFieldValue microsoft = new CustomFieldValue(publisherCustomField.id(), publisherCustomField.name(), publisherCustomField.type(), "microsoft");
        final CustomFieldValue newSony = new CustomFieldValue(publisherCustomField.id(), publisherCustomField.name(), publisherCustomField.type(), "new sony");

        insertSystemData("System 1", List.of(nintendo));
        insertSystemData("System 2", List.of(sega));
        insertSystemData("System 3", List.of(sony));
        insertSystemData("System 4", List.of(microsoft));
        insertSystemData("System 5", List.of(newSony));

        testNoFilters(5);
        testTextFilterNameEquals("sega sammy", 1);
        testTextFilterNameNotEquals("sega sammy", 4);
        testTextFilterNameContains("s", 4);
        testTextFilterNameStartsWith("s", 2);
        testTextFilterNameEndsWith("y", 3);
    }

    void testNoFilters(int expectedResults) {
        final List<Filter> filters = List.of();

        final List<System> results = systemRepository.getWithFilters(filters);

        assertEquals(expectedResults, results.size(), "Wrong number of results returned when testing results with no filters on the custom field text filter data.");
    }

    void testTextFilterNameEquals(String name, int expectedResults) {
        final List<Filter> filters = List.of(
                new Filter(Keychain.SYSTEM_KEY, Filter.FIELD_TYPE_TEXT, customFieldName, Filter.OPERATOR_EQUALS, name, true)
        );

        final List<System> results = systemRepository.getWithFilters(filters);

        assertEquals(expectedResults, results.size(), "Wrong number of results returned when testing custom field text filter name equals.");
    }

    void testTextFilterNameNotEquals(String name, int expectedResults) {
        final List<Filter> filters = List.of(
                new Filter(Keychain.SYSTEM_KEY, Filter.FIELD_TYPE_TEXT, customFieldName, Filter.OPERATOR_NOT_EQUALS, name, true)
        );

        final List<System> results = systemRepository.getWithFilters(filters);

        assertEquals(expectedResults, results.size(), "Wrong number of results returned when testing custom field text filter name not_equals.");
    }

    void testTextFilterNameContains(String name, int expectedResults) {
        final List<Filter> filters = List.of(
                new Filter(Keychain.SYSTEM_KEY, Filter.FIELD_TYPE_TEXT, customFieldName, Filter.OPERATOR_CONTAINS, name, true)
        );

        final List<System> results = systemRepository.getWithFilters(filters);

        assertEquals(expectedResults, results.size(), "Wrong number of results returned when testing custom field text filter name contains.");
    }

    void testTextFilterNameStartsWith(String name, int expectedResults) {
        final List<Filter> filters = List.of(
                new Filter(Keychain.SYSTEM_KEY, Filter.FIELD_TYPE_TEXT, customFieldName, Filter.OPERATOR_STARTS_WITH, name, true)
        );

        final List<System> results = systemRepository.getWithFilters(filters);

        assertEquals(expectedResults, results.size(), "Wrong number of results returned when testing custom field text filter name starts_with.");
    }

    void testTextFilterNameEndsWith(String name, int expectedResults) {
        final List<Filter> filters = List.of(
                new Filter(Keychain.SYSTEM_KEY, Filter.FIELD_TYPE_TEXT, customFieldName, Filter.OPERATOR_ENDS_WITH, name, true)
        );

        final List<System> results = systemRepository.getWithFilters(filters);

        assertEquals(expectedResults, results.size(), "Wrong number of results returned when testing custom field text filter name ends_with.");
    }

    private void insertSystemData(String name, List<CustomFieldValue> customFieldValues) {
        systemRepository.insert(new System(null, name, 1, false, null, null, null, customFieldValues));
    }
}

package com.sethhaskellcondie.thegamepensieveapi.domain.filter;

import com.sethhaskellcondie.thegamepensieveapi.domain.Keychain;
import com.sethhaskellcondie.thegamepensieveapi.domain.customfield.CustomField;
import com.sethhaskellcondie.thegamepensieveapi.domain.customfield.CustomFieldOptionRepository;
import com.sethhaskellcondie.thegamepensieveapi.domain.customfield.CustomFieldRepository;
import com.sethhaskellcondie.thegamepensieveapi.domain.customfield.CustomFieldRequestDto;
import com.sethhaskellcondie.thegamepensieveapi.domain.customfield.CustomFieldValue;
import com.sethhaskellcondie.thegamepensieveapi.domain.entity.system.System;
import com.sethhaskellcondie.thegamepensieveapi.domain.entity.system.SystemRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.JdbcTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import com.sethhaskellcondie.thegamepensieveapi.domain.exceptions.ExceptionInvalidFilter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Verifies that getWithFilters() returns correct results when multiple custom field filters
 * are combined in a single query. This was previously broken because the generated SQL used
 * a single JOIN alias for all custom fields, making it impossible for a row to satisfy
 * two different field name conditions simultaneously.
 *
 * Also verifies that soft-deleted custom fields are excluded from results.
 */
@JdbcTest
@ActiveProfiles("filter-tests7")
public class GetWithFiltersMultipleCustomFieldFiltersTests {

    @Autowired
    protected JdbcTemplate jdbcTemplate;
    protected SystemRepository systemRepository;
    protected CustomFieldRepository customFieldRepository;

    @BeforeEach
    public void setUp() {
        systemRepository = new SystemRepository(jdbcTemplate);
        customFieldRepository = new CustomFieldRepository(jdbcTemplate, new CustomFieldOptionRepository(jdbcTemplate));
    }

    @Test
    void testMultipleCustomFieldFiltersReturnCorrectResults() {
        String maxPlayersFieldName = "Max Players";
        final CustomField maxPlayersField = customFieldRepository.insertCustomField(
                CustomFieldRequestDto.withoutOptions(maxPlayersFieldName, CustomField.TYPE_NUMBER, Keychain.SYSTEM_KEY));
        String localMultiplayerFieldName = "Local Multiplayer";
        final CustomField localMultiplayerField = customFieldRepository.insertCustomField(
                CustomFieldRequestDto.withoutOptions(localMultiplayerFieldName, CustomField.TYPE_BOOLEAN, Keychain.SYSTEM_KEY));

        CustomFieldValue maxPlayers1 = createCfValue(maxPlayersField, "1");
        CustomFieldValue maxPlayers2 = createCfValue(maxPlayersField, "2");
        CustomFieldValue maxPlayers4 = createCfValue(maxPlayersField, "4");
        CustomFieldValue localMultiTrue = createCfValue(localMultiplayerField, "true");
        CustomFieldValue localMultiFalse = createCfValue(localMultiplayerField, "false");

        insertSystem("System 1", List.of(maxPlayers4, localMultiTrue)); //matches both
        insertSystem("System 2", List.of(maxPlayers2, localMultiTrue)); //matches both
        insertSystem("System 3", List.of(maxPlayers4, localMultiFalse)); //matches max players but not multiplayer
        insertSystem("System 4", List.of(maxPlayers1, localMultiTrue)); //matches multiplayer but not max players
        insertSystem("System 5", List.of(maxPlayers4)); // no multiplayer field
        insertSystem("System 6", List.of(localMultiTrue)); // no max players field


        final int expectedMaxPlayers = 4; // should return systems 1, 2, 3, 5
        List<Filter> maxPlayersFilter = List.of(
                new Filter(Keychain.SYSTEM_KEY, Filter.FIELD_TYPE_NUMBER, maxPlayersFieldName, Filter.OPERATOR_GREATER_THAN_EQUAL_TO, "2", true)
        );
        assertEquals(expectedMaxPlayers, systemRepository.getWithFilters(maxPlayersFilter).size(), "Single custom field number filter (>=2) should return 4 results.");

        final int expectedMultiplayer = 4; // should return systems 1, 2, 4, 6
        List<Filter> multiplayerFilter = List.of(
                new Filter(Keychain.SYSTEM_KEY, Filter.FIELD_TYPE_BOOLEAN, localMultiplayerFieldName, Filter.OPERATOR_EQUALS, "true", true)
        );
        assertEquals(expectedMultiplayer, systemRepository.getWithFilters(multiplayerFilter).size(),
                "Single custom field boolean filter (=true) should return 4 results.");

        final int expectedCombined = 2; // should return only systems 1 and 2
        List<Filter> combinedFilters = List.of(
                new Filter(Keychain.SYSTEM_KEY, Filter.FIELD_TYPE_NUMBER, maxPlayersFieldName, Filter.OPERATOR_GREATER_THAN_EQUAL_TO, "2", true),
                new Filter(Keychain.SYSTEM_KEY, Filter.FIELD_TYPE_BOOLEAN, localMultiplayerFieldName, Filter.OPERATOR_EQUALS, "true", true)
        );
        assertEquals(expectedCombined, systemRepository.getWithFilters(combinedFilters).size(),
                "Combined custom field filters (max players >= 2 AND local multiplayer = true) should return exactly 2 results.");
    }

    @Test
    void testDeletedCustomFieldsAreExcluded() {
        final CustomField deletedField = customFieldRepository.insertCustomField(
                CustomFieldRequestDto.withoutOptions("Deleted Field", CustomField.TYPE_NUMBER, Keychain.SYSTEM_KEY));
        final CustomField activeField = customFieldRepository.insertCustomField(
                CustomFieldRequestDto.withoutOptions("Active Field", CustomField.TYPE_NUMBER, Keychain.SYSTEM_KEY));

        CustomFieldValue deletedFieldValue = createCfValue(deletedField, "42");
        CustomFieldValue activeFieldValue = createCfValue(activeField, "42");
        insertSystem("System With Deleted Field", List.of(deletedFieldValue, activeFieldValue));
        customFieldRepository.deleteById(deletedField.id());


        List<Filter> filterOnDeletedField = List.of(
                new Filter(Keychain.SYSTEM_KEY, Filter.FIELD_TYPE_NUMBER, "Deleted Field", Filter.OPERATOR_EQUALS, "42", true)
        );
        assertThrows(ExceptionInvalidFilter.class, () -> systemRepository.getWithFilters(filterOnDeletedField),
                "Filtering on a deleted custom field should throw ExceptionInvalidFilter since deleted fields are excluded from validation.");

        List<Filter> filterOnActiveField = List.of(
                new Filter(Keychain.SYSTEM_KEY, Filter.FIELD_TYPE_NUMBER, "Active Field", Filter.OPERATOR_EQUALS, "42", true)
        );
        assertEquals(1, systemRepository.getWithFilters(filterOnActiveField).size(),
                "Filtering on an active custom field should return the matching system.");
    }

    private CustomFieldValue createCfValue(CustomField field, String value) {
        return new CustomFieldValue(field.id(), field.name(), field.type(), value);
    }

    private void insertSystem(String name, List<CustomFieldValue> customFieldValues) {
        systemRepository.insert(new System(null, name, 1, false, null, null, null, customFieldValues));
    }
}

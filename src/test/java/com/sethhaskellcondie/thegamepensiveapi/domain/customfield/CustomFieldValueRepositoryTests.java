package com.sethhaskellcondie.thegamepensiveapi.domain.customfield;

import com.sethhaskellcondie.thegamepensiveapi.exceptions.ExceptionCustomFieldValue;
import com.sethhaskellcondie.thegamepensiveapi.exceptions.ExceptionFailedDbValidation;
import com.sethhaskellcondie.thegamepensiveapi.exceptions.ExceptionResourceNotFound;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.JdbcTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.fail;

@JdbcTest
@ActiveProfiles("test-container")
public class CustomFieldValueRepositoryTests {

    @Autowired
    protected JdbcTemplate jdbcTemplate;
    protected CustomFieldValueRepository repository;
    protected CustomFieldRepository customFieldRepository;

    @BeforeEach
    public void setUp() {
        repository = new CustomFieldValueRepository(jdbcTemplate);
        customFieldRepository = new CustomFieldRepository(jdbcTemplate);
    }

    @Test
    public void upsertValue_NewCustomFieldAndValue_NewCustomFieldAndValueCreated() {
        final int customFieldId = 0;
        final String customFieldName = "Release Date";
        final String customFieldValue = "1991";
        final CustomFieldValue newValue = new CustomFieldValue(customFieldId, customFieldName, CustomField.TYPE_NUMBER, customFieldValue, false);

        final CustomFieldValue returnedValue = repository.upsertValue(newValue, 1, "system");

        CustomField newCustomField = null;
        try {
            newCustomField = customFieldRepository.getById(returnedValue.getCustomFieldId());
        } catch (ExceptionResourceNotFound exception) {
            fail("New Custom Field was not found when it should have been automatically created.");
        }
        assertNotEquals(customFieldId, newCustomField.id());
        assertAll(
                "The CustomFieldValue that was return didn't match the expected value.",
                () -> assertNotEquals(customFieldId, returnedValue.getCustomFieldId()),
                () -> assertEquals(customFieldName, returnedValue.getCustomFieldName()),
                () -> assertEquals(CustomField.TYPE_NUMBER, returnedValue.getCustomFieldType()),
                () -> assertEquals(customFieldValue, returnedValue.getValue()),
                () -> assertFalse(returnedValue.isDeleted())
        );
    }


    @Test
    public void upsertValue_NewInvalidValue_ExceptionThrown() {
        final CustomFieldValue newValue = new CustomFieldValue(0, "customFieldName", "badCustomFieldType", "customFieldValue", false);

        assertThrows(ExceptionCustomFieldValue.class, () -> repository.upsertValue(newValue, 1, "badEntityKey"));
    }

    @Test
    public void upsertValue_ExistingCustomFieldNewValue_CustomFieldUpdatedNewValueCreated() throws ExceptionFailedDbValidation, ExceptionResourceNotFound {
        final String newCustomFieldName = "NewCustomFieldName";
        final String valueText = "valueText";
        final CustomFieldRequestDto customFieldRequestDto = new CustomFieldRequestDto("OldCustomFieldName", "text", "system");
        final CustomField customField = customFieldRepository.insertCustomField(customFieldRequestDto);
        final CustomFieldValue value = new CustomFieldValue(customField.id(), newCustomFieldName, customField.type(), valueText, false);

        final CustomFieldValue insertedValue = repository.upsertValue(value, 1, "system");

        CustomField updatedCustomField = customFieldRepository.getById(customField.id());
        assertAll(
                "The existing custom field was not updated properly.",
                () -> assertEquals(customField.id(), updatedCustomField.id()),
                () -> assertEquals(newCustomFieldName, updatedCustomField.name()),
                () -> assertEquals(customField.entityKey(), updatedCustomField.entityKey()),
                () -> assertEquals(customField.type(), updatedCustomField.type())
        );
        assertAll(
                "The inserted custom field value was not returned properly",
                () -> assertEquals(updatedCustomField.id(), insertedValue.getCustomFieldId()),
                () -> assertEquals(updatedCustomField.name(), insertedValue.getCustomFieldName()),
                () -> assertEquals(updatedCustomField.type(), insertedValue.getCustomFieldType()),
                () -> assertEquals(valueText, insertedValue.getValue()),
                () -> assertFalse(insertedValue.isDeleted())
        );
    }

    @Test
    public void upsertValue_ExistingCustomFieldAndExistingValue_UpdateCustomFieldAndValue() throws ExceptionFailedDbValidation, ExceptionResourceNotFound {
        final String newCustomFieldName = "NewCustomFieldName";
        final String valueText = "valueText";
        final CustomFieldRequestDto customFieldRequestDto = new CustomFieldRequestDto("OldCustomFieldName", "text", "system");
        final CustomField customField = customFieldRepository.insertCustomField(customFieldRequestDto);
        final CustomFieldValue value = new CustomFieldValue(customField.id(), newCustomFieldName, customField.type(), valueText, false);
        final CustomFieldValue insertedValue = repository.upsertValue(value, 1, "system");
        final String newValueText = "NewValueText";
        final CustomFieldValue valueToOverwrite = new CustomFieldValue(customField.id(), newCustomFieldName, customField.type(), newValueText, false);
        final CustomFieldValue overwrittenValue = repository.upsertValue(valueToOverwrite, 1, "system");

        CustomField updatedCustomField = customFieldRepository.getById(customField.id());
        assertAll(
                "The existing custom field was not updated properly.",
                () -> assertEquals(customField.id(), updatedCustomField.id()),
                () -> assertEquals(newCustomFieldName, updatedCustomField.name()),
                () -> assertEquals(customField.entityKey(), updatedCustomField.entityKey()),
                () -> assertEquals(customField.type(), updatedCustomField.type())
        );
        assertAll(
                "The inserted custom field value was not returned properly",
                () -> assertEquals(updatedCustomField.id(), overwrittenValue.getCustomFieldId()),
                () -> assertEquals(updatedCustomField.name(), overwrittenValue.getCustomFieldName()),
                () -> assertEquals(updatedCustomField.type(), overwrittenValue.getCustomFieldType()),
                () -> assertEquals(newValueText, overwrittenValue.getValue()),
                () -> assertFalse(overwrittenValue.isDeleted())
        );
    }
}

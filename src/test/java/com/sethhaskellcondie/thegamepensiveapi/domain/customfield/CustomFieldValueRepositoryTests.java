package com.sethhaskellcondie.thegamepensiveapi.domain.customfield;

import com.sethhaskellcondie.thegamepensiveapi.domain.Keychain;
import com.sethhaskellcondie.thegamepensiveapi.domain.toy.Toy;
import com.sethhaskellcondie.thegamepensiveapi.domain.toy.ToyRepository;
import com.sethhaskellcondie.thegamepensiveapi.exceptions.ExceptionCustomFieldValue;
import com.sethhaskellcondie.thegamepensiveapi.exceptions.ExceptionFailedDbValidation;
import com.sethhaskellcondie.thegamepensiveapi.exceptions.ExceptionMalformedEntity;
import com.sethhaskellcondie.thegamepensiveapi.exceptions.ExceptionResourceNotFound;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.JdbcTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Since the custom field values belong to entities, and they behave the same way for every entity
 * the testing strategy is going to be up close unit tests for the custom field value repository.
 * Then test the generic compatibility of entities with custom field on the entity api tests.
 */
@JdbcTest
@ActiveProfiles("test-container")
public class CustomFieldValueRepositoryTests {

    //TODO refactor this to be tested through an EntityRepository instead of calling the custom field repository directly

    @Autowired
    protected JdbcTemplate jdbcTemplate;

    //since the CustomFieldValueRepository is only accessed through an EntityRepository we will run the tests through the ToyRepository
    protected ToyRepository toyRepository;
    protected CustomFieldValueRepository customFieldValueRepository;
    protected CustomFieldRepository customFieldRepository;

    @BeforeEach
    public void setUp() {
        toyRepository = new ToyRepository(jdbcTemplate);
        customFieldValueRepository = new CustomFieldValueRepository(jdbcTemplate);
        customFieldRepository = new CustomFieldRepository(jdbcTemplate);
    }

    @Test
    public void upsertValuesOnInsert_NewCustomFieldsNewValues_NewCustomFieldAndValueCreated() {
        final int customFieldId1 = 0; //This will create a new custom field
        final String customFieldName1 = "Release Date";
        final String customFieldValue1 = "1991";
        final CustomFieldValue newValue1 = new CustomFieldValue(customFieldId1, customFieldName1, CustomField.TYPE_NUMBER, customFieldValue1);
        final int customFieldId2 = 0; //This will create a new custom field
        final String customFieldName2 = "Release Date";
        final String customFieldValue2 = "1991";
        final CustomFieldValue newValue2 = new CustomFieldValue(customFieldId2, customFieldName2, CustomField.TYPE_NUMBER, customFieldValue2);
        final Toy newToy = createNewToyWithCustomFields(List.of(newValue1, newValue2));

        final Toy insertedToy = toyRepository.insert(newToy);

        final CustomFieldValue returnedCustomFieldValue1 = insertedToy.getCustomFieldValues().get(0);
        final CustomFieldValue returnedCustomFieldValue2 = insertedToy.getCustomFieldValues().get(1);

        CustomField returnedCustomField1 = customFieldRepository.getById(returnedCustomFieldValue1.getCustomFieldId());
        CustomField returnedCustomField2 = customFieldRepository.getById(returnedCustomFieldValue2.getCustomFieldId());
        assertAll(
                "Newly created CustomFields and CustomFieldValues were not created and returned as expected.",
                () -> assertNotEquals(customFieldId1, returnedCustomField1.id()),
                () -> assertEquals(customFieldName1, returnedCustomField1.name()),
                () -> assertEquals(CustomField.TYPE_NUMBER, returnedCustomField1.type()),
                () -> assertEquals(Keychain.TOY_KEY, returnedCustomField1.entityKey()),
                () -> assertEquals(returnedCustomField1.id(), returnedCustomFieldValue1.getCustomFieldId()),
                () -> assertEquals(customFieldName1, returnedCustomFieldValue1.getCustomFieldName()),
                () -> assertEquals(CustomField.TYPE_NUMBER, returnedCustomFieldValue1.getCustomFieldType()),
                () -> assertEquals(customFieldValue1, returnedCustomFieldValue1.getValue()),
                () -> assertNotEquals(customFieldId2, returnedCustomField2.id()),
                () -> assertEquals(customFieldName2, returnedCustomField2.name()),
                () -> assertEquals(CustomField.TYPE_NUMBER, returnedCustomField2.type()),
                () -> assertEquals(Keychain.TOY_KEY, returnedCustomField2.entityKey()),
                () -> assertEquals(returnedCustomField2.id(), returnedCustomFieldValue2.getCustomFieldId()),
                () -> assertEquals(customFieldName2, returnedCustomFieldValue2.getCustomFieldName()),
                () -> assertEquals(CustomField.TYPE_NUMBER, returnedCustomFieldValue2.getCustomFieldType()),
                () -> assertEquals(customFieldValue2, returnedCustomFieldValue2.getValue())
        );
    }

    @Test
    public void upsertValuesOnInsert_ExistingCustomFieldBadId_ExceptionThrown() {
        final int badId = Integer.MAX_VALUE; //cannot use -1 or a new custom field will be created
        final CustomFieldValue newValueBadCustomFieldId = new CustomFieldValue(badId, "customFieldName", CustomField.TYPE_BOOLEAN, "false");
        final Toy newToy = createNewToyWithCustomFields(List.of(newValueBadCustomFieldId));

        assertThrows(ExceptionCustomFieldValue.class, () -> toyRepository.insert(newToy));
    }

    @Test
    public void upsertValuesOnInsert_ExistingCustomFieldTypeMismatch_ExceptionThrown() {
        final String customFieldName = "Custom!";
        final CustomField existingCustomField = customFieldRepository.insertCustomField(customFieldName, CustomField.TYPE_TEXT, Keychain.TOY_KEY);
        final CustomFieldValue newValueTypeMismatch = new CustomFieldValue(existingCustomField.id(), customFieldName, CustomField.TYPE_BOOLEAN, "true");
        final Toy newToy = createNewToyWithCustomFields(List.of(newValueTypeMismatch));

        assertThrows(ExceptionCustomFieldValue.class, () -> toyRepository.insert(newToy));
    }

    @Test
    public void upsertValuesOnInsert_ExistingCustomFieldNewInvalidNumberValue_ExceptionThrown() {
        final String customFieldName = "Custom Whole Number!";
        final CustomField existingCustomField = customFieldRepository.insertCustomField(customFieldName, CustomField.TYPE_NUMBER, Keychain.TOY_KEY);
        final CustomFieldValue newValue = new CustomFieldValue(existingCustomField.id(), customFieldName, CustomField.TYPE_NUMBER, "InvalidNumber");
        final Toy newToy = createNewToyWithCustomFields(List.of(newValue));

        assertThrows(ExceptionMalformedEntity.class, () -> toyRepository.insert(newToy));
    }

    @Test
    public void upsertValuesOnInsert_ExistingCustomFieldNewInvalidBooleanValue_ExceptionThrown() {
        final String customFieldName = "Custom Boolean!";
        final CustomField existingCustomField = customFieldRepository.insertCustomField(customFieldName, CustomField.TYPE_BOOLEAN, Keychain.TOY_KEY);
        final CustomFieldValue newValue = new CustomFieldValue(existingCustomField.id(), customFieldName, CustomField.TYPE_BOOLEAN, "InvalidBoolean");
        final Toy newToy = createNewToyWithCustomFields(List.of(newValue));

        assertThrows(ExceptionMalformedEntity.class, () -> toyRepository.insert(newToy));
    }


    //upsertValues_ExistingCustomFieldNewNameNewValue_CustomFieldUpdatedValueCreated()
    @Test
    public void upsertValues_ExistingCustomFieldNewValue_CustomFieldUpdatedNewValueCreated() throws ExceptionFailedDbValidation, ExceptionResourceNotFound {
        //TODO update this
        final String newCustomFieldName = "NewCustomFieldName";
        final String valueText = "valueText";
        final CustomFieldRequestDto customFieldRequestDto = new CustomFieldRequestDto("OldCustomFieldName", "text", "system");
        final CustomField customField = customFieldRepository.insertCustomField(customFieldRequestDto);
        final CustomFieldValue value = new CustomFieldValue(customField.id(), newCustomFieldName, customField.type(), valueText);

        final List<CustomFieldValue> insertedValues = customFieldValueRepository.upsertValues(List.of(value), 1, "system");
        final CustomFieldValue insertedValue = insertedValues.get(0);

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
                () -> assertEquals(valueText, insertedValue.getValue())
        );
    }

    @Test
    public void upsertValues_ExistingCustomFieldAndExistingValue_UpdateCustomFieldAndValue() throws ExceptionFailedDbValidation, ExceptionResourceNotFound {
        //TODO update this
        final String newCustomFieldName = "NewCustomFieldName";
        final String valueText = "valueText";
        final CustomFieldRequestDto customFieldRequestDto = new CustomFieldRequestDto("OldCustomFieldName", "text", "system");
        final CustomField customField = customFieldRepository.insertCustomField(customFieldRequestDto);
        final CustomFieldValue value = new CustomFieldValue(customField.id(), newCustomFieldName, customField.type(), valueText);
        customFieldValueRepository.upsertValues(List.of(value), 1, "system");
        final String newValueText = "NewValueText";
        final CustomFieldValue valueToOverwrite = new CustomFieldValue(customField.id(), newCustomFieldName, customField.type(), newValueText);
        final List<CustomFieldValue> overwrittenValues = customFieldValueRepository.upsertValues(List.of(valueToOverwrite), 1, "system");
        final CustomFieldValue overwrittenValue = overwrittenValues.get(0);

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
                () -> assertEquals(newValueText, overwrittenValue.getValue())
        );
    }

    //getCustomFieldsByEntityId_NoFieldsPresent_EmptyListReturned()
    @Test
    public void getCustomFieldsByEntityId_CustomFieldsExist_ListReturned() {
        //TODO update this
        CustomFieldValue releaseYearCustomField = new CustomFieldValue(0, "Release Year", CustomField.TYPE_NUMBER, "1991");
        CustomFieldValue publisherCustomField = new CustomFieldValue(0, "Publisher", CustomField.TYPE_TEXT, "Nintendo");
        CustomFieldValue ownedCustomField = new CustomFieldValue(0, "Owned", CustomField.TYPE_BOOLEAN, "true");
        List<CustomFieldValue> customFields = new ArrayList<>();
        customFields.add(releaseYearCustomField);
        customFields.add(publisherCustomField);
        customFields.add(ownedCustomField);
        customFieldValueRepository.upsertValues(customFields, 1, "system");

        List<CustomFieldValue> returnedCustomFields = customFieldValueRepository.getCustomFieldValuesByEntityIdAndEntityKey(1, "system");

        assertEquals(3, returnedCustomFields.size());
        //The order is not required, but we are testing the order here
        validateReturnedCustomFields(releaseYearCustomField, returnedCustomFields.get(0));
        validateReturnedCustomFields(publisherCustomField, returnedCustomFields.get(1));
        validateReturnedCustomFields(ownedCustomField, returnedCustomFields.get(2));
    }

    private void validateReturnedCustomFields(CustomFieldValue expected, CustomFieldValue actual) {
        //TODO update this
        assertAll(
                "The returned custom field value were malformed.",
                () -> assertEquals(expected.getCustomFieldId(), actual.getCustomFieldId()),
                () -> assertEquals(expected.getCustomFieldName(), actual.getCustomFieldName()),
                () -> assertEquals(expected.getCustomFieldType(), actual.getCustomFieldType()),
                () -> assertEquals(expected.getValue(), actual.getValue())
        );
    }

    private Toy createNewToyWithCustomFields(List<CustomFieldValue> customFieldValues) {
        return new Toy(null, "ToyName", "ToySet", null, null, null, customFieldValues);
    }
}

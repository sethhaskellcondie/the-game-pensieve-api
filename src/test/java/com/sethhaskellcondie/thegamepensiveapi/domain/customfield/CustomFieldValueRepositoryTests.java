package com.sethhaskellcondie.thegamepensiveapi.domain.customfield;

import com.sethhaskellcondie.thegamepensiveapi.domain.Keychain;
import com.sethhaskellcondie.thegamepensiveapi.domain.toy.Toy;
import com.sethhaskellcondie.thegamepensiveapi.domain.toy.ToyRepository;
import com.sethhaskellcondie.thegamepensiveapi.exceptions.ExceptionCustomFieldValue;
import com.sethhaskellcondie.thegamepensiveapi.exceptions.ExceptionMalformedEntity;
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
        final int numberTypeCustomFieldId = 0; //Using an ID that is less than 1 will prompt the creation of a new CustomField
        final String numberTypeCustomFieldName = "Release Date";
        final String numberTypeCustomFieldNumberValue = "1991";
        final CustomFieldValue numberTypeCustomFieldValue = new CustomFieldValue(numberTypeCustomFieldId, numberTypeCustomFieldName, CustomField.TYPE_NUMBER, numberTypeCustomFieldNumberValue);
        final int booleanTypeCustomFieldId = 0;
        final String booleanTypeCustomFieldName = "Favorite";
        final String booleanTypeCustomFieldTextValue = "true";
        final CustomFieldValue booleanTypeCustomField = new CustomFieldValue(booleanTypeCustomFieldId, booleanTypeCustomFieldName, CustomField.TYPE_BOOLEAN, booleanTypeCustomFieldTextValue);
        final int textTypeCustomFieldId = 0;
        final String textTypeCustomFieldName = "ToyStore";
        final String textTypeCustomFieldTextValue = "ToysRUs";
        final CustomFieldValue textTypeCustomField = new CustomFieldValue(textTypeCustomFieldId, textTypeCustomFieldName, CustomField.TYPE_TEXT, textTypeCustomFieldTextValue);

        final Toy newToy = createNewToyWithCustomFields(List.of(numberTypeCustomFieldValue, booleanTypeCustomField, textTypeCustomField));

        final Toy insertedToy = toyRepository.insert(newToy);

        assertEquals(3, insertedToy.getCustomFieldValues().size(), "The wrong number of CustomFieldValues returned on new entity insert.");
        //The custom fields should be returned in the same order they were saved
        final CustomFieldValue returnedNumberValue = insertedToy.getCustomFieldValues().get(0);
        final CustomFieldValue returnedBooleanValue = insertedToy.getCustomFieldValues().get(1);
        final CustomFieldValue returnedTextValue = insertedToy.getCustomFieldValues().get(2);

        CustomField retrievedNumberField = customFieldRepository.getById(returnedNumberValue.getCustomFieldId());
        CustomField retrievedBooleanField = customFieldRepository.getById(returnedBooleanValue.getCustomFieldId());
        CustomField retrievedTextField = customFieldRepository.getById(returnedTextValue.getCustomFieldId());
        assertAll(
                "Newly created CustomFields were not saved to the database correctly after a new entity insert.",
                () -> assertNotEquals(numberTypeCustomFieldId, retrievedNumberField.id()),
                () -> assertEquals(numberTypeCustomFieldName, retrievedNumberField.name()),
                () -> assertEquals(CustomField.TYPE_NUMBER, retrievedNumberField.type()),
                () -> assertEquals(insertedToy.getKey(), retrievedNumberField.entityKey()),

                () -> assertNotEquals(booleanTypeCustomFieldId, retrievedBooleanField.id()),
                () -> assertEquals(booleanTypeCustomFieldName, retrievedBooleanField.name()),
                () -> assertEquals(CustomField.TYPE_BOOLEAN, retrievedBooleanField.type()),
                () -> assertEquals(insertedToy.getKey(), retrievedBooleanField.entityKey()),

                () -> assertNotEquals(textTypeCustomFieldId, retrievedTextField.id()),
                () -> assertEquals(textTypeCustomFieldName, retrievedTextField.name()),
                () -> assertEquals(CustomField.TYPE_TEXT, retrievedTextField.type()),
                () -> assertEquals(insertedToy.getKey(), retrievedTextField.entityKey())
        );
        assertAll(
                "Newly created CustomFieldValues were not returned on the entity correctly after a new entity insert.",
                () -> assertEquals(retrievedNumberField.id(), returnedNumberValue.getCustomFieldId()),
                () -> assertEquals(numberTypeCustomFieldName, returnedNumberValue.getCustomFieldName()),
                () -> assertEquals(CustomField.TYPE_NUMBER, returnedNumberValue.getCustomFieldType()),
                () -> assertEquals(numberTypeCustomFieldNumberValue, returnedNumberValue.getValue()),

                () -> assertEquals(retrievedBooleanField.id(), returnedBooleanValue.getCustomFieldId()),
                () -> assertEquals(booleanTypeCustomFieldName, returnedBooleanValue.getCustomFieldName()),
                () -> assertEquals(CustomField.TYPE_BOOLEAN, returnedBooleanValue.getCustomFieldType()),
                () -> assertEquals(booleanTypeCustomFieldTextValue, returnedBooleanValue.getValue()),

                () -> assertEquals(retrievedTextField.id(), returnedTextValue.getCustomFieldId()),
                () -> assertEquals(textTypeCustomFieldName, returnedTextValue.getCustomFieldName()),
                () -> assertEquals(CustomField.TYPE_TEXT, returnedTextValue.getCustomFieldType()),
                () -> assertEquals(textTypeCustomFieldTextValue, returnedTextValue.getValue())
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

    @Test
    public void upsertValuesOnInsert_ExistingCustomFieldUpdatedNameOnNewValue_CustomFieldUpdatedValueCreated() {
        final String updatedCustomFieldName = "UpdatedName";
        final String customFieldValueText = "Valid Text";
        final CustomField existingCustomField = customFieldRepository.insertCustomField("Old Custom Field Name", CustomField.TYPE_TEXT, Keychain.TOY_KEY);
        final CustomFieldValue newValue = new CustomFieldValue(existingCustomField.id(), updatedCustomFieldName, CustomField.TYPE_TEXT, customFieldValueText);
        final Toy newToy = createNewToyWithCustomFields(List.of(newValue));

        final Toy insertedToy = toyRepository.insert(newToy);

        final CustomField retrievedUpdatedCustomField = customFieldRepository.getById(existingCustomField.id());
        assertEquals(1, insertedToy.getCustomFieldValues().size(), "More or less than 1 CustomFieldValue was returned on an entity insert with 1 CustomFieldValue.");
        final CustomFieldValue returnedCustomFieldValue = insertedToy.getCustomFieldValues().get(0);

        assertAll(
                "The previously existing CustomField wasn't updated properly after a new entity insert.",
                () -> assertEquals(updatedCustomFieldName, retrievedUpdatedCustomField.name()),
                () -> assertEquals(CustomField.TYPE_TEXT, retrievedUpdatedCustomField.type()),
                () -> assertEquals(insertedToy.getKey(), retrievedUpdatedCustomField.entityKey())
        );
        assertAll(
                "The newly created CustomFieldValue wasn't created and returned properly after a new entity insert.",
                () -> assertEquals(existingCustomField.id(), returnedCustomFieldValue.getCustomFieldId()),
                () -> assertEquals(updatedCustomFieldName, returnedCustomFieldValue.getCustomFieldName()),
                () -> assertEquals(CustomField.TYPE_TEXT, returnedCustomFieldValue.getCustomFieldType()),
                () -> assertEquals(customFieldValueText, returnedCustomFieldValue.getValue())
        );

        //use the setup from this test on the next test
        upsertValuesOnUpdate_ExistingCustomFieldAndExistingValue_UpdateCustomFieldAndValue(insertedToy, retrievedUpdatedCustomField, returnedCustomFieldValue);
    }

    public void upsertValuesOnUpdate_ExistingCustomFieldAndExistingValue_UpdateCustomFieldAndValue(Toy existingToy, CustomField existingCustomField, CustomFieldValue existingCustomFieldValue) {
        final String updatedCustomFieldName = "UpdatedAgain!";
        final String updatedCustomFieldValueText = "Still Valid Text";
        final CustomFieldValue updatedCustomFieldValue = new CustomFieldValue(existingCustomField.id(), updatedCustomFieldName, existingCustomField.type(), updatedCustomFieldValueText);
        existingToy.setCustomFieldValues(List.of(updatedCustomFieldValue));

        toyRepository.update(existingToy);

        final CustomField retrievedCustomField = customFieldRepository.getById(existingCustomField.id());
        final List<CustomFieldValue> retrievedCustomFieldValues = customFieldValueRepository.getCustomFieldValuesByEntityIdAndEntityKey(existingToy.getId(), existingToy.getKey());
        assertEquals(1, retrievedCustomFieldValues.size(), "More or less than 1 CustomFieldValue was retrieved from the database on after an entity insert with 1 CustomFieldValue.");
        final CustomFieldValue retrievedCustomFieldValue = retrievedCustomFieldValues.get(0);

        assertAll(
                "The previously existing CustomField was not updated properly on entity update.",
                () -> assertEquals(updatedCustomFieldName, retrievedCustomField.name()),
                () -> assertEquals(existingCustomField.entityKey(), retrievedCustomField.entityKey()),
                () -> assertEquals(existingCustomField.type(), retrievedCustomField.type())
        );
        assertAll(
                "The previously existing CustomFieldValue was not updated properly on entity update.",
                () -> assertEquals(existingCustomFieldValue.getCustomFieldId(), retrievedCustomFieldValue.getCustomFieldId()),
                () -> assertEquals(updatedCustomFieldName, retrievedCustomFieldValue.getCustomFieldName()),
                () -> assertEquals(existingCustomFieldValue.getCustomFieldType(), retrievedCustomFieldValue.getCustomFieldType()),
                () -> assertEquals(updatedCustomFieldValueText, retrievedCustomFieldValue.getValue())
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

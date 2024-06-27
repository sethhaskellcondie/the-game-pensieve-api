package com.sethhaskellcondie.thegamepensiveapi.domain.customfield;

import com.sethhaskellcondie.thegamepensiveapi.domain.Keychain;
import com.sethhaskellcondie.thegamepensiveapi.domain.filter.Filter;
import com.sethhaskellcondie.thegamepensiveapi.domain.entity.toy.Toy;
import com.sethhaskellcondie.thegamepensiveapi.domain.entity.toy.ToyRepository;
import com.sethhaskellcondie.thegamepensiveapi.domain.exceptions.ExceptionCustomFieldValue;
import com.sethhaskellcondie.thegamepensiveapi.domain.exceptions.ExceptionMalformedEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.JdbcTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Since the custom field values belong to entities, and they behave the same way for every entity
 * the testing strategy is going to be up close unit tests for the custom field value repository.
 * Then test the generic compatibility of entities with custom field on the entity api tests.
 * <p>
 * This test is testing the public functions on the CustomFieldValueRepository upsertValues() and getCustomFieldValuesByEntityIdAndEntityKey()
 */
@JdbcTest
@ActiveProfiles("test-container")
public class CustomFieldValueRepositoryTests {

    @Autowired
    protected JdbcTemplate jdbcTemplate;

    //since the CustomFieldValueRepository is only accessed through an EntityRepository we will run the tests through the ToyRepository
    protected ToyRepository toyRepository;
    protected CustomFieldRepository customFieldRepository;

    @BeforeEach
    public void setUp() {
        toyRepository = new ToyRepository(jdbcTemplate);
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

        //use the setup for this test on another test
        getCustomFieldValuesByEntityIdAndEntityKey_UsingGetWithFilters_CustomFieldValuesReturned(insertedToy, insertedToy.getCustomFieldValues());
    }

    public void getCustomFieldValuesByEntityIdAndEntityKey_UsingGetWithFilters_CustomFieldValuesReturned(Toy toyWithCustomFields, List<CustomFieldValue> expectedCustomFieldValues) {
        Filter nameEqualsFilter = new Filter(toyWithCustomFields.getKey(), Filter.FIELD_TYPE_TEXT, "name", Filter.OPERATOR_EQUALS, toyWithCustomFields.getName(), false);
        List<Toy> retrievedToys = toyRepository.getWithFilters(List.of(nameEqualsFilter));

        assertEquals(1, retrievedToys.size(), "Unexpected number of results after performing a getWithFilters()");
        Toy retrievedToy = retrievedToys.get(0);

        assertEquals(3, expectedCustomFieldValues.size(), "Unexpected number of expected customFieldValues before performing getWithFilters() test.");
        final CustomFieldValue expectedNumberValue = expectedCustomFieldValues.get(0);
        final CustomFieldValue expectedBooleanValue = expectedCustomFieldValues.get(1);
        final CustomFieldValue expectedTextValue = expectedCustomFieldValues.get(2);
        //The custom fields should be returned in the same order they were saved
        final CustomFieldValue returnedNumberValue = retrievedToy.getCustomFieldValues().get(0);
        final CustomFieldValue returnedBooleanValue = retrievedToy.getCustomFieldValues().get(1);
        final CustomFieldValue returnedTextValue = retrievedToy.getCustomFieldValues().get(2);

        assertAll(
                "CustomFieldValues retrieved on an entity using getWithFilters() did not matched the expected result",
                () -> assertEquals(expectedNumberValue.getCustomFieldId(), returnedNumberValue.getCustomFieldId()),
                () -> assertEquals(expectedNumberValue.getCustomFieldName(), returnedNumberValue.getCustomFieldName()),
                () -> assertEquals(expectedNumberValue.getCustomFieldType(), returnedNumberValue.getCustomFieldType()),
                () -> assertEquals(expectedNumberValue.getValue(), returnedNumberValue.getValue()),

                () -> assertEquals(expectedBooleanValue.getCustomFieldId(), returnedBooleanValue.getCustomFieldId()),
                () -> assertEquals(expectedBooleanValue.getCustomFieldName(), returnedBooleanValue.getCustomFieldName()),
                () -> assertEquals(expectedBooleanValue.getCustomFieldType(), returnedBooleanValue.getCustomFieldType()),
                () -> assertEquals(expectedBooleanValue.getValue(), returnedBooleanValue.getValue()),

                () -> assertEquals(expectedTextValue.getCustomFieldId(), returnedTextValue.getCustomFieldId()),
                () -> assertEquals(expectedTextValue.getCustomFieldName(), returnedTextValue.getCustomFieldName()),
                () -> assertEquals(expectedTextValue.getCustomFieldType(), returnedTextValue.getCustomFieldType()),
                () -> assertEquals(expectedTextValue.getValue(), returnedTextValue.getValue())
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
        final CustomField existingCustomField = customFieldRepository.insertCustomField(customFieldName, CustomField.TYPE_TEXT, Keychain.TOY_KEY); //TYPE_TEXT should match TYPE_BOOLEAN in the value
        final CustomFieldValue newValueTypeMismatch = new CustomFieldValue(existingCustomField.id(), customFieldName, CustomField.TYPE_BOOLEAN, "true");
        final Toy newToy = createNewToyWithCustomFields(List.of(newValueTypeMismatch));

        assertThrows(ExceptionCustomFieldValue.class, () -> toyRepository.insert(newToy));
    }

    @Test
    public void upsertValuesOnInsert_ExistingCustomFieldNewInvalidNumberValue_ExceptionThrown() {
        final String customFieldName = "Custom Whole Number!";
        final CustomField existingCustomField = customFieldRepository.insertCustomField(customFieldName, CustomField.TYPE_NUMBER, Keychain.TOY_KEY);
        final CustomFieldValue newValue = new CustomFieldValue(existingCustomField.id(), customFieldName, CustomField.TYPE_NUMBER, "InvalidNumber"); //the value should be able to convert to an int.
        final Toy newToy = createNewToyWithCustomFields(List.of(newValue));

        assertThrows(ExceptionMalformedEntity.class, () -> toyRepository.insert(newToy));
    }

    @Test
    public void upsertValuesOnInsert_ExistingCustomFieldNewInvalidBooleanValue_ExceptionThrown() {
        final String customFieldName = "Custom Boolean!";
        final CustomField existingCustomField = customFieldRepository.insertCustomField(customFieldName, CustomField.TYPE_BOOLEAN, Keychain.TOY_KEY);
        final CustomFieldValue newValue = new CustomFieldValue(existingCustomField.id(), customFieldName, CustomField.TYPE_BOOLEAN, "InvalidBoolean"); //the value should be 'true' or 'false'
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

        //use the setup for this test on other tests
        upsertValuesOnUpdate_ExistingCustomFieldAndExistingValue_UpdateCustomFieldAndValue(insertedToy, retrievedUpdatedCustomField, returnedCustomFieldValue);
        getCustomFieldValuesByEntityIdAndEntityKey_UsingGetById_CustomFieldValuesReturned(insertedToy, insertedToy.getCustomFieldValues());
        getCustomFieldValuesByEntityIdAndEntityKey_UsingGetDeletedById_CustomFieldValuesReturned(insertedToy, insertedToy.getCustomFieldValues());
    }

    public void upsertValuesOnUpdate_ExistingCustomFieldAndExistingValue_UpdateCustomFieldAndValue(Toy existingToy, CustomField existingCustomField, CustomFieldValue existingCustomFieldValue) {
        final String updatedCustomFieldName = "UpdatedAgain!";
        final String updatedCustomFieldValueText = "Still Valid Text";
        final CustomFieldValue updatedCustomFieldValue = new CustomFieldValue(existingCustomField.id(), updatedCustomFieldName, existingCustomField.type(), updatedCustomFieldValueText);
        existingToy.setCustomFieldValues(List.of(updatedCustomFieldValue));

        final Toy updatedToy = toyRepository.update(existingToy);

        final CustomField retrievedCustomField = customFieldRepository.getById(existingCustomField.id());
        final List<CustomFieldValue> retrievedCustomFieldValues = updatedToy.getCustomFieldValues();
        assertEquals(1, retrievedCustomFieldValues.size(), "More or less than 1 CustomFieldValue was returned on an entity after an entity update with 1 CustomFieldValue.");
        final CustomFieldValue retrievedCustomFieldValue = retrievedCustomFieldValues.get(0);

        assertAll(
                "The previously existing CustomField was not updated properly after an entity update.",
                () -> assertEquals(updatedCustomFieldName, retrievedCustomField.name()),
                () -> assertEquals(existingCustomField.entityKey(), retrievedCustomField.entityKey()),
                () -> assertEquals(existingCustomField.type(), retrievedCustomField.type())
        );
        assertAll(
                "The previously existing CustomFieldValue was not updated properly after an entity update.",
                () -> assertEquals(existingCustomFieldValue.getCustomFieldId(), retrievedCustomFieldValue.getCustomFieldId()),
                () -> assertEquals(updatedCustomFieldName, retrievedCustomFieldValue.getCustomFieldName()),
                () -> assertEquals(existingCustomFieldValue.getCustomFieldType(), retrievedCustomFieldValue.getCustomFieldType()),
                () -> assertEquals(updatedCustomFieldValueText, retrievedCustomFieldValue.getValue())
        );
    }

    public void getCustomFieldValuesByEntityIdAndEntityKey_UsingGetById_CustomFieldValuesReturned(Toy toyWithCustomFieldValues, List<CustomFieldValue> expectedCustomFieldValues) {
        Toy retrievedToy = toyRepository.getById(toyWithCustomFieldValues.getId());

        assertEquals(1, expectedCustomFieldValues.size(), "Unexpected number of expected customFieldValues before performing getById() test.");
        final CustomFieldValue expectedCustomFieldValue = expectedCustomFieldValues.get(0);
        final CustomFieldValue returnedCustomFieldValue = retrievedToy.getCustomFieldValues().get(0);

        assertAll(
                "CustomFieldValues retrieved on an entity using getWithFilters() did not matched the expected result",
                () -> assertEquals(expectedCustomFieldValue.getCustomFieldId(), returnedCustomFieldValue.getCustomFieldId()),
                () -> assertEquals(expectedCustomFieldValue.getCustomFieldName(), returnedCustomFieldValue.getCustomFieldName()),
                () -> assertEquals(expectedCustomFieldValue.getCustomFieldType(), returnedCustomFieldValue.getCustomFieldType()),
                () -> assertEquals(expectedCustomFieldValue.getValue(), returnedCustomFieldValue.getValue())
        );
    }

    public void getCustomFieldValuesByEntityIdAndEntityKey_UsingGetDeletedById_CustomFieldValuesReturned(Toy toyToBeDeleted, List<CustomFieldValue> expectedCustomFieldValues) {
        toyRepository.deleteById(toyToBeDeleted.getId());
        Toy deletedToy = toyRepository.getDeletedById(toyToBeDeleted.getId());

        assertEquals(1, expectedCustomFieldValues.size(), "Unexpected number of expected customFieldValues before performing getDeletedById() test.");
        final CustomFieldValue expectedCustomFieldValue = expectedCustomFieldValues.get(0);
        final CustomFieldValue returnedCustomFieldValue = deletedToy.getCustomFieldValues().get(0);

        assertAll(
                "CustomFieldValues retrieved on an entity using getWithFilters() did not matched the expected result",
                () -> assertEquals(expectedCustomFieldValue.getCustomFieldId(), returnedCustomFieldValue.getCustomFieldId()),
                () -> assertEquals(expectedCustomFieldValue.getCustomFieldName(), returnedCustomFieldValue.getCustomFieldName()),
                () -> assertEquals(expectedCustomFieldValue.getCustomFieldType(), returnedCustomFieldValue.getCustomFieldType()),
                () -> assertEquals(expectedCustomFieldValue.getValue(), returnedCustomFieldValue.getValue())
        );
    }

    private Toy createNewToyWithCustomFields(List<CustomFieldValue> customFieldValues) {
        return new Toy(null, "ToyName", "ToySet", null, null, null, customFieldValues);
    }
}

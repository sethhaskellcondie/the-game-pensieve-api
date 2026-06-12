package com.sethhaskellcondie.thegamepensieveapi.domain.customfield;

import com.sethhaskellcondie.thegamepensieveapi.domain.Keychain;
import com.sethhaskellcondie.thegamepensieveapi.domain.filter.Filter;
import com.sethhaskellcondie.thegamepensieveapi.domain.entity.toy.Toy;
import com.sethhaskellcondie.thegamepensieveapi.domain.entity.toy.ToyRepository;
import com.sethhaskellcondie.thegamepensieveapi.domain.exceptions.ExceptionCustomFieldValue;
import com.sethhaskellcondie.thegamepensieveapi.domain.exceptions.ExceptionMalformedEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.JdbcTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
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
        customFieldRepository = new CustomFieldRepository(jdbcTemplate, new CustomFieldOptionRepository(jdbcTemplate));
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
        //TYPE_TEXT should match TYPE_BOOLEAN in the value
        final CustomField existingCustomField = customFieldRepository.insertCustomField(CustomFieldRequestDto.withoutOptions(customFieldName, CustomField.TYPE_TEXT, Keychain.TOY_KEY));
        final CustomFieldValue newValueTypeMismatch = new CustomFieldValue(existingCustomField.id(), customFieldName, CustomField.TYPE_BOOLEAN, "true");
        final Toy newToy = createNewToyWithCustomFields(List.of(newValueTypeMismatch));

        assertThrows(ExceptionCustomFieldValue.class, () -> toyRepository.insert(newToy));
    }

    @Test
    public void upsertValuesOnInsert_ExistingCustomFieldNewInvalidNumberValue_ExceptionThrown() {
        final String customFieldName = "Custom Whole Number!";
        final CustomField existingCustomField = customFieldRepository.insertCustomField(CustomFieldRequestDto.withoutOptions(customFieldName, CustomField.TYPE_NUMBER, Keychain.TOY_KEY));
        final CustomFieldValue newValue = new CustomFieldValue(existingCustomField.id(), customFieldName, CustomField.TYPE_NUMBER, "InvalidNumber"); //the value should be able to convert to an int.
        final Toy newToy = createNewToyWithCustomFields(List.of(newValue));

        assertThrows(ExceptionMalformedEntity.class, () -> toyRepository.insert(newToy));
    }

    @Test
    public void upsertValuesOnInsert_ExistingCustomFieldNewInvalidBooleanValue_ExceptionThrown() {
        final String customFieldName = "Custom Boolean!";
        final CustomField existingCustomField = customFieldRepository.insertCustomField(CustomFieldRequestDto.withoutOptions(customFieldName, CustomField.TYPE_BOOLEAN, Keychain.TOY_KEY));
        final CustomFieldValue newValue = new CustomFieldValue(existingCustomField.id(), customFieldName, CustomField.TYPE_BOOLEAN, "InvalidBoolean"); //the value should be 'true' or 'false'
        final Toy newToy = createNewToyWithCustomFields(List.of(newValue));

        assertThrows(ExceptionMalformedEntity.class, () -> toyRepository.insert(newToy));
    }

    @Test
    public void upsertValuesOnInsert_ExistingCustomFieldUpdatedNameOnNewValue_CustomFieldUpdatedValueCreated() {
        final String updatedCustomFieldName = "UpdatedName";
        final String customFieldValueText = "Valid Text";
        final CustomField existingCustomField = customFieldRepository.insertCustomField(CustomFieldRequestDto.withoutOptions("Old Custom Field Name", CustomField.TYPE_TEXT, Keychain.TOY_KEY));
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

    @Test
    public void getWithFilters_MultipleEntitiesEachWithCustomFieldValues_CustomFieldValuesReturnedOnCorrectEntity() {
        final CustomField textField = customFieldRepository.insertCustomField(CustomFieldRequestDto.withoutOptions("Toy Store", CustomField.TYPE_TEXT, Keychain.TOY_KEY));
        final CustomField numberField = customFieldRepository.insertCustomField(CustomFieldRequestDto.withoutOptions("Release Year Test 2", CustomField.TYPE_NUMBER, Keychain.TOY_KEY));

        final String toy1Name = "BatchTest_Alpha";
        final String toy1ToyStoreValue = "ToysRUs";
        final String toy1ReleaseYearValue = "1985";
        final CustomFieldValue toy1TextValue = new CustomFieldValue(textField.id(), textField.name(), CustomField.TYPE_TEXT, toy1ToyStoreValue);
        final CustomFieldValue toy1NumberValue = new CustomFieldValue(numberField.id(), numberField.name(), CustomField.TYPE_NUMBER, toy1ReleaseYearValue);
        final Toy insertedToy1 = toyRepository.insert(new Toy(null, toy1Name, "SetA", null, null, null, List.of(toy1TextValue, toy1NumberValue)));

        final String toy2Name = "BatchTest_Beta";
        final String toy2ToyStoreValue = "Target";
        final String toy2ReleaseYearValue = "1992";
        final CustomFieldValue toy2TextValue = new CustomFieldValue(textField.id(), textField.name(), CustomField.TYPE_TEXT, toy2ToyStoreValue);
        final CustomFieldValue toy2NumberValue = new CustomFieldValue(numberField.id(), numberField.name(), CustomField.TYPE_NUMBER, toy2ReleaseYearValue);
        final Toy insertedToy2 = toyRepository.insert(new Toy(null, toy2Name, "SetB", null, null, null, List.of(toy2TextValue, toy2NumberValue)));

        final String toy3Name = "BatchTest_Gamma";
        final String toy3ToyStoreValue = "Walmart";
        final String toy3ReleaseYearValue = "2001";
        final CustomFieldValue toy3TextValue = new CustomFieldValue(textField.id(), textField.name(), CustomField.TYPE_TEXT, toy3ToyStoreValue);
        final CustomFieldValue toy3NumberValue = new CustomFieldValue(numberField.id(), numberField.name(), CustomField.TYPE_NUMBER, toy3ReleaseYearValue);
        final Toy insertedToy3 = toyRepository.insert(new Toy(null, toy3Name, "SetC", null, null, null, List.of(toy3TextValue, toy3NumberValue)));

        final Filter nameContainsFilter = new Filter(Keychain.TOY_KEY, Filter.FIELD_TYPE_TEXT, "name", Filter.OPERATOR_CONTAINS, "BatchTest_", false);
        final List<Toy> retrievedToys = toyRepository.getWithFilters(List.of(nameContainsFilter));

        assertEquals(3, retrievedToys.size(), "Expected exactly 3 toys returned from batch search.");

        final Toy retrievedToy1 = retrievedToys.stream().filter(t -> t.getId().equals(insertedToy1.getId())).findFirst().orElseThrow();
        final Toy retrievedToy2 = retrievedToys.stream().filter(t -> t.getId().equals(insertedToy2.getId())).findFirst().orElseThrow();
        final Toy retrievedToy3 = retrievedToys.stream().filter(t -> t.getId().equals(insertedToy3.getId())).findFirst().orElseThrow();

        final String toyStoreFieldName = textField.name();
        final String releaseYearFieldName = numberField.name();
        assertAll(
                toy1Name + " custom field values were not returned correctly.",
                () -> assertEquals(2, retrievedToy1.getCustomFieldValues().size()),
                () -> assertEquals(toy1ToyStoreValue, retrievedToy1.getCustomFieldValues().stream()
                        .filter(v -> v.getCustomFieldName().equals(toyStoreFieldName)).findFirst().orElseThrow().getValue()),
                () -> assertEquals(toy1ReleaseYearValue, retrievedToy1.getCustomFieldValues().stream()
                        .filter(v -> v.getCustomFieldName().equals(releaseYearFieldName)).findFirst().orElseThrow().getValue())
        );
        assertAll(
                toy2Name + " custom field values were not returned correctly.",
                () -> assertEquals(2, retrievedToy2.getCustomFieldValues().size()),
                () -> assertEquals(toy2ToyStoreValue, retrievedToy2.getCustomFieldValues().stream()
                        .filter(v -> v.getCustomFieldName().equals(toyStoreFieldName)).findFirst().orElseThrow().getValue()),
                () -> assertEquals(toy2ReleaseYearValue, retrievedToy2.getCustomFieldValues().stream()
                        .filter(v -> v.getCustomFieldName().equals(releaseYearFieldName)).findFirst().orElseThrow().getValue())
        );
        assertAll(
                toy3Name + " custom field values were not returned correctly.",
                () -> assertEquals(2, retrievedToy3.getCustomFieldValues().size()),
                () -> assertEquals(toy3ToyStoreValue, retrievedToy3.getCustomFieldValues().stream()
                        .filter(v -> v.getCustomFieldName().equals(toyStoreFieldName)).findFirst().orElseThrow().getValue()),
                () -> assertEquals(toy3ReleaseYearValue, retrievedToy3.getCustomFieldValues().stream()
                        .filter(v -> v.getCustomFieldName().equals(releaseYearFieldName)).findFirst().orElseThrow().getValue())
        );
    }

    @Test
    public void upsertValues_DropdownTypeValidOption_ValueStoredAndRetrieved() {
        final CustomFieldOptionRepository optionRepository = new CustomFieldOptionRepository(jdbcTemplate);
        final CustomField dropdownField = customFieldRepository.insertCustomField(CustomFieldRequestDto.withoutOptions("Status", CustomField.TYPE_DROPDOWN, Keychain.TOY_KEY));
        final String optionName = "In Progress";
        final CustomFieldOption option = optionRepository.insertOption(dropdownField.id(), optionName, true, 0);

        //Enum values are written by the selected option's id; the value text is resolved on read.
        final CustomFieldValue value = new CustomFieldValue(dropdownField.id(), dropdownField.name(), CustomField.TYPE_DROPDOWN, null, option.id());
        final Toy inserted = toyRepository.insert(createNewToyWithCustomFields(List.of(value)));

        assertEquals(1, inserted.getCustomFieldValues().size());
        final CustomFieldValue returned = inserted.getCustomFieldValues().get(0);
        assertAll(
                "Dropdown custom field value was not stored and retrieved correctly.",
                () -> assertEquals(dropdownField.id(), returned.getCustomFieldId()),
                () -> assertEquals(dropdownField.name(), returned.getCustomFieldName()),
                () -> assertEquals(CustomField.TYPE_DROPDOWN, returned.getCustomFieldType()),
                () -> assertEquals(optionName, returned.getValue()),
                () -> assertEquals(option.id(), returned.getValueOptionId())
        );
    }

    @Test
    public void upsertValues_DropdownTypeInvalidOption_ThrowsMalformedEntity() {
        final CustomFieldOptionRepository optionRepository = new CustomFieldOptionRepository(jdbcTemplate);
        final CustomField dropdownField = customFieldRepository.insertCustomField(CustomFieldRequestDto.withoutOptions("Category", CustomField.TYPE_DROPDOWN, Keychain.TOY_KEY));
        final CustomFieldOption validOption = optionRepository.insertOption(dropdownField.id(), "Valid Option", true, 0);

        //An option id that does not belong to this field must be rejected.
        final CustomFieldValue invalidValue = new CustomFieldValue(dropdownField.id(), dropdownField.name(), CustomField.TYPE_DROPDOWN, null, validOption.id() + 9999);
        final Toy toyWithInvalidValue = createNewToyWithCustomFields(List.of(invalidValue));

        assertThrows(
                ExceptionMalformedEntity.class,
                () -> toyRepository.insert(toyWithInvalidValue)
        );
    }

    @Test
    public void upsertValues_RadioButtonTypeValidOption_ValueStoredAndRetrieved() {
        final CustomFieldOptionRepository optionRepository = new CustomFieldOptionRepository(jdbcTemplate);
        final CustomField radioField = customFieldRepository.insertCustomField(CustomFieldRequestDto.withoutOptions("Size", CustomField.TYPE_RADIO_BUTTON, Keychain.TOY_KEY));
        final String optionName = "Large";
        final CustomFieldOption option = optionRepository.insertOption(radioField.id(), optionName, true, 0);

        final CustomFieldValue value = new CustomFieldValue(radioField.id(), radioField.name(), CustomField.TYPE_RADIO_BUTTON, null, option.id());
        final Toy inserted = toyRepository.insert(createNewToyWithCustomFields(List.of(value)));

        assertEquals(1, inserted.getCustomFieldValues().size());
        final CustomFieldValue returned = inserted.getCustomFieldValues().get(0);
        assertAll(
                "Radio button custom field value was not stored and retrieved correctly.",
                () -> assertEquals(radioField.id(), returned.getCustomFieldId()),
                () -> assertEquals(radioField.name(), returned.getCustomFieldName()),
                () -> assertEquals(CustomField.TYPE_RADIO_BUTTON, returned.getCustomFieldType()),
                () -> assertEquals(optionName, returned.getValue()),
                () -> assertEquals(option.id(), returned.getValueOptionId())
        );
    }

    @Test
    public void upsertValues_RadioButtonTypeInvalidOption_ThrowsMalformedEntity() {
        final CustomFieldOptionRepository optionRepository = new CustomFieldOptionRepository(jdbcTemplate);
        final CustomField radioField = customFieldRepository.insertCustomField(CustomFieldRequestDto.withoutOptions("Color", CustomField.TYPE_RADIO_BUTTON, Keychain.TOY_KEY));
        final CustomFieldOption validOption = optionRepository.insertOption(radioField.id(), "Red", true, 0);

        final CustomFieldValue invalidValue = new CustomFieldValue(radioField.id(), radioField.name(), CustomField.TYPE_RADIO_BUTTON, null, validOption.id() + 9999);
        final Toy toyWithInvalidValue = createNewToyWithCustomFields(List.of(invalidValue));

        assertThrows(
                ExceptionMalformedEntity.class,
                () -> toyRepository.insert(toyWithInvalidValue)
        );
    }

    @Test
    public void upsertValues_ProgressBarTypeValidOption_ValueStoredAndRetrieved() {
        final CustomFieldOptionRepository optionRepository = new CustomFieldOptionRepository(jdbcTemplate);
        final CustomField progressField = customFieldRepository.insertCustomField(CustomFieldRequestDto.withoutOptions("Completion", CustomField.TYPE_PROGRESS_BAR, Keychain.TOY_KEY));
        final String optionName = "50%";
        final CustomFieldOption option = optionRepository.insertOption(progressField.id(), optionName, true, 0);

        final CustomFieldValue value = new CustomFieldValue(progressField.id(), progressField.name(), CustomField.TYPE_PROGRESS_BAR, null, option.id());
        final Toy inserted = toyRepository.insert(createNewToyWithCustomFields(List.of(value)));

        assertEquals(1, inserted.getCustomFieldValues().size());
        final CustomFieldValue returned = inserted.getCustomFieldValues().get(0);
        assertAll(
                "Progress bar custom field value was not stored and retrieved correctly.",
                () -> assertEquals(progressField.id(), returned.getCustomFieldId()),
                () -> assertEquals(progressField.name(), returned.getCustomFieldName()),
                () -> assertEquals(CustomField.TYPE_PROGRESS_BAR, returned.getCustomFieldType()),
                () -> assertEquals(optionName, returned.getValue()),
                () -> assertEquals(option.id(), returned.getValueOptionId())
        );
    }

    @Test
    public void upsertValues_ProgressBarTypeInvalidOption_ThrowsMalformedEntity() {
        final CustomFieldOptionRepository optionRepository = new CustomFieldOptionRepository(jdbcTemplate);
        final CustomField progressField = customFieldRepository.insertCustomField(CustomFieldRequestDto.withoutOptions("Stage", CustomField.TYPE_PROGRESS_BAR, Keychain.TOY_KEY));
        final CustomFieldOption validOption = optionRepository.insertOption(progressField.id(), "Not Started", true, 0);

        final CustomFieldValue invalidValue = new CustomFieldValue(progressField.id(), progressField.name(), CustomField.TYPE_PROGRESS_BAR, null, validOption.id() + 9999);
        final Toy toyWithInvalidValue = createNewToyWithCustomFields(List.of(invalidValue));

        assertThrows(
                ExceptionMalformedEntity.class,
                () -> toyRepository.insert(toyWithInvalidValue)
        );
    }

    @Test
    public void renameOption_ExistingValueReferencesOption_ValueReflectsNewNameAndEntityStillSaves() {
        //This reproduces the original bug: before values referenced the option by id, renaming an option
        //orphaned every stored value (it still held the old name) and re-saving the entity failed validation.
        final CustomFieldOptionRepository optionRepository = new CustomFieldOptionRepository(jdbcTemplate);
        final CustomField dropdownField = customFieldRepository.insertCustomField(CustomFieldRequestDto.withoutOptions("RenameStatus", CustomField.TYPE_DROPDOWN, Keychain.TOY_KEY));
        final CustomFieldOption option = optionRepository.insertOption(dropdownField.id(), "Old Name", true, 0);

        final CustomFieldValue value = new CustomFieldValue(dropdownField.id(), dropdownField.name(), CustomField.TYPE_DROPDOWN, null, option.id());
        final Toy inserted = toyRepository.insert(createNewToyWithCustomFields(List.of(value)));
        final int toyId = inserted.getId();

        //rename the option in place
        optionRepository.updateOption(option.id(), "New Name", 0, true);

        //the existing value now resolves to the new name with the same option id - nothing was orphaned
        final Toy reread = toyRepository.getById(toyId);
        final CustomFieldValue rereadValue = reread.getCustomFieldValues().get(0);
        assertAll(
                "Renaming an option must update existing values through the reference, not orphan them.",
                () -> assertEquals("New Name", rereadValue.getValue()),
                () -> assertEquals(option.id(), rereadValue.getValueOptionId())
        );

        //and the entity must still be savable (this threw an ExceptionMalformedEntity before the refactor)
        assertDoesNotThrow(() -> toyRepository.update(reread), "Re-saving an entity after an option rename should succeed.");
    }

    @Test
    public void deleteOption_ValueReferencesDeletedOption_ReassignedToDefaultOption() {
        final CustomFieldOptionRepository optionRepository = new CustomFieldOptionRepository(jdbcTemplate);
        final CustomField dropdownField = customFieldRepository.insertCustomField(CustomFieldRequestDto.withoutOptions("CascadeStatus", CustomField.TYPE_DROPDOWN, Keychain.TOY_KEY));
        final CustomFieldOption defaultOption = optionRepository.insertOption(dropdownField.id(), "Default", true, 0);
        final CustomFieldOption otherOption = optionRepository.insertOption(dropdownField.id(), "Other", false, 1);

        final CustomFieldValue value = new CustomFieldValue(dropdownField.id(), dropdownField.name(), CustomField.TYPE_DROPDOWN, null, otherOption.id());
        final Toy inserted = toyRepository.insert(createNewToyWithCustomFields(List.of(value)));
        final int toyId = inserted.getId();

        //deleting the referenced non-default option reassigns the value to the default option
        optionRepository.deleteOption(otherOption.id(), dropdownField.id());

        final Toy reread = toyRepository.getById(toyId);
        final CustomFieldValue rereadValue = reread.getCustomFieldValues().get(0);
        assertAll(
                "Deleting a referenced option must reassign existing values to the default option.",
                () -> assertEquals(defaultOption.id(), rereadValue.getValueOptionId()),
                () -> assertEquals("Default", rereadValue.getValue())
        );
    }

    private Toy createNewToyWithCustomFields(List<CustomFieldValue> customFieldValues) {
        return new Toy(null, "ToyName", "ToySet", null, null, null, customFieldValues);
    }
}

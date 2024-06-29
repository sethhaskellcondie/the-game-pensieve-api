package com.sethhaskellcondie.thegamepensiveapi.domain.backupimport;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import com.sethhaskellcondie.thegamepensiveapi.domain.Keychain;
import com.sethhaskellcondie.thegamepensiveapi.domain.customfield.CustomField;
import com.sethhaskellcondie.thegamepensiveapi.domain.customfield.CustomFieldValue;
import com.sethhaskellcondie.thegamepensiveapi.domain.entity.system.SystemRequestDto;
import com.sethhaskellcondie.thegamepensiveapi.domain.entity.toy.ToyRequestDto;

/**
 * This test suite is a little different it's a set of integration tests that simulate the likely situation of updating and importing a single file
 * again and again trying to get their data imported so the tests are not independent instead they build on top of one another. Each assuming that
 * some data was imported from the previous test. Because of this, there is a single test-container dedicated to this test suite to be spun up
 * each time the test run to ensure repeated accurate results. And each test is called in sequence after the first test is complete making one big test.
 */
@SpringBootTest
@ActiveProfiles("import-tests")
public class BackupImportGatewayTests {

    @Autowired
    private BackupImportGateway gateway;

    //ids for custom fields and custom field values are ignored.
    private final CustomField initialCustomField = new CustomField(0, "Initial Custom Field", CustomField.TYPE_TEXT, Keychain.SYSTEM_KEY);
    private final ToyRequestDto initialToy = new ToyRequestDto("Initial Toy", "Initial Set", new ArrayList<>());

    private final CustomField toyCustomField = new CustomField(11, "Count", CustomField.TYPE_NUMBER, Keychain.TOY_KEY);
    private final ToyRequestDto secondToy = new ToyRequestDto("Second Toy", "Second Set", List.of(new CustomFieldValue(11, "Count", CustomField.TYPE_NUMBER, "4")));

    private final SystemRequestDto initialSystem = new SystemRequestDto("Initial System", 3, false, List.of(new CustomFieldValue(0, "Initial Custom Field", CustomField.TYPE_TEXT, "Initial Value")));
    private final CustomField systemCustomField = new CustomField(99, "Owned", CustomField.TYPE_BOOLEAN, Keychain.SYSTEM_KEY);
    private final SystemRequestDto secondSystem = new SystemRequestDto("Second System", 4, false, List.of(new CustomFieldValue(0, "Owned", CustomField.TYPE_BOOLEAN, "false")));

    @Test
    void testImportBackupData_HappyPath_InitialDataCreated() {
        final BackupDataDto backupData = new BackupDataDto(
            List.of(initialCustomField),
            List.of(initialToy),
            List.of()
        );

        final ImportResultsDto results = gateway.importBackupData(backupData);

        assertAll(
            "Error when importing brand new data for testing.",
            () -> assertEquals(1, results.createdCustomFields()),
            () -> assertEquals(0, results.existingCustomFields()),
            () -> assertEquals(1, results.createdToys()),
            () -> assertEquals(0, results.existingToys()),
            () -> assertEquals(0, results.createdSystems()),
            () -> assertEquals(0, results.existingSystems()),
            () -> assertEquals(0, results.exceptionBackupImport().getExceptions().size())
        );
        validateBackupData(backupData, gateway.getBackupData());

        //call the rest of the tests in order
        testImportCustomFields_MismatchedType_ReturnErrors();
        testImportCustomFields_ValidFieldsAndInvalidTypeAndKey_ReturnSuccessAndErrors();
        testImportToys_MissingCustomField_ReturnSuccessAndErrors();
        testImportSystems_MissingCustomField_ReturnSuccessAndErrors();
        testImportSystems_SomeExistingSomeNew_ReturnSuccess();
        importBackupData_UsingRetrievedBackupData_NoNewDataImported();
    }

    void testImportCustomFields_MismatchedType_ReturnErrors() {
        final BackupDataDto backupData = new BackupDataDto(
            //The name and key match the initialCustomField but the type is a mismatch causing an error
            List.of(new CustomField(0, "Initial Custom Field", CustomField.TYPE_NUMBER, Keychain.SYSTEM_KEY)),
            List.of(new ToyRequestDto("Will Be Skipped", "Ignored", new ArrayList<>())),
            List.of(new SystemRequestDto("Will Be Skipped", 3, false, new ArrayList<>()))
        );
        final BackupDataDto expectedBackupData = new BackupDataDto(
            List.of(initialCustomField),
            List.of(initialToy),
            List.of()
        );

        final ImportResultsDto results = gateway.importBackupData(backupData);

        assertAll(
            "Unexpected results for importing custom fields with mismatched types.",
            () -> assertEquals(0, results.createdCustomFields()),
            () -> assertEquals(0, results.existingCustomFields()),
            () -> assertEquals(0, results.createdToys()),
            () -> assertEquals(0, results.existingToys()),
            () -> assertEquals(0, results.createdSystems()),
            () -> assertEquals(0, results.existingSystems()),
            () -> assertEquals(2, results.exceptionBackupImport().getExceptions().size())
        );
        validateBackupData(expectedBackupData, gateway.getBackupData());
    }

    void testImportCustomFields_ValidFieldsAndInvalidTypeAndKey_ReturnSuccessAndErrors() {
        final BackupDataDto backupData = new BackupDataDto(
            //The toyCustomField will work but the invalidCustomField will throw an error preventing the rest of the import from completing.
            List.of(initialCustomField, toyCustomField, new CustomField(42, "Valid Name", "Invalid Type", "Invalid Key")),
            List.of(new ToyRequestDto("Will Be Skipped", "Ignored", new ArrayList<>())),
            List.of(new SystemRequestDto("Will Be Skipped", 3, false, new ArrayList<>()))
        );
        final BackupDataDto expectedBackupData = new BackupDataDto(
            List.of(initialCustomField, toyCustomField),
            List.of(initialToy),
            List.of()
        );

        final ImportResultsDto results = gateway.importBackupData(backupData);

        assertAll(
            "Unexpected results for importing some custom fields while others return errors.",
            () -> assertEquals(1, results.createdCustomFields()),
            () -> assertEquals(1, results.existingCustomFields()),
            () -> assertEquals(0, results.createdToys()),
            () -> assertEquals(0, results.existingToys()),
            () -> assertEquals(0, results.createdSystems()),
            () -> assertEquals(0, results.existingSystems()),
            () -> assertEquals(2, results.exceptionBackupImport().getExceptions().size())
        );
        validateBackupData(expectedBackupData, gateway.getBackupData());
    }

    void testImportToys_MissingCustomField_ReturnSuccessAndErrors() {
        //if a toy to be imported has a custom field value but is missing a matching custom field in the import that toy will be skipped
        final ToyRequestDto skippedToy = new ToyRequestDto("Valid Name", "Valid Set", List.of(new CustomFieldValue(0, "Missing Name", CustomField.TYPE_TEXT, "Value")));
        final BackupDataDto backupData = new BackupDataDto(
            List.of(initialCustomField, toyCustomField),
            List.of(initialToy, secondToy, skippedToy),
            List.of()
        );
        final BackupDataDto expectedBackupData = new BackupDataDto(
            List.of(initialCustomField, toyCustomField),
            List.of(initialToy, secondToy),
            List.of()
        );

        final ImportResultsDto results = gateway.importBackupData(backupData);

        assertAll(
            "Unexpected results for importing some toys while others return errors.",
            () -> assertEquals(0, results.createdCustomFields()),
            () -> assertEquals(2, results.existingCustomFields()),
            () -> assertEquals(1, results.createdToys()),
            () -> assertEquals(1, results.existingToys()),
            () -> assertEquals(0, results.createdSystems()),
            () -> assertEquals(0, results.existingSystems()),
            () -> assertEquals(2, results.exceptionBackupImport().getExceptions().size())
        );
        validateBackupData(expectedBackupData, gateway.getBackupData());
    }

    void testImportSystems_MissingCustomField_ReturnSuccessAndErrors() {
        //if a system to be imported has a custom field value but is missing a matching custom field in the import that system will be skipped
        final SystemRequestDto skippedSystem = new SystemRequestDto("Valid Name", 4, false, List.of(new CustomFieldValue(0, "Missing Name", CustomField.TYPE_TEXT, "Value")));
        final BackupDataDto backupData = new BackupDataDto(
            List.of(initialCustomField, toyCustomField),
            List.of(initialToy, secondToy),
            List.of(initialSystem, skippedSystem)
        );
        final BackupDataDto expectedBackupData = new BackupDataDto(
            List.of(initialCustomField, toyCustomField),
            List.of(initialToy, secondToy),
            List.of(initialSystem)
        );

        final ImportResultsDto results = gateway.importBackupData(backupData);

        assertAll(
            "Unexpected results for importing some systems successfully and others with missing custom fields.",
            () -> assertEquals(0, results.createdCustomFields()),
            () -> assertEquals(2, results.existingCustomFields()),
            () -> assertEquals(0, results.createdToys()),
            () -> assertEquals(2, results.existingToys()),
            () -> assertEquals(1, results.createdSystems()),
            () -> assertEquals(0, results.existingSystems()),
            () -> assertEquals(2, results.exceptionBackupImport().getExceptions().size())
        );
        validateBackupData(expectedBackupData, gateway.getBackupData());
    }

    void testImportSystems_SomeExistingSomeNew_ReturnSuccess() {
        final BackupDataDto backupData = new BackupDataDto(
            List.of(initialCustomField, toyCustomField, systemCustomField),
            List.of(initialToy, secondToy),
            List.of(initialSystem, secondSystem)
        );

        final ImportResultsDto results = gateway.importBackupData(backupData);

        assertAll(
            "Unexpected results for importing some systems while others systems already exist.",
            () -> assertEquals(1, results.createdCustomFields()), //systemCustomField is new
            () -> assertEquals(2, results.existingCustomFields()),
            () -> assertEquals(0, results.createdToys()),
            () -> assertEquals(2, results.existingToys()),
            () -> assertEquals(1, results.createdSystems()), //secondSystem is new
            () -> assertEquals(1, results.existingSystems()), //initialSystem is existing
            () -> assertEquals(0, results.exceptionBackupImport().getExceptions().size())
        );
        validateBackupData(backupData, gateway.getBackupData());
    }

    void importBackupData_UsingRetrievedBackupData_NoNewDataImported() {

        final BackupDataDto actualBackupData = gateway.getBackupData();
        final BackupDataDto expectedBackupData = new BackupDataDto(
            List.of(initialCustomField, toyCustomField, systemCustomField),
            List.of(initialToy, secondToy),
            List.of(initialSystem, secondSystem)
        );
        validateBackupData(expectedBackupData, actualBackupData);

        final ImportResultsDto results = gateway.importBackupData(actualBackupData);

        assertAll(
            "Unexpected errors when checking to see that the import process is idempotent.",
            () -> assertEquals(0, results.createdCustomFields()),
            () -> assertEquals(3, results.existingCustomFields()),
            () -> assertEquals(0, results.createdToys()),
            () -> assertEquals(2, results.existingToys()),
            () -> assertEquals(0, results.createdSystems()),
            () -> assertEquals(2, results.existingSystems()),
            () -> assertEquals(0, results.exceptionBackupImport().getExceptions().size())
        );
        validateBackupData(expectedBackupData, gateway.getBackupData());
    }

    private void validateBackupData(BackupDataDto expectedBackupData, BackupDataDto actualBackupData) {
        validateCustomFieldBackupData(expectedBackupData, actualBackupData);
        validateToyBackupData(expectedBackupData, actualBackupData);
        validateSystemBackupData(expectedBackupData, actualBackupData);
    }

    private void validateCustomFieldBackupData(BackupDataDto expectedData, BackupDataDto actualData) {
        final List<CustomField> expectedCustomFields = expectedData.customFields();
        final List<CustomField> actualCustomFields = actualData.customFields();
        if (null == expectedCustomFields || null == actualCustomFields) {
            assertAll(
                "If the expected custom fields are null then the actual should be as well.",
                () -> assertNull(expectedCustomFields),
                () -> assertNull(actualCustomFields)
            );
            return;
        }
        assertEquals(expectedCustomFields.size(), actualCustomFields.size(), "Unexpected number of custom field results returned in BackupDataDto.");
        for(int i = 0; i < expectedCustomFields.size(); i++) {
            final CustomField expectedCustomField = expectedCustomFields.get(i);
            final CustomField actualCustomField = actualCustomFields.get(i);
            assertAll(
                "Mismatched custom field data returned in BackupDataDto.",
                () -> assertEquals(expectedCustomField.name(), actualCustomField.name()),
                () -> assertEquals(expectedCustomField.type(), actualCustomField.type()),
                () -> assertEquals(expectedCustomField.entityKey(), actualCustomField.entityKey())
            );
        }
    }

    private void validateToyBackupData(BackupDataDto expectedData, BackupDataDto actualData) {
        final List<ToyRequestDto> expectedToys = expectedData.toys();
        final List<ToyRequestDto> actualToys = actualData.toys();
        if (null == expectedToys || null == actualToys) {
            assertAll(
                "If the expected toys are null then the actual should be as well.",
                () -> assertNull(expectedToys),
                () -> assertNull(actualToys)
            );
            return;
        }
        assertEquals(expectedToys.size(), actualToys.size(), "Unexpected number of toy results returned in BackupDataDto");
        for(int i = 0; i < expectedToys.size(); i++) {
            final ToyRequestDto expectedToy = expectedToys.get(i);
            final ToyRequestDto actualToy = actualToys.get(i);
            assertAll(
                "Mismatched toy data returned in BackupDataDto.",
                () -> assertEquals(expectedToy.name(), actualToy.name()),
                () -> assertEquals(expectedToy.set(), actualToy.set())
            );
            validateCustomFieldValues(expectedToy.customFieldValues(), actualToy.customFieldValues(), Keychain.TOY_KEY, actualToy.name());
        }
    }

    private void validateSystemBackupData(BackupDataDto expectedData, BackupDataDto actualData) {
        final List<SystemRequestDto> expectedSystems = expectedData.systems();
        final List<SystemRequestDto> actualSystems = actualData.systems();
        if (null == expectedSystems || null == actualSystems) {
            assertAll(
                "If the expected systems are null then the actual should be as well.",
                () -> assertNull(expectedSystems),
                () -> assertNull(actualSystems)
            );
            return;
        }
        assertEquals(expectedSystems.size(), actualSystems.size(), "Unexpected number of system results returned in BackupDataDto");
        for(int i = 0; i < expectedSystems.size(); i++) {
            final SystemRequestDto expectedSystem = expectedSystems.get(i);
            final SystemRequestDto actualSystem = actualSystems.get(i);
            assertAll(
                "Mismatched system data returned in BackupDataDto.",
                () -> assertEquals(expectedSystem.name(), actualSystem.name()),
                () -> assertEquals(expectedSystem.generation(), actualSystem.generation()),
                () -> assertEquals(expectedSystem.handheld(), actualSystem.handheld())
            );
            validateCustomFieldValues(expectedSystem.customFieldValues(), actualSystem.customFieldValues(), Keychain.SYSTEM_KEY, actualSystem.name());
        }
    }

    private void validateCustomFieldValues(List<CustomFieldValue> expectedValues, List<CustomFieldValue> actualValues, String entityKey, String name) {
        assertEquals(expectedValues.size(), actualValues.size(), "Unexpected number of custom field values in " + entityKey + " with the name '" + name + "'");
        for(int i = 0; i < expectedValues.size(); i++) {
            final CustomFieldValue expectedValue = expectedValues.get(i);
            final CustomFieldValue actualValue = actualValues.get(i);
            assertAll(
                "Mismatched custom field value data returned in " + entityKey + " with the name '" + name + "'",
                () -> assertEquals(expectedValue.getCustomFieldName(), actualValue.getCustomFieldName()),
                () -> assertEquals(expectedValue.getCustomFieldType(), actualValue.getCustomFieldType()),
                () -> assertEquals(expectedValue.getValue(), actualValue.getValue())
            );
        }
    }
}

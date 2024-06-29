package com.sethhaskellcondie.thegamepensiveapi.domain.backupimport;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

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
        BackupDataDto backupData = new BackupDataDto(
            List.of(initialCustomField),
            List.of(initialToy),
            null
        );

        ImportResultsDto results = gateway.importBackupData(backupData);

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

        //call the following tests in order
        testImportCustomFields_MismatchedType_ReturnErrors();
        testImportCustomFields_ValidFieldsAndInvalidTypeAndKey_ReturnSuccessAndErrors();
        testImportToys_MissingCustomField_ReturnSuccessAndErrors();
        testImportSystems_MissingCustomField_ReturnSuccessAndErrors();
        testImportSystems_SomeExistingSomeNew_ReturnSuccess();
    }

    void testImportCustomFields_MismatchedType_ReturnErrors() {
        BackupDataDto backupData = new BackupDataDto(
            //The name and key match the initialCustomField but the type is a mismatch causing an error
            List.of(new CustomField(0, "Initial Custom Field", CustomField.TYPE_NUMBER, Keychain.SYSTEM_KEY)),
            List.of(new ToyRequestDto("Will Be Skipped", "Ignored", new ArrayList<>())),
            List.of(new SystemRequestDto("Will Be Skipped", 3, false, new ArrayList<>()))
        );

        ImportResultsDto results = gateway.importBackupData(backupData);

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
    }

    void testImportCustomFields_ValidFieldsAndInvalidTypeAndKey_ReturnSuccessAndErrors() {
        BackupDataDto backupData = new BackupDataDto(
            //The toyCustomField will work but the invalidCustomField will throw an error preventing the rest of the import from completing.
            List.of(initialCustomField, toyCustomField, new CustomField(42, "Valid Name", "Invalid Type", "Invalid Key")),
            List.of(new ToyRequestDto("Will Be Skipped", "Ignored", new ArrayList<>())),
            List.of(new SystemRequestDto("Will Be Skipped", 3, false, new ArrayList<>()))
        );

        ImportResultsDto results = gateway.importBackupData(backupData);

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
    }

    void testImportToys_MissingCustomField_ReturnSuccessAndErrors() {
        //if a toy to be imported has a custom field value but is missing a matching custom field in the import that toy will be skipped
        final ToyRequestDto skippedToy = new ToyRequestDto("Valid Name", "Valid Set", List.of(new CustomFieldValue(0, "Missing Name", CustomField.TYPE_TEXT, "Value")));
        BackupDataDto backupData = new BackupDataDto(
            List.of(initialCustomField, toyCustomField),
            List.of(initialToy, secondToy, skippedToy),
            List.of()
        );

        ImportResultsDto results = gateway.importBackupData(backupData);

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
    }

    void testImportSystems_MissingCustomField_ReturnSuccessAndErrors() {
        //if a system to be imported has a custom field value but is missing a matching custom field in the import that system will be skipped
        final SystemRequestDto skippedSystem = new SystemRequestDto("Valid Name", 4, false, List.of(new CustomFieldValue(0, "Missing Name", CustomField.TYPE_TEXT, "Value")));
        BackupDataDto backupData = new BackupDataDto(
            List.of(initialCustomField, toyCustomField),
            List.of(initialToy, secondToy),
            List.of(initialSystem, skippedSystem)
        );

        ImportResultsDto results = gateway.importBackupData(backupData);

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
    }

    void testImportSystems_SomeExistingSomeNew_ReturnSuccess() {
        BackupDataDto backupData = new BackupDataDto(
            List.of(initialCustomField, toyCustomField, systemCustomField),
            List.of(initialToy, secondToy),
            List.of(initialSystem, secondSystem)
        );

        ImportResultsDto results = gateway.importBackupData(backupData);

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
    }

    void getBackupData_HappyPath_AllDataPresent() {
        //get all of the data that has been imported into the database
    }

    void importBackupData_UsingRetrievedBackupData_NoNewDataImported() {
        //test to make sure that a double import will not create any new data
    }
}

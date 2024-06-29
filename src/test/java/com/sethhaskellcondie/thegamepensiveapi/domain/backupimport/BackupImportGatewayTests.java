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

    private final CustomField initialCustomField = new CustomField(0, "Initial Custom Field", CustomField.TYPE_TEXT, Keychain.SYSTEM_KEY); //id is ignored
    private final ToyRequestDto initialToy = new ToyRequestDto("Initial Toy", "Initial Set", new ArrayList<>());
    private final SystemRequestDto initialSystem = new SystemRequestDto("Initial System", 3, false, List.of(new CustomFieldValue(0, "Initial Custom Field", CustomField.TYPE_TEXT, "Initial Value"))); //id is ignored

    private final CustomField systemCustomField = new CustomField(99, "Owned", CustomField.TYPE_BOOLEAN, Keychain.SYSTEM_KEY);


    @Test
    void testImportBackupData_HappyPath_InitialDataCreated() {
        BackupDataDto backupData = new BackupDataDto(
            List.of(initialCustomField),
            List.of(initialToy),
            List.of(initialSystem)
        );

        ImportResultsDto results = gateway.importBackupData(backupData);

        assertAll(
            "Unexpected results returned from the initial import.",
            () -> assertEquals(1, results.createdCustomFields()),
            () -> assertEquals(0, results.existingCustomFields()),
            () -> assertEquals(1, results.createdToys()),
            () -> assertEquals(0, results.existingToys()),
            () -> assertEquals(1, results.createdSystems()),
            () -> assertEquals(0, results.existingSystems()),
            () -> assertEquals(0, results.exceptionBackupImport().getExceptions().size())
        );

        //call the following tests in order
        testImportCustomFields_MismatchedType_ReturnErrors();
        testImportCustomFields_ValidFieldsAndInvalidTypeAndKey_ReturnSuccessAndErrors();
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
            "Unexpected results returned from the initial import.",
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
            //The systemCustomField will work but the invalidCustomField will throw an error preventing the rest of the import from completing.
            List.of(initialCustomField, systemCustomField, new CustomField(42, "Valid Name", "Invalid Type", "Invalid Key")),
            List.of(initialToy),
            List.of(initialSystem)
        );

        ImportResultsDto results = gateway.importBackupData(backupData);

        assertAll(
            "Unexpected results returned from the initial import.",
            () -> assertEquals(1, results.createdCustomFields()),
            () -> assertEquals(1, results.existingCustomFields()),
            () -> assertEquals(0, results.createdToys()),
            () -> assertEquals(0, results.existingToys()),
            () -> assertEquals(0, results.createdSystems()),
            () -> assertEquals(0, results.existingSystems()),
            () -> assertEquals(2, results.exceptionBackupImport().getExceptions().size())
        );
    }

    @Test
    void testImportBackupData_ErrorInCustomFieldsImport_ImportStopped() {
        //This will have a valid toy import and an invalid custom field import
        //the import function will return early without attempting to import the toy
    }

    @Test
    void testImportToys_MissingCustomField_ReturnSuccessAndErrors() {
        //if a toy to be imported has a custom field value but is missing a matching custom field in the import that toy will be skipped
        //valid toys will still be imported
    }

    @Test
    void testImportToys_ExistingToys_NoNewToysCreated() {
        //the system will check to see if a toy exists with the given name and set, if that toy is found then the custom fields values will be updated
        //but the toy will not be created again
    }

    @Test
    void testImportSystems_MissingCustomField_ReturnSuccessAndErrors() {
        //if a system to be imported has a custom field value but is missing a matching custom field in the import that system will be skipped
        //valid new system will still be created
    }

    @Test
    void testImportSystems_SomeExistingSomeNew_ReturnSuccessAndErrors() {
        //the system will check to see if an existing system entity is already in the database with the given name, if that system is found then it will be updated
        //new systems will be created in the database
    }
}
